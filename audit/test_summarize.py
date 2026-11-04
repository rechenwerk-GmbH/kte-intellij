"""Small, synthetic fixtures for the project audit report contract."""

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

import summarize


class SummarizeTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.templates = self.root / "templates"
        self.templates.mkdir()
        (self.templates / "sample.kte").write_text("${bad}\n", encoding="utf-8")
        self.report = self.root / "KteDiagnosticAudit.xml"
        self.annotator = self.root / "Annotator.xml"
        self.log = self.root / "inspect.log"
        self.summary = self.root / "summary.json"
        self.write_problem_reports()
        self.log.write_text(
            f"Analyzing code in {self.templates / 'sample.kte'} [1/1]\n",
            encoding="utf-8",
        )

    def write_problem_reports(self, audit_code="UNRESOLVED_REFERENCE",
                              audit_message="Unresolved reference", editor_message=None):
        editor_message = audit_message if editor_message is None else editor_message
        self.report.write_text(self.problem_report(
            f"[KTE/KOTLIN/{audit_code}] {audit_message}"), encoding="utf-8")
        self.annotator.write_text(self.problem_report(f"{editor_message} #loc"), encoding="utf-8")

    @staticmethod
    def problem_report(description):
        return f"""<problems><problem>
  <file>file://$PROJECT_DIR$/templates/sample.kte</file>
  <line>1</line>
  <problem_class severity="ERROR">Problem</problem_class>
  <description>{description}</description>
  <offset>2</offset>
  <length>3</length>
</problem></problems>"""

    def run_summary(self, *extra):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            summarize.main([
                str(self.report), "--templates", str(self.templates),
                "--project-root", str(self.root), "--json", str(self.summary),
                *extra,
            ])
        return json.loads(self.summary.read_text(encoding="utf-8"))

    def verified_args(self):
        return ("--scan-log", str(self.log), "--annotator", str(self.annotator))

    def test_verified_report_checks_coverage_and_editor_parity(self):
        result = self.run_summary(*self.verified_args(), "--plugin-revision", "plugin-fixture",
                                  "--ide-build", "261.123",
                                  "--project-revision", "fixture-revision")

        self.assertEqual("verified", result["verification"]["status"])
        self.assertEqual(1, result["scan_coverage"]["scanned_count"])
        self.assertEqual(1, result["editor_parity"]["matching_count"])
        self.assertEqual("261.123", result["environment"]["ide_build"])
        self.assertEqual("plugin-fixture", result["environment"]["plugin_revision"])
        self.assertEqual("fixture-revision", result["environment"]["project_revision"])
        self.assertEqual(1, result["source"]["file_count"])

    def test_partial_report_requires_explicit_unverified_mode(self):
        with self.assertRaises(SystemExit) as failure:
            self.run_summary()
        self.assertEqual(2, failure.exception.code)

        result = self.run_summary("--allow-unverified")
        self.assertEqual("unverified", result["verification"]["status"])
        self.assertFalse(result["verification"]["coverage_checked"])

    def test_missing_file_coverage_fails_audit(self):
        self.log.write_text("", encoding="utf-8")
        with self.assertRaises(SystemExit) as failure:
            self.run_summary(*self.verified_args())
        self.assertEqual(2, failure.exception.code)
        result = json.loads(self.summary.read_text(encoding="utf-8"))
        self.assertEqual("failed", result["verification"]["status"])
        self.assertEqual(["sample.kte"], result["scan_coverage"]["missing"])

    def test_editor_mismatch_and_analysis_failure_fail_audit(self):
        self.write_problem_reports(editor_message="Different editor message")
        with self.assertRaises(SystemExit) as mismatch:
            self.run_summary(*self.verified_args())
        self.assertEqual(2, mismatch.exception.code)
        result = json.loads(self.summary.read_text(encoding="utf-8"))
        self.assertEqual(1, result["editor_parity"]["audit_only_count"])

        self.write_problem_reports(audit_code="ANALYSIS_FAILURE", audit_message="Analysis unavailable")
        with self.assertRaises(SystemExit) as failure:
            self.run_summary(*self.verified_args())
        self.assertEqual(2, failure.exception.code)
        result = json.loads(self.summary.read_text(encoding="utf-8"))
        self.assertEqual(1, result["verification"]["analysis_failure_count"])


if __name__ == "__main__":
    unittest.main()
