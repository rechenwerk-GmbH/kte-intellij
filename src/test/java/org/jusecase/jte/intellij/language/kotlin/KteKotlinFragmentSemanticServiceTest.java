package org.jusecase.jte.intellij.language.kotlin;

import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;

import java.util.Set;

public class KteKotlinFragmentSemanticServiceTest extends KtePluginFixtureSupport {
    public void testResolvesPropertyReferenceFromScopedFragment() {
        addProfileClassWithKotlinProperties();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${profile.displayName}
                """);

        PsiElement resolved = resolveInFragment("profile.displayName", "displayName");

        assertTrue(resolved instanceof PsiNamedElement);
        assertEquals("displayName", ((PsiNamedElement) resolved).getName());
    }

    public void testMapsGeneratedParameterLocalBackToTemplateParam() {
        addProfileClassWithKotlinProperties();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${profile.displayName}
                """);

        PsiElement resolved = resolveInFragment("profile.displayName", "profile");

        assertSame(myFixture.getFile(), resolved.getContainingFile());
        assertTrue(Set.of("profile: Profile", "profile").contains(resolved.getText()));
    }

    public void testMapsVisibleStatementLocalBackToTemplateStatement() {
        addCareOfferingFixture();

        myFixture.configureByText("facility.kte", """
                @import com.example.Page
                @param page: Page
                !{val careOfferingForm = requireNotNull(page.careOfferingForm)}
                ${careOfferingForm.displayName}
                """);

        PsiElement resolved = resolveInFragment("careOfferingForm.displayName", "careOfferingForm");

        assertSame(myFixture.getFile(), resolved.getContainingFile());
        assertTrue(resolved.getText().contains("val careOfferingForm"));
    }

    public void testDoesNotMapStatementLocalOutOfTemplateContentBlock() {
        addTemplateRoot();
        addLayoutTemplate();

        myFixture.configureByText("content.kte", """
                @template.layout(title = "Title", content = @`
                    !{val contentLocal = "inside"}
                    ${contentLocal}
                `)
                ${contentLocal.toString()}
                """);

        assertNull(resolveInFragmentOrNull("contentLocal.toString()", "contentLocal"));
    }

    public void testDoesNotMapIfBranchLocalIntoElseBranch() {
        addCareOfferingFixture();

        myFixture.configureByText("facility.kte", """
                @import com.example.Page
                @param page: Page
                @if(page.careOfferingForm != null)
                    !{val careOfferingForm = requireNotNull(page.careOfferingForm)}
                    ${careOfferingForm.displayName}
                @else
                    ${careOfferingForm}
                @endif
                """);

        assertNull(resolveInFragmentOrNull("careOfferingForm", "careOfferingForm"));
    }

    private PsiElement resolveInFragment(String injectionText, String referenceText) {
        PsiElement resolved = resolveInFragmentOrNull(injectionText, referenceText);
        assertNotNull(resolved);
        return resolved;
    }

    private PsiElement resolveInFragmentOrNull(String injectionText, String referenceText) {
        JtePsiJavaInjection injection = injection(injectionText);
        int offset = injection.getText().indexOf(referenceText);
        assertTrue("Expected reference text '" + referenceText + "' in " + injection.getText(), offset >= 0);
        return KteKotlinFragmentSemanticService.resolveReferenceAtTemplateRange(
                injection,
                TextRange.from(offset, referenceText.length())
        );
    }

    private JtePsiJavaInjection injection(String text) {
        return PsiTreeUtil.findChildrenOfType(myFixture.getFile(), JtePsiJavaInjection.class)
                .stream()
                .filter(injection -> text.equals(injection.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected injection '" + text + "'"));
    }
}
