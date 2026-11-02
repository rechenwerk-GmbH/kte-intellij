# kte-intellij

IntelliJ plugin for Kotlin jte template files (`.kte`).

This repository contains the standalone KTE plugin split from
[casid/jte-intellij](https://github.com/casid/jte-intellij). Java `.jte`
templates remain supported by the original jte plugin.

## Current Scope

- `.kte` file type support
- Kotlin K2 completion, resolve, references, and documentation
- Editor highlighting for unresolved Kotlin references in template code
- Scoped Kotlin fragments for template-aware Kotlin analysis
- Template parameter validation and selected deterministic quick fixes
- Formatting, folding, brace matching, live templates, and template navigation

## Requirements

- IntelliJ IDEA 2026.1 or newer
- Kotlin plugin bundled with IntelliJ IDEA

## Editor Diagnostics

The editor highlights unresolved Kotlin names in output expressions (`${...}`),
conditions, loops, statements, and template arguments. Removing an `@param`
declaration, for example, highlights uses of that parameter in the template;
restoring the declaration clears the error.

Template structure and template parameter checks are separate features. The
Kotlin diagnostic integration currently reports unresolved references only;
other Kotlin compiler errors and warnings are not shown in `.kte` files.
