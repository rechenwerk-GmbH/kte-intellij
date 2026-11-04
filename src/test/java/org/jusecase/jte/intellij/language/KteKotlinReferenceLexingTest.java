package org.jusecase.jte.intellij.language;

import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.JtePsiOutput;

import java.util.Arrays;

public class KteKotlinReferenceLexingTest extends KtePluginFixtureSupport {
    public void testPlainStringWordsDoNotBecomeKotlinReferences() {
        myFixture.configureByText("literal.kte", """
                @param profile: String
                ${"profile"}
                """);

        JtePsiOutput output = PsiTreeUtil.findChildOfType(myFixture.getFile(), JtePsiOutput.class);
        assertNotNull(output);
        JtePsiJavaInjection injection = PsiTreeUtil.getChildOfType(output, JtePsiJavaInjection.class);
        assertNotNull(injection);
        assertFalse(Arrays.toString(injection.getReferences()), Arrays.stream(injection.getReferences())
                .map(PsiReference::getCanonicalText)
                .anyMatch("profile"::equals));
    }

    public void testStringInterpolationStillResolvesParameter() {
        myFixture.configureByText("interpolation.kte", """
                @param profile: String
                ${"Hello $profile"}
                """);

        JtePsiOutput output = PsiTreeUtil.findChildOfType(myFixture.getFile(), JtePsiOutput.class);
        assertNotNull(output);
        JtePsiJavaInjection injection = PsiTreeUtil.getChildOfType(output, JtePsiJavaInjection.class);
        assertNotNull(injection);
        assertTrue(Arrays.toString(injection.getReferences()), Arrays.stream(injection.getReferences())
                .anyMatch(reference -> "profile".equals(reference.getCanonicalText()) && reference.resolve() != null));
    }

    public void testCommentWordsDoNotCreateExtraReferences() {
        myFixture.configureByText("comment.kte", """
                @param profile: String
                ${/* profile */ profile}
                """);

        JtePsiOutput output = PsiTreeUtil.findChildOfType(myFixture.getFile(), JtePsiOutput.class);
        assertNotNull(output);
        JtePsiJavaInjection injection = PsiTreeUtil.getChildOfType(output, JtePsiJavaInjection.class);
        assertNotNull(injection);
        assertEquals(Arrays.toString(injection.getReferences()), 1, Arrays.stream(injection.getReferences())
                .filter(reference -> "profile".equals(reference.getCanonicalText()))
                .count());
    }
}
