#!/usr/bin/env python3
"""Summarize the KTE batch inspection and prepare a manual triage sheet."""

import argparse
import csv
import hashlib
import json
import re
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree


PREFIX = re.compile(r"^\[KTE/([^/]+)/([^]]+)\] (.*)$", re.DOTALL)
PROJECT_PREFIX = "file://$PROJECT_DIR$/"


def read_problems(path: Path):
    root = ElementTree.parse(path).getroot()
    if root.tag != "problems":
        raise ValueError(f"Unexpected inspection report root in {path}: {root.tag}")
    return list(root.findall("problem"))


def location(problem):
    raw_file = problem.findtext("file") or ""
    if not raw_file.startswith(PROJECT_PREFIX):
        raise ValueError(f"Unexpected inspection file URL: {raw_file}")
    return {
        "file": raw_file[len(PROJECT_PREFIX):],
        "line": int(problem.findtext("line") or 0),
        "column": int(problem.findtext("offset") or 0) + 1,
        "length": int(problem.findtext("length") or 0),
        "severity": problem.find("problem_class").get("severity"),
    }


def audit_issue(problem):
    description = problem.findtext("description") or ""
    match = PREFIX.fullmatch(description)
    if match is None:
        raise ValueError(f"Unexpected KTE audit description: {description}")
    issue = location(problem)
    issue.update({
        "origin": match.group(1),
        "code": match.group(2),
        "message": match.group(3),
        "highlighted_text": problem.findtext("highlighted_element") or "",
    })
    return issue


def source_fingerprint(directory: Path):
    digest = hashlib.sha256()
    files = sorted(directory.rglob("*.kte"))
    if not files:
        raise ValueError(f"No .kte files in {directory}")
    for path in files:
        relative = path.relative_to(directory).as_posix()
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return {"file_count": len(files), "sha256": digest.hexdigest()}


def scan_coverage(log_path: Path, project_root: Path, templates: Path):
    relative_root = templates.resolve().relative_to(project_root.resolve()).as_posix()
    pattern = re.compile(r"Analyzing code in .*?/" + re.escape(relative_root) + r"/(.*?)\.kte \[")
    scanned = {match + ".kte" for match in pattern.findall(log_path.read_text(encoding="utf-8", errors="replace"))}
    expected = {path.relative_to(templates).as_posix() for path in templates.rglob("*.kte")}
    return {
        "source_count": len(expected),
        "scanned_count": len(scanned),
        "missing": sorted(expected - scanned),
        "unexpected": sorted(scanned - expected),
    }


def parity(audit, annotator):
    def key(item):
        return (item["file"], item["line"], item["column"], item["length"],
                item["severity"], item["message"])

    observed = Counter()
    for problem in annotator:
        item = location(problem)
        item["message"] = (problem.findtext("description") or "").removesuffix(" #loc")
        if item["file"].endswith(".kte"):
            observed[key(item)] += 1
    expected = Counter(key(item) for item in audit)
    return {
        "audit_count": sum(expected.values()),
        "annotator_count": sum(observed.values()),
        "matching_count": sum((expected & observed).values()),
        "audit_only_count": sum((expected - observed).values()),
        "annotator_only_count": sum((observed - expected).values()),
    }


def write_csv(path: Path, issues):
    fields = ["file", "line", "column", "length", "severity", "origin", "code",
              "message", "highlighted_text", "classification", "notes"]
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for issue in issues:
            writer.writerow({**issue, "classification": "", "notes": ""})


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="KteDiagnosticAudit.xml")
    parser.add_argument("--templates", required=True, type=Path, help="scanned KTE source directory")
    parser.add_argument("--project-root", type=Path, help="project copy root, required with --scan-log")
    parser.add_argument("--scan-log", type=Path, help="inspect -v2 log for file coverage check")
    parser.add_argument("--annotator", type=Path, help="Annotator.xml for editor parity check")
    parser.add_argument("--allow-unverified", action="store_true",
                        help="summarize without both coverage and editor parity checks")
    parser.add_argument("--plugin-revision", help="plugin revision used for the inspection run; note local changes")
    parser.add_argument("--ide-build", help="IDE build used for the inspection run")
    parser.add_argument("--project-revision", help="revision of the original project before making the copy")
    parser.add_argument("--json", type=Path, help="write machine-readable results")
    parser.add_argument("--csv", type=Path, help="write manual triage sheet")
    args = parser.parse_args(argv)

    if not args.templates.is_dir():
        parser.error(f"Not a directory: {args.templates}")
    if args.scan_log and not args.project_root:
        parser.error("--project-root is required with --scan-log")
    if not args.allow_unverified and (not args.scan_log or not args.annotator):
        parser.error("Verified audit requires --scan-log, --project-root, and --annotator; "
                     "use --allow-unverified for an exploratory summary")
    issues = [audit_issue(problem) for problem in read_problems(args.report)]
    issues.sort(key=lambda item: (item["file"], item["line"], item["column"], item["code"]))
    counts = {
        "severity": dict(sorted(Counter(item["severity"] for item in issues).items())),
        "origin": dict(sorted(Counter(item["origin"] for item in issues).items())),
        "code": dict(sorted(Counter(item["code"] for item in issues).items())),
        "affected_files": len({item["file"] for item in issues}),
    }
    result = {
        "source": source_fingerprint(args.templates),
        "environment": {
            "plugin_revision": args.plugin_revision,
            "ide_build": args.ide_build,
            "project_revision": args.project_revision,
        },
        "counts": counts,
        "issues": issues,
    }
    if args.annotator:
        result["editor_parity"] = parity(issues, read_problems(args.annotator))
    if args.scan_log:
        result["scan_coverage"] = scan_coverage(args.scan_log, args.project_root, args.templates)
    coverage_failed = args.scan_log and (result["scan_coverage"]["missing"] or
                                         result["scan_coverage"]["unexpected"])
    parity_failed = args.annotator and (result["editor_parity"]["audit_only_count"] or
                                        result["editor_parity"]["annotator_only_count"])
    analysis_failed = counts["code"].get("ANALYSIS_FAILURE", 0) > 0
    result["verification"] = {
        "status": "failed" if coverage_failed or parity_failed or analysis_failed else
                  "verified" if args.scan_log and args.annotator else "unverified",
        "coverage_checked": bool(args.scan_log),
        "editor_parity_checked": bool(args.annotator),
        "analysis_failure_count": counts["code"].get("ANALYSIS_FAILURE", 0),
    }
    if args.json:
        args.json.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.csv:
        write_csv(args.csv, issues)
    print(json.dumps({key: value for key, value in result.items() if key != "issues"},
                     ensure_ascii=False, indent=2))
    if coverage_failed or parity_failed or analysis_failed:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
