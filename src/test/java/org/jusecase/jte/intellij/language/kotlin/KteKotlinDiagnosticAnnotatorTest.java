package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

import java.util.List;
import java.util.Locale;

public class KteKotlinDiagnosticAnnotatorTest extends KtePluginFixtureSupport {
    public void testMissingAvatarParametersAreHighlightedAndClearWhenRestored() {
        String declarations = """
                @param src: String
                @param alt: String
                @param size: Int
                """;
        String body = """
                <img src="${src}" alt="${alt}" width="${size}">
                """;
        myFixture.configureByText("avatar.kte", declarations + body);
        assertTrue(unresolved().toString(), unresolved().isEmpty());

        replaceText(body);
        List<HighlightInfo> missing = unresolved();
        assertEquals(missing.toString(), 3, missing.size());
        assertUnresolvedRange(missing, body, "src");
        assertUnresolvedRange(missing, body, "alt");
        assertUnresolvedRange(missing, body, "size");

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

    private List<HighlightInfo> unresolved() {
        return myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
                .filter(info -> info.getDescription() != null &&
                        info.getDescription().toLowerCase(Locale.ROOT).contains("unresolved reference"))
                .toList();
    }

    private void assertUnresolvedRange(List<HighlightInfo> errors, String text, String name) {
        int start = text.indexOf("${" + name + "}");
        start = start >= 0 ? start + 2 : text.indexOf(name);
        assertTrue("Expected " + name + " in fixture", start >= 0);
        int expectedStart = start;
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
