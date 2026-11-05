package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

import java.util.List;
import java.util.Locale;

public class KteKotlinDiagnosticAnnotatorTest extends KtePluginFixtureSupport {
    public void testMissingAvatarParametersAreHighlightedAndClearWhenRestored() {
        String declarations = """
                @param src: String?
                @param alt: String = "Photo de profil"
                @param size: String = "size-8"
                """;
        String body = """
                <img
                    src="${ src ?: "/images/default-avatar.png" }"
                    alt="${ alt }"
                    class="${ size } rounded-xl object-cover"
                    loading="lazy"
                >
                """;
        myFixture.configureByText("avatar.kte", declarations + body);
        assertTrue(unresolved().toString(), unresolved().isEmpty());

        replaceText(body);
        List<HighlightInfo> missing = unresolved();
        assertEquals(missing.toString(), 3, missing.size());
        assertUnresolvedRangeAt(missing, body.indexOf("src ?:"), "src");
        assertUnresolvedRangeAt(missing, body.indexOf("alt }"), "alt");
        assertUnresolvedRangeAt(missing, body.indexOf("size }"), "size");

        replaceText(declarations + body);
        assertTrue(unresolved().toString(), unresolved().isEmpty());
    }

    public void testUnknownNameInOutputIsHighlightedAtIdentifier() {
        String text = "<p>${doesNotExist}</p>";
        myFixture.configureByText("unknown.kte", text);

        List<HighlightInfo> errors = unresolved();
        assertEquals(errors.toString(), 1, errors.size());
        assertUnresolvedRange(errors, text, "doesNotExist");
    }

    public void testVisibleParametersLocalsLoopsAndImportsDoNotReportUnresolvedReferences() {
        addProfileClassWithKotlinProperties();
        myFixture.configureByText("valid.kte", """
                @import com.example.Profile
                @param profile: Profile
                @param profiles: List<Profile>
                !{val selected = profile.displayName}
                <h1>${selected}</h1>
                @if(profile.active)
                    @for(item in profiles)
                        <span>${item.displayName}</span>
                    @endfor
                @endif
                """);

        assertTrue(unresolved().toString(), unresolved().isEmpty());
    }

    public void testConditionsLoopsStatementsAndTemplateArgumentsReportOnlySourceNames() {
        addTemplateRoot();
        addHeaderTemplate();
        String text = """
                @if(missingCondition)
                @endif
                @for(item in missingItems)
                    ${item}
                @endfor
                !{val local = missingValue}
                @template.components.header(title = missingTitle)
                """;
        myFixture.configureByText("contexts.kte", text);

        List<HighlightInfo> errors = unresolved();
        assertEquals(errors.toString(), 4, errors.size());
        for (String name : List.of("missingCondition", "missingItems", "missingValue", "missingTitle")) {
            assertUnresolvedRange(errors, text, name);
        }
    }

    public void testExternalKotlinEditRefreshesEditorDiagnostics() {
        PsiFile kotlinFile = myFixture.addFileToProject("src/com/example/Holder.kt", """
                package com.example
                class Holder(val value: String)
                """);
        String source = """
                @import com.example.Holder
                @param holder: Holder
                ${holder.value}
                """;
        myFixture.configureByText("external.kte", source);
        assertTrue(unresolved().toString(), unresolved().isEmpty());

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            var document = PsiDocumentManager.getInstance(getProject()).getDocument(kotlinFile);
            assertNotNull(document);
            document.setText("package com.example\nclass Holder(val renamed: String)\n");
            PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        });

        assertUnresolvedRange(unresolved(), source, "value");
    }

    private List<HighlightInfo> unresolved() {
        return myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
                .filter(info -> info.getDescription() != null &&
                        info.getDescription().toLowerCase(Locale.ROOT).contains("unresolved reference"))
                .toList();
    }

    private void assertUnresolvedRange(List<HighlightInfo> errors, String text, String name) {
        int start = text.indexOf("${" + name + "}");
        start = start >= 0 ? start + 2 : text.indexOf(name);
        assertUnresolvedRangeAt(errors, start, name);
    }

    private void assertUnresolvedRangeAt(List<HighlightInfo> errors, int expectedStart, String name) {
        assertTrue("Expected " + name + " in fixture", expectedStart >= 0);
        assertTrue("Expected unresolved " + name + " at " + expectedStart + " in " + errors,
                errors.stream().anyMatch(info -> info.getStartOffset() == expectedStart &&
                        info.getEndOffset() == expectedStart + name.length()));
    }

    private void replaceText(String text) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            myFixture.getEditor().getDocument().setText(text);
            PsiDocumentManager.getInstance(getProject()).commitDocument(myFixture.getEditor().getDocument());
        });
    }
}
