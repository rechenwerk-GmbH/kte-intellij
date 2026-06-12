# kte-intellij

IntelliJ plugin for Kotlin jte template files (`.kte`).

This repository contains the standalone KTE plugin split from
[casid/jte-intellij](https://github.com/casid/jte-intellij). Java `.jte`
templates remain supported by the original jte plugin. This plugin registers
only `.kte` files.

## Current Scope

- `.kte` file type support
- Kotlin K2 completion, resolve, references, documentation, and diagnostics
- Synthetic Kotlin model for template-aware Kotlin analysis
- Template parameter validation and selected deterministic quick fixes
- Formatting, folding, brace matching, live templates, and template navigation
- Debug action: `Tools > KTE > Open Synthetic Kotlin for Current .kte`

## Requirements

- IntelliJ IDEA 2026.1 or newer
- Kotlin plugin bundled with IntelliJ IDEA

## Known Caveat

KTE support no longer uses injected Kotlin code fragments for the Kotlin parts
of a template. This avoids K2 code-fragment errors for directives such as
`@import`, but Kotlin syntax highlighting inside template fragments does not
behave like the old injected Kotlin highlighting path yet.

## Maintenance Note

The K2 path uses some JetBrains and Kotlin APIs that are not stable public
plugin APIs. The first releases should be treated as compatibility-focused and
may need updates when IntelliJ or the Kotlin plugin changes.
