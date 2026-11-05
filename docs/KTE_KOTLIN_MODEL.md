# KTE Kotlin model

## Ownership

The `.kte` PSI and document own edits. Kotlin PSI is an analysis view and is never written to disk.

| Concern | Owner |
| --- | --- |
| Template syntax, paths, missing and duplicate arguments | KTE PSI and KTE annotators |
| Kotlin diagnostics, semantic colors, reference resolution | Cached whole-template Kotlin projection |
| Native Kotlin completion at the caret | Completion-scoped injected Kotlin fragment |
| `.kte` source edits and intentions | KTE PSI/document handlers |

`KteTemplateSourceFacts` supplies imports and parameter declarations to the whole-template builder and completion context. The two Kotlin representations intentionally differ in size: the whole file preserves scopes across directives, while the injected fragment gives IntelliJ's completion pipeline a Kotlin file at the caret. They must agree on imports, parameters, and visible locals.

`KteTemplateSignatureService` caches only declarations from the template file. Hover documentation displays the declared type text; contract checks, references, and completion do not create separate Kotlin type probes while reading a signature.

## Projection rules

- A source mapping covers only text copied exactly from `.kte` to the in-memory Kotlin file. Generated names, wrapper code, and synthetic child-template stubs have no source mapping.
- A Kotlin diagnostic appears in `.kte` only when its complete range fits one mapped span. Diagnostics spanning generated and source text are dropped.
- KTE owns missing and duplicate template-call arguments. Kotlin owns ordinary Kotlin call errors, mapped argument expression errors, and unsupported template-call names or excess positional arguments. Suppress Kotlin argument diagnostics only when they belong to a generated template call.
- The unused-import warning uses identifier matching in copied source spans. It skips star imports and can miss edge cases such as names in strings or backtick identifiers. Treat it as a conservative editor hint.
- The cached model depends on its `.kte` PSI, referenced child templates, project roots, and VFS structure. Kotlin analysis symbols and types are used only inside an Analysis API session; they are not cached in the model.
- An unexpected whole-template diagnostic analysis failure produces a visible editor warning. Check the IDE log for the underlying exception.
- Completion still uses injected fragments. Do not redirect it to the whole-template file without proving host offset mapping and insertion behavior in an IDE fixture.

## JTE mode boundary

The projection uses `gg.jte.TemplateOutput` and `gg.jte.Content`, the common JTE runtime interfaces. JTE's Plain mode generates those types. HTML mode generates `gg.jte.html.HtmlTemplateOutput` and `gg.jte.html.HtmlContent`. Most template expressions use common members, but mode-specific uses and content parameter types can produce different diagnostics. The plugin does not currently read a project's JTE content-mode configuration. Decide how to detect that configuration before claiming exact HTML-mode parity.

`checkJteKotlinCodegen` verifies these code-generation shapes against JTE 3.2.4 in an isolated JVM. Its separate source set keeps JTE's compiler dependencies out of IntelliJ fixture tests.

## Platform API boundary

`KteKotlinModuleContext` contains the Kotlin module setup and the injected-file context key. The verifier still reports `Key.findKeyByName` as deprecated on all configured IDE versions. On the 263 build, it also reports two `AnalyzeKt.analyze` calls as deprecated. `KteFileViewProvider.getContentElementType` is experimental on all three. These are the remaining platform migration points; keep them behind their current boundaries and check replacements against each supported IDE version before changing them.

## Verification

Run these before changing projection or module setup:

```bash
./gradlew check verifyPlugin --no-daemon
```

The IDE fixture uses a 4 GiB test JVM heap. At 2 GiB, repeated K2 completion tests caused sustained garbage collection and hit IntelliJ's 100-second completion timeout.

In a real JTE Kotlin project, smoke-test these editor actions in both HTML and Plain configurations:

1. Change a template `@param` and a referenced child template's signature; diagnostics and navigation should refresh.
2. Change a Kotlin class used by a template without editing the template; diagnostics and member resolution should refresh.
3. Try a non-Boolean `@if`, an unsafe nullable access, a wrong child-template value type, an unknown named argument, and an excess positional argument. Errors should land on `.kte` text.
4. Complete a member inside an output expression and a loop, then apply the completion. The edit should affect only `.kte` source.
5. Navigate from a later expression to a parameter and an earlier local declaration; inspect Quick Documentation. Run Find Usages and rename on a template parameter.
6. Check a valid import, an unused explicit import, and its removal intention.

These manual checks complement the fixture tests. The fixture's actual-runtime check validates API signatures and Kotlin syntax, but does not compile the synthetic projection as a full JTE application.
