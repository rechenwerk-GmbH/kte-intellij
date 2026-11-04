package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

import java.util.List;

public class KteKotlinProjectionTest extends KtePluginFixtureSupport {
    public void testWholeTemplateProjectionPreservesKotlinScopesAndExactSourceRanges() {
        String source = """
                @import com.example.Profile
                @param profile: Profile
                @param title: String = "Welcome"
                !{val label = title + profile.displayName}
                @if(profile.active)
                    @for(tag in profile.tags)
                        ${label + tag}
                    @endfor
                @endif
                """;
        myFixture.configureByText("profile.kte", source);

        KteKotlinProjection projection = model().projection();
        assertTrue(projection.getText(), projection.getText().contains("fun render(jteOutput: gg.jte.TemplateOutput, profile: Profile, title: String = \"Welcome\")"));
        assertTrue(projection.getText(), projection.getText().contains("if (profile.active)"));
        assertTrue(projection.getText(), projection.getText().contains("for (tag in profile.tags)"));
        assertTrue(projection.getText(), projection.getText().contains("writeUserContent(label + tag)"));
        assertCopiedRanges(source, projection);
        assertTrue(PsiTreeUtil.findChildrenOfType(model().ktFile(), PsiErrorElement.class).toString(),
                PsiTreeUtil.findChildrenOfType(model().ktFile(), PsiErrorElement.class).isEmpty());
    }

    public void testGeneratedFunctionNamesNeverMapToTemplateSource() {
        addTemplateRoot();
        addHeaderTemplate();
        String source = """
                @param title: String
                @template.components.header(title = title)
                """;
        myFixture.configureByText("page.kte", source);

        KteKotlinProjection projection = model().projection();
        int call = projection.getText().lastIndexOf("__jte_template_");
        assertTrue(projection.getText(), call >= 0);
        assertNull(projection.mapKotlinOffsetToTemplate(call));
        assertNull(projection.mapKotlinRangeToTemplate(TextRange.from(call, "__jte_template_".length())));
        assertCopiedRanges(source, projection);
    }

    public void testNestedContentUsesRuntimeMethodSignature() {
        String source = """
                @param title: String
                @template.card(title, @`
                    ${title}
                `)
                """;
        myFixture.configureByText("content.kte", source);

        KteKotlinProjection projection = model().projection();
        assertTrue(projection.getText(), projection.getText().contains(
                "override fun writeTo(jteOutput: gg.jte.TemplateOutput)"));
        assertFalse(projection.getText(), projection.getText().contains("gg.jte.TemplateOutput()"));
        assertTrue(PsiTreeUtil.findChildrenOfType(model().ktFile(), PsiErrorElement.class).toString(),
                PsiTreeUtil.findChildrenOfType(model().ktFile(), PsiErrorElement.class).isEmpty());
        assertCopiedRanges(source, projection);
    }

    public void testForElseAndUnsafeOutputKeepGeneratedControlFlowUnmapped() {
        String source = """
                @param values: List<String>
                @for(value in values)
                    $unsafe{value}
                @else
                    ${values.size}
                @endfor
                """;
        myFixture.configureByText("loop.kte", source);

        KteKotlinProjection projection = model().projection();
        assertTrue(projection.getText(), projection.getText().contains("writeUnsafeContent(value)"));
        assertTrue(projection.getText(), projection.getText().contains("var __jteForElse0 = true"));
        int generatedFlag = projection.getText().indexOf("__jteForElse0");
        assertNull(projection.mapKotlinOffsetToTemplate(generatedFlag));
        assertCopiedRanges(source, projection);
        assertTrue(PsiTreeUtil.findChildrenOfType(model().ktFile(), PsiErrorElement.class).toString(),
                PsiTreeUtil.findChildrenOfType(model().ktFile(), PsiErrorElement.class).isEmpty());
    }

    public void testTemplateEditRebuildsModel() {
        myFixture.configureByText("change.kte", "@param title: String\n${title}\n");
        KteKotlinModelService.Model before = model();
        assertSame(before, model());

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            myFixture.getEditor().getDocument().setText("@param title: String\n${title.length}\n");
            PsiDocumentManager.getInstance(getProject()).commitDocument(myFixture.getEditor().getDocument());
        });

        KteKotlinModelService.Model after = model();
        assertNotSame(before, after);
        assertTrue(after.projection().getText(), after.projection().getText().contains("title.length"));
    }

    public void testChildSignatureEditRebuildsTypedCallStub() {
        addTemplateRoot();
        PsiFile child = myFixture.addFileToProject("components/card.kte", "@param title: String\n");
        myFixture.configureByText("page.kte", "@param title: String\n@template.components.card(title = title)\n");
        KteKotlinModelService.Model before = model();
        assertTrue(before.projection().getText(), before.projection().getText().contains("title: String"));

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            var document = PsiDocumentManager.getInstance(getProject()).getDocument(child);
            assertNotNull(document);
            document.setText("@param title: Int\n");
            PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        });

        KteKotlinModelService.Model after = model();
        assertNotSame(before, after);
        assertTrue(after.projection().getText(), after.projection().getText().contains("title: Int"));
    }

    public void testChildSignatureImportWinsOverConflictingParentImport() {
        addTemplateRoot();
        myFixture.addFileToProject("components/card.kte", """
                @import com.example.Profile
                @param profile: Profile
                """);
        myFixture.configureByText("page.kte", """
                @import com.other.Profile
                @param label: String
                @template.components.card(profile = label)
                """);

        String kotlin = model().projection().getText();
        assertTrue(kotlin, kotlin.contains("profile: com.example.Profile"));
    }

    public void testUnrelatedKotlinBodyEditKeepsProjection() {
        PsiFile other = myFixture.addFileToProject("src/Other.kt", "fun other() = 1\n");
        myFixture.configureByText("page.kte", "@param title: String\n${title}\n");
        KteKotlinModelService.Model before = model();

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            var document = PsiDocumentManager.getInstance(getProject()).getDocument(other);
            assertNotNull(document);
            document.setText("fun other() = 2\n");
            PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        });

        assertSame(before, model());
    }

    public void testTemplateArgumentNavigationLandsOnParameterName() {
        addTemplateRoot();
        PsiFile child = myFixture.addFileToProject("components/card.kte", "@param title: String\n");
        myFixture.configureByText("page.kte", "@param title: String\n@template.components.card(tit<caret>le = title)\n");

        var target = resolveReferenceAtCaret();
        assertSame(child, target.getContainingFile());
        assertEquals(child.getText().indexOf("title"), target.getTextOffset());
    }

    public void testIncompleteTemplatesRemainAnalyzable() {
        for (String source : List.of("", "<p>Hello</p>", "@param title: String\n${",
                "@param title: String\n@if(", "@param title: String\n@template.card(title = ")) {
            myFixture.configureByText("incomplete-" + source.length() + ".kte", source);
            KteKotlinModelService.Model model = model();
            assertNotNull(model.ktFile());
            assertCopiedRanges(source, model.projection());
        }
    }

    private KteKotlinModelService.Model model() {
        return KteKotlinModelService.getInstance(getProject()).getModel(myFixture.getFile());
    }

    private void assertCopiedRanges(String source, KteKotlinProjection projection) {
        List<KteKotlinSourceMapping> mappings = projection.getMappings();
        for (int index = 0; index < mappings.size(); index++) {
            KteKotlinSourceMapping mapping = mappings.get(index);
            assertEquals(source.substring(mapping.getTemplateRange().getStartOffset(), mapping.getTemplateRange().getEndOffset()),
                    projection.getText().substring(mapping.getKotlinRange().getStartOffset(), mapping.getKotlinRange().getEndOffset()));
            assertEquals(mapping.getTemplateRange(), projection.mapKotlinRangeToTemplate(mapping.getKotlinRange()));
            assertEquals(mapping.getKotlinRange(), projection.mapTemplateRangeToKotlin(mapping.getTemplateRange()));
            if (index > 0) {
                assertTrue(mappings.get(index - 1).getKotlinRange().getEndOffset() <= mapping.getKotlinRange().getStartOffset());
            }
        }
    }
}
