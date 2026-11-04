# Auditing KTE diagnostics in a real project

The `KteDiagnosticAudit` inspection exports the plugin's KTE editor errors and warnings through IntelliJ's batch inspection runner. It is disabled in normal IDE profiles. The report contains one finding per source location, with an origin and code such as `[KTE/KOTLIN/UNRESOLVED_REFERENCE]` or `[KTE/CONTRACT/MISSING_REQUIRED_ARGUMENT]`.

## Run

1. Make a disposable copy of the target project. Include its Gradle files, KTE sources, Kotlin sources, and generated sources needed for project sync. Run the inspection against the copy so indexing and Gradle sync cannot alter your working tree.
2. From this plugin repository, build and run the IDE inspection command. Save its output so file coverage can be checked:

   ```bash
   ./gradlew runIde --no-daemon --console=plain \
     --args="inspect /absolute/project-copy $PWD/audit/KteAudit.xml /absolute/report-dir -d /absolute/project-copy/path/to/jte -v2" \
     > /absolute/inspection.log 2>&1
   ```

3. Summarize and prepare a triage sheet:

   ```bash
   python3 audit/summarize.py /absolute/report-dir/KteDiagnosticAudit.xml \
     --templates /absolute/project-copy/path/to/jte \
     --project-root /absolute/project-copy \
     --scan-log /absolute/inspection.log \
     --annotator /absolute/report-dir/Annotator.xml \
     --plugin-revision 'plugin-commit-or-build-id' \
     --ide-build 'IDE-build-used-for-the-run' \
     --project-revision 'original-project-revision' \
     --json /absolute/report-dir/summary.json \
     --csv /absolute/report-dir/triage.csv
   ```

The summary records a SHA-256 fingerprint of all `.kte` source files, the supplied run versions, counts by severity, origin, and code, and a row for every finding. If the plugin build included local changes, say so in `--plugin-revision`. `scan_coverage` compares the `-v2` log to the source tree. A verified run requires both `--scan-log` and `--annotator`. The command exits with an error if coverage or editor parity differs, or if a file's analysis failed. It still writes the JSON and CSV for investigation. The CSV adds blank `classification` and `notes` columns for manual review. Keep reports from private projects outside this repository.

For an exploratory summary without both checks, pass `--allow-unverified`. Its JSON result says `verification.status: unverified`; do not interpret a zero count as a clean project. Run the summarizer's synthetic report tests with `python3 -B -m unittest discover -s audit -p 'test_*.py'`.

## Interpret

The optional `editor_parity` result compares each audit finding to IntelliJ's `Annotator.xml` report by file, line, column, length, severity, and message. A complete match means this batch run exported the same diagnostics as the plugin's editor annotators in that run. Review any `ANALYSIS_FAILURE` before interpreting a low issue count.

Classify representative findings by root cause, then verify them against the target template declaration, JTE's generated Kotlin, and a project build. A compiler clean file can still have useful IDE warnings; a generated file alone does not prove the build passed. Fix or suppress one root cause at a time, rerun the same snapshot, and compare counts and locations.

This inspection exports the plugin's KTE parser, structure, template contract, and source-mapped Kotlin errors and warnings. It does not claim to export every IntelliJ inspection, unmappable raw Kotlin diagnostic, or build/compiler failure. Its result is an audit of the plugin's current behavior, not a correctness oracle for the project.
