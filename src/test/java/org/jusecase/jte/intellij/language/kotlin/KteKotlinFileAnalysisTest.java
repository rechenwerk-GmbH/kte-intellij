package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

public class KteKotlinFileAnalysisTest extends KtePluginFixtureSupport {
    public void testFullTemplateReportsMappedUnsafeCallAndConditionType() {
        addProfileClassWithKotlinProperties();
        String source = """
                @import com.example.Profile
                @param profile: Profile
                @if(profile.displayName)
                    ${profile.manager.displayName}
                @endif
                """;
        myFixture.configureByText("unsafe.kte", source);

        KteKotlinFileAnalysis.Result result = analyze();
        assertTrue(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.severity() == HighlightSeverity.ERROR &&
                        diagnostic.range().intersects(sourceRange(source, "profile.displayName")) &&
                        diagnostic.factoryName().contains("CONDITION")));
        assertTrue(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.severity() == HighlightSeverity.ERROR &&
                        diagnostic.range().intersects(sourceRange(source, "manager.displayName")) &&
                        diagnostic.factoryName().contains("UNSAFE")));
        assertTrue(result.diagnostics().toString(), result.diagnostics().stream().allMatch(diagnostic ->
                diagnostic.range().getStartOffset() >= 0 && diagnostic.range().getEndOffset() <= source.length()));
    }

    public void testUnusedImportAndSemanticColorsComeFromSourceModel() {
        addProfileClassWithKotlinProperties();
        myFixture.addFileToProject("src/com/example/Unused.kt", "package com.example\nclass Unused\n");
        String source = """
                @import com.example.Profile
                @import com.example.Unused
                @param profile: Profile
                ${profile.displayName}
                """;
        myFixture.configureByText("colors.kte", source);

        KteKotlinFileAnalysis.Result result = analyze();
        assertTrue(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(diagnostic ->
                "UNUSED_IMPORT".equals(diagnostic.factoryName()) &&
                        diagnostic.range().equals(sourceRange(source, "com.example.Unused"))));
        assertFalse(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(diagnostic ->
                "UNUSED_IMPORT".equals(diagnostic.factoryName()) &&
                        diagnostic.range().equals(sourceRange(source, "com.example.Profile"))));
        assertTrue(result.highlights().toString(), result.highlights().stream().anyMatch(highlight ->
                highlight.key() == DefaultLanguageHighlighterColors.PARAMETER &&
                        highlight.range().equals(TextRange.from(
                                source.lastIndexOf("profile.displayName"), "profile".length()))));
    }

    public void testProjectionUsesActualJteRuntimeApi() throws Exception {
        addActualJteRuntimeToModule();
        myFixture.configureByText("runtime.kte", """
                @param title: String
                ${title}
                @template.missing(title, @`
                    ${title}
                `)
                """);

        KteKotlinModelService.Model model = KteKotlinModelService.getInstance(getProject())
                .getModel(myFixture.getFile());
        assertTrue(gg.jte.TemplateOutput.class.isInterface());
        assertEquals(void.class, gg.jte.Content.class.getMethod("writeTo", gg.jte.TemplateOutput.class).getReturnType());
        assertTrue(model.projection().getText(), model.projection().getText().contains(
                "override fun writeTo(jteOutput: gg.jte.TemplateOutput)"));
        assertTrue(PsiTreeUtil.findChildrenOfType(model.ktFile(), PsiErrorElement.class).toString(),
                PsiTreeUtil.findChildrenOfType(model.ktFile(), PsiErrorElement.class).isEmpty());
    }

    public void testTemplateCallTypeMismatchAndParamTypeAreMapped() {
        addTemplateRoot();
        myFixture.addFileToProject("components/card.kte", "@param title: String\n");
        String source = """
                @param missing: MissingType
                @template.components.card(title = 42)
                """;
        myFixture.configureByText("calls.kte", source);

        KteKotlinFileAnalysis.Result result = analyze();
        assertTrue(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.range().intersects(sourceRange(source, "MissingType")) &&
                        diagnostic.factoryName().contains("UNRESOLVED")));
        assertTrue(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.range().intersects(sourceRange(source, "42")) &&
                        (diagnostic.factoryName().contains("MISMATCH") ||
                                diagnostic.factoryName().contains("APPLICABLE"))));
    }

    public void testUnknownNamedAndExcessPositionalTemplateArgumentsAreReported() {
        addTemplateRoot();
        myFixture.addFileToProject("components/card.kte", "@param title: String\n");
        String source = """
                @template.components.card(unknown = "Title")
                @template.components.card("Title", "Extra")
                """;
        myFixture.configureByText("calls.kte", source);

        KteKotlinFileAnalysis.Result result = analyze();
        assertTrue(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.range().intersects(sourceRange(source, "unknown")) &&
                        diagnostic.severity() == HighlightSeverity.ERROR));
        assertTrue(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.range().intersects(sourceRange(source, "\"Extra\"")) &&
                        diagnostic.severity() == HighlightSeverity.ERROR));
    }

    public void testExternalKotlinEditUpdatesAnalysisWithoutRebuildingProjection() {
        PsiFile modelSource = myFixture.addFileToProject("src/com/example/Holder.kt", """
                package com.example
                class Holder(val value: String)
                """);
        String source = """
                @import com.example.Holder
                @param holder: Holder
                ${holder.value}
                """;
        myFixture.configureByText("external.kte", source);

        KteKotlinModelService service = KteKotlinModelService.getInstance(getProject());
        KteKotlinModelService.Model model = service.getModel(myFixture.getFile());
        KteKotlinFileAnalysis.Result before = analyze();
        assertFalse(before.diagnostics().toString(), before.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.range().intersects(sourceRange(source, "value")) &&
                        diagnostic.factoryName().contains("UNRESOLVED")));

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            var document = PsiDocumentManager.getInstance(getProject()).getDocument(modelSource);
            assertNotNull(document);
            document.setText("package com.example\nclass Holder(val renamed: String)\n");
            PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        });

        assertSame(model, service.getModel(myFixture.getFile()));
        KteKotlinFileAnalysis.Result after = analyze();
        assertTrue(after.diagnostics().toString(), after.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.range().intersects(sourceRange(source, "value")) &&
                        diagnostic.factoryName().contains("UNRESOLVED")));
    }

    public void testSemanticColorsDistinguishFunctionsObjectsAndLocals() {
        addSupportHelpers();
        String source = """
                @import com.example.UiLabels
                @import com.example.i18n
                @param title: String
                !{val decorated = i18n(title)}
                ${decorated + UiLabels.title}
                """;
        myFixture.configureByText("semantic.kte", source);

        KteKotlinFileAnalysis.Result result = analyze();
        assertColor(result, sourceRange(source, "i18n(title)"), "i18n",
                DefaultLanguageHighlighterColors.FUNCTION_CALL);
        assertColor(result, sourceRange(source, "decorated +"), "decorated",
                DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
        assertColor(result, sourceRange(source, "UiLabels.title"), "UiLabels",
                DefaultLanguageHighlighterColors.CLASS_NAME);
    }

    private void assertColor(KteKotlinFileAnalysis.Result result,
                             TextRange context,
                             String name,
                             com.intellij.openapi.editor.colors.TextAttributesKey expected) {
        TextRange range = TextRange.from(context.getStartOffset(), name.length());
        assertTrue("Expected " + name + " color " + expected + " in " + result.highlights(),
                result.highlights().stream().anyMatch(highlight ->
                        highlight.range().equals(range) && highlight.key() == expected));
    }

    private KteKotlinFileAnalysis.Result analyze() {
        return new KteKotlinFileAnalysis().collect(myFixture.getFile());
    }

    private TextRange sourceRange(String source, String text) {
        int start = source.indexOf(text);
        assertTrue("Missing source text: " + text, start >= 0);
        return TextRange.from(start, text.length());
    }
}
