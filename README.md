# kte-intellij

IntelliJ plugin for Kotlin jte template files (`.kte`).

This repository contains the standalone KTE plugin split from
[casid/jte-intellij](https://github.com/casid/jte-intellij). Java `.jte`
templates remain supported by the original jte plugin.

## Current Scope

- `.kte` file type support
- Kotlin K2 completion, resolve, references, and documentation
- Go to Declaration and Quick Documentation for types in `@param` declarations
- Source-mapped Kotlin diagnostics and semantic coloring across each template
- In-memory Kotlin model for analysis, with scoped injection for completion
- Template parameter validation and selected deterministic quick fixes
- Formatting, folding, brace matching, live templates, and template navigation

## Requirements

- IntelliJ IDEA 2026.1 or newer
- Kotlin plugin bundled with IntelliJ IDEA

## Editor Diagnostics

The editor analyzes a source-mapped, in-memory Kotlin view of the complete
template. It reports Kotlin syntax, type, nullability, and unresolved-reference
diagnostics when their ranges map exactly to `.kte` source. It also colors
resolved Kotlin symbols and warns about unused explicit `@import` directives.
Generated Kotlin scaffolding cannot produce a source edit or visible diagnostic.

Template structure and required argument checks remain KTE-specific. Kotlin
completion continues to use a scoped injected fragment at the caret.

The in-memory model uses JTE's common output and content interfaces. JTE HTML
mode generates narrower HTML interfaces, so mode-specific Kotlin may receive
different diagnostics. See [the model notes](docs/KTE_KOTLIN_MODEL.md) for the
architecture, verification commands, and manual editor checks.

For project-wide diagnostic inventories, see [the KTE audit workflow](docs/KTE_AUDIT.md).
