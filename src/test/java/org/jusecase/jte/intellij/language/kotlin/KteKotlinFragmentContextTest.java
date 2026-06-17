package org.jusecase.jte.intellij.language.kotlin;

import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;

import java.util.List;

public class KteKotlinFragmentContextTest extends KtePluginFixtureSupport {
    public void testCollectsOnlyCompletionRelevantKotlinFragments() {
        addProfileClassWithKotlinProperties();

        KteKotlinFragmentContext context = fragmentContext("""
                @import com.example.Profile
                @param profile: Profile
                @param profiles: List<Profile>
                
                ${profile.displayName}
                !{val selected = requireNotNull(profile.manager)}
                @if(profile.active)
                    ${selected.email}
                @endif
                @for(item in profiles)
                    ${item.displayName}
                @endfor
                @template.components.card(profile = selected)
                """);

        List<String> injectionTexts = context.places()
                .stream()
                .map(place -> place.injection().getText())
                .toList();

        assertTrue(injectionTexts.contains("profile.displayName"));
        assertTrue(injectionTexts.contains("val selected = requireNotNull(profile.manager)"));
        assertTrue(injectionTexts.contains("profile.active"));
        assertTrue(injectionTexts.contains("selected.email"));
        assertTrue(injectionTexts.contains("item in profiles"));
        assertTrue(injectionTexts.contains("item.displayName"));
        assertTrue(injectionTexts.contains("selected"));
        assertFalse(injectionTexts.contains("com.example.Profile"));
        assertFalse(injectionTexts.contains("profile: Profile"));
        assertFalse(injectionTexts.contains("profiles: List<Profile>"));
    }

    public void testBuildsImportsAndTemplateParameterLocals() {
        addProfileClassWithKotlinProperties();

        KteKotlinFragmentContext context = fragmentContext("""
                @import com.example.Profile
                @param profile: Profile
                @param profiles: List<Profile>
                
                ${profile.displayName}
                """);
        KteKotlinFragmentPlace place = findPlace(context, "profile.displayName");

        String prefix = context.prefix(place);
        String suffix = context.suffix(place);

        assertEquals("com.example.Profile", context.parameters().get("profile"));
        assertEquals("List<com.example.Profile>", context.parameters().get("profiles"));
        assertTrue(prefix.contains("typealias Profile = com.example.Profile\n"));
        assertTrue(prefix.contains("fun __jte_render() {\n"));
        assertTrue(prefix.contains("val profile = null as com.example.Profile\n"));
        assertTrue(prefix.contains("val profiles = null as List<com.example.Profile>\n"));
        assertTrue(prefix.endsWith("val profiles = null as List<com.example.Profile>\n"));
        assertEquals("\n}\n", suffix);
    }

    public void testIncludesOnlyVisiblePreviousStatementLocals() {
        addCareOfferingFixture();

        KteKotlinFragmentContext context = fragmentContext("""
                @import com.example.Page
                @param page: Page
                
                !{val first = requireNotNull(page.careOfferingForm)}
                ${first.displayName}
                !{val future = first}
                """);
        KteKotlinFragmentPlace place = findPlace(context, "first.displayName");

        String prefix = context.prefix(place);

        assertTrue(prefix.contains("val first = requireNotNull(page.careOfferingForm)\n"));
        assertFalse(prefix.contains("val future = first"));
    }

    public void testWrapsFragmentsInsideEnclosingLoops() {
        addProfileClassWithKotlinProperties();

        KteKotlinFragmentContext context = fragmentContext("""
                @import com.example.Profile
                @param profiles: List<Profile>
                
                @for(profile in profiles)
                    ${profile.displayName}
                @endfor
                """);
        KteKotlinFragmentPlace place = findPlace(context, "profile.displayName");

        String prefix = context.prefix(place);
        String suffix = context.suffix(place);

        assertTrue(prefix.contains("for (profile in profiles) {\n"));
        assertTrue(prefix.endsWith("for (profile in profiles) {\n"));
        assertEquals("\n}\n}\n", suffix);
    }

    private KteKotlinFragmentContext fragmentContext(String text) {
        PsiFile file = myFixture.configureByText("context.kte", text);
        KtePsiJavaContent host = PsiTreeUtil.findChildOfType(file, KtePsiJavaContent.class);
        assertNotNull(host);
        return KteKotlinFragmentContext.from(host);
    }

    private KteKotlinFragmentPlace findPlace(KteKotlinFragmentContext context, String injectionText) {
        return context.places()
                .stream()
                .filter(place -> injectionText.equals(place.injection().getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected injected place '" + injectionText + "' in " +
                        context.places().stream().map(place -> place.injection().getText()).toList()));
    }
}
