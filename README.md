# kte-intellij

IntelliJ plugin for Kotlin jte template files (`.kte`).

This repository contains the standalone KTE plugin split from
[casid/jte-intellij](https://github.com/casid/jte-intellij). Java `.jte`
templates remain supported by the original jte plugin.

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

Completion uses scoped injected Kotlin fragments. Resolve, documentation, and
diagnostics use the synthetic Kotlin model.
