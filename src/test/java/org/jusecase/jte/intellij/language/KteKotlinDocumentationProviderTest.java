package org.jusecase.jte.intellij.language;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.DocumentationData;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import org.jusecase.jte.intellij.language.psi.JtePsiTemplateName;

public class KteKotlinDocumentationProviderTest extends KtePluginFixtureSupport {
    public void testKotlinStringParamTypeUsesPlatformDocumentation() {
        myFixture.configureByText("avatar.kte", """
                @param src: Str<caret>ing?
                ${src}
                """);

        String info = platformDocumentationHintAtCaret();

        assertNotNull(info);
        assertTrue(info, plainDocumentationText(info).contains("String"));
    }

    public void testKotlinListParamTypeUsesPlatformDocumentation() {
        myFixture.configureByText("items.kte", """
                @param items: Li<caret>st<String>
                ${items}
                """);

        String info = platformDocumentationHintAtCaret();

        assertNotNull(info);
        assertTrue(info, plainDocumentationText(info).contains("List"));
    }

    public void testQuickNavigateInfoForTemplateParamShowsDeclarationType() {
        addProfileClass();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${pro<caret>file.displayName}
                """);

        String info = quickNavigateInfoAtCaret();

        assertEquals("profile: Profile", info);
    }

    public void testQuickNavigateInfoForNullableTemplateParamShowsDeclaredType() {
        addProfileClass();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile?
                ${pro<caret>file?.displayName}
                """);

        String info = quickNavigateInfoAtCaret();

        assertEquals("profile: Profile?", info);
    }

    public void testQuickNavigateInfoPreservesImportedTypeAlias() {
        addProfileClass();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile as Person
                @param profile: Person?
                ${pro<caret>file?.displayName}
                """);

        assertEquals("profile: Person?", quickNavigateInfoAtCaret());
    }

    public void testTemplateDocumentationKeepsUnknownSourceType() {
        addTemplateRoot();
        myFixture.addFileToProject("components/broken.kte", "@param profile: MissingProfile\n");
        myFixture.configureByText("parent.kte", "@template.components.bro<caret>ken(profile = null)\n");

        String info = quickNavigateInfoAtCaret();

        assertNotNull(info);
        assertTrue(info, info.contains("profile: MissingProfile"));
    }

    public void testKotlinPropertyDocumentationFallsBackToPlatformProvider() {
        addProfileClass();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${profile.display<caret>Name}
                """);

        assertNull(quickNavigateInfoAtCaret());

        String info = platformDocumentationHintAtCaret();
        assertNotNull(info);
        assertTrue(info, info.contains("displayName"));
    }

    public void testImportedKotlinClassDocumentationFallsBackToPlatformProvider() {
        addProfileClass();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Pro<caret>file
                ${profile.displayName}
                """);

        assertNull(quickNavigateInfoAtCaret());

        String info = platformDocumentationHintAtCaret();
        assertNotNull(info);
        assertTrue(info, plainDocumentationText(info).contains("data class Profile"));
    }

    public void testImportedKotlinFunctionDocumentationFallsBackToPlatformProvider() {
        addSupportHelpers();

        myFixture.configureByText("helpers.kte", """
                @import com.example.i18n
                ${i18<caret>n("title")}
                """);

        assertNull(quickNavigateInfoAtCaret());

        String info = platformDocumentationHintAtCaret();
        assertNotNull(info);
        String infoText = plainDocumentationText(info);
        assertTrue(info, infoText.contains("fun i18n"));
        assertFalse(info, infoText.contains("__args"));
    }

    public void testEnumEntryDocumentationFallsBackToPlatformProvider() {
        addSupportHelpers();

        myFixture.configureByText("form.kte", """
                @import com.example.HiddenHttpMethod
                @template.form.hidden_http_method(method = HiddenHttpMethod.P<caret>UT)
                """);

        assertNull(quickNavigateInfoAtCaret());

        String info = platformDocumentationHintAtCaret();
        assertNotNull(info);
        assertTrue(info.contains("PUT"));
    }

    public void testLocalStatementVariableDoesNotUseCustomDocumentation() {
        addCareOfferingFixture();

        myFixture.configureByText("facility.kte", """
                @import com.example.Page
                @param page: Page
                !{val careOfferingForm = requireNotNull(page.careOfferingForm)}
                ${care<caret>OfferingForm.displayName}
                """);

        assertNull(quickNavigateInfoAtCaret());
    }

    public void testLocalStatementDeclarationDoesNotUseCustomDocumentation() {
        addNestedObjectAndExtensionFixture();

        myFixture.configureByText("base.kte", """
                @import com.example.navigation.config.PathConfig
                @param isLoggedIn: Boolean
                !{val acc<caret>Link = if (isLoggedIn) PathConfig.BackOffice.ACCESSIBILITY else PathConfig.FrontOffice.ACCESSIBILITY}
                <a href="${accLink}">Accessibility</a>
                """);

        assertNull(quickNavigateInfoAtCaret());
    }

    public void testLocalStatementUsageDoesNotUseCustomDocumentation() {
        addNestedObjectAndExtensionFixture();

        myFixture.configureByText("base.kte", """
                @import com.example.navigation.config.PathConfig
                @param isLoggedIn: Boolean
                !{val accLink = if (isLoggedIn) PathConfig.BackOffice.ACCESSIBILITY else PathConfig.FrontOffice.ACCESSIBILITY}
                <a href="${acc<caret>Link}">Accessibility</a>
                """);

        assertNull(quickNavigateInfoAtCaret());
    }

    public void testForDeclarationDoesNotUseCustomDocumentation() {
        addProfileClassWithKotlinProperties();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profiles: List<Profile>
                @for(it<caret>em in profiles)
                    <li>${item.displayName}</li>
                @else
                    <li>empty</li>
                @endfor
                """);

        assertNull(quickNavigateInfoAtCaret());
    }

    public void testForVariableUsageDoesNotUseCustomDocumentation() {
        addProfileClassWithKotlinProperties();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profiles: List<Profile>
                @for(item in profiles)
                    <li>${it<caret>em.displayName}</li>
                @else
                    <li>empty</li>
                @endfor
                """);

        assertNull(quickNavigateInfoAtCaret());
    }

    public void testGeneratedDocumentationForLocalDeclarationFallsBackToPlatformProvider() {
        addNestedObjectAndExtensionFixture();

        myFixture.configureByText("base.kte", """
                @import com.example.navigation.config.PathConfig
                @param isLoggedIn: Boolean
                !{val acc<caret>Link = if (isLoggedIn) PathConfig.BackOffice.ACCESSIBILITY else PathConfig.FrontOffice.ACCESSIBILITY}
                <a href="${accLink}">Accessibility</a>
                """);

        String documentation = documentationAtCaret();

        assertNull(documentation);
    }

    public void testQuickNavigateInfoForTemplateReferenceShowsChildSignature() {
        addProfileClassWithKotlinProperties();
        addTemplateRoot();
        addSignatureTemplate();

        myFixture.configureByText("parent.kte", """
                @template.components.signatureKitchen<caret>Sink(profile = broken, tags = broken)
                """);

        String info = quickNavigateInfoAtCaret();

        assertNotNull(info);
        assertTrue(info, info.contains("template components.signatureKitchenSink("));
        assertTrue(info, info.contains("profile: Profile"));
        assertTrue(info, info.contains("title: String = \"Profile\""));
        assertTrue(info, info.contains("tags: List<String>"));
        assertTrue(info, info.contains("content: gg.jte.Content"));
        assertTrue(info, info.contains("vararg labels: String"));
    }

    public void testGeneratedDocumentationForTemplateReferenceShowsChildSignature() {
        addProfileClassWithKotlinProperties();
        addTemplateRoot();
        addSignatureTemplate();

        myFixture.configureByText("parent.kte", """
                @template.components.signatureKitchen<caret>Sink(profile = broken, tags = broken)
                """);

        String documentation = documentationAtCaret();
        String documentationText = plainDocumentationText(documentation);

        assertNotNull(documentation);
        assertTrue(documentation, documentation.contains("<span style=\""));
        assertTrue(documentation, documentationText.contains("template components.signatureKitchenSink("));
        assertTrue(documentation, documentationText.contains("profile: Profile"));
        assertTrue(documentation, documentationText.contains("vararg labels: String"));
    }

    public void testTemplateCallDocumentationExcludesNestedTemplateNames() {
        addTemplateRoot();
        myFixture.addFileToProject("ui/modal/modal_shell.kte", """
                @param id: String
                @param body: gg.jte.Content? = null
                """);
        myFixture.addFileToProject("form/fields/text_input.kte", "@param name: String\n");
        myFixture.configureByText("parent.kte", """
                @template.ui.modal.modal_<caret>shell(
                    id = "modal",
                    body = @`
                        @template.form.fields.text_input(name = "field")
                    `
                )
                """);

        String info = quickNavigateInfoAtCaret();
        String documentation = documentationAtCaret();
        assertNotNull(info);
        assertNotNull(documentation);
        assertTrue(info, info.startsWith("template ui.modal.modal_shell("));
        assertFalse(info, info.contains("form.fields.text_input"));
        assertFalse(documentation, plainDocumentationText(documentation).contains("form.fields.text_input"));
        PsiElement original = elementAtCaret();
        PsiElement target = DocumentationManager.getInstance(getProject())
                .findTargetElement(myFixture.getEditor(), myFixture.getFile(), original);
        assertNotNull(target);
        assertTrue(target.toString(), target instanceof JtePsiTemplateName);
        var managerProvider = DocumentationManager.getProviderFromElement(target);
        assertEquals(info, managerProvider.getQuickNavigateInfo(target, original));
        String managedDocumentation = managerProvider.generateDoc(target, original);
        assertNotNull(managedDocumentation);
        assertTrue(managedDocumentation, plainDocumentationText(managedDocumentation)
                .contains("template ui.modal.modal_shell("));
    }

    public void testUnresolvedTemplateReferenceDoesNotShowMisleadingSignature() {
        addTemplateRoot();

        myFixture.configureByText("parent.kte", """
                @template.components.miss<caret>ing(profile = broken)
                """);

        assertNull(quickNavigateInfoAtCaret());
    }

    private String quickNavigateInfoAtCaret() {
        KteKotlinDocumentationProvider provider = new KteKotlinDocumentationProvider();
        PsiElement element = elementAtCaret();
        PsiElement documentationElement = documentationElementAtCaret(provider, element);
        return documentationElement == null ? null : provider.getQuickNavigateInfo(documentationElement, element);
    }

    private String platformDocumentationHintAtCaret() {
        KteKotlinDocumentationProvider provider = new KteKotlinDocumentationProvider();
        PsiElement element = elementAtCaret();
        PsiElement documentationElement = documentationElementAtCaret(provider, element);
        assertNotNull(documentationElement);
        for (PsiDocumentationTargetProvider targetProvider : PsiDocumentationTargetProvider.EP_NAME.getExtensionList()) {
            for (DocumentationTarget target : targetProvider.documentationTargets(documentationElement, element)) {
                String hint = target.computeDocumentationHint();
                if (hint != null) {
                    return hint;
                }

                DocumentationResult documentation = target.computeDocumentation();
                if (documentation instanceof DocumentationData data) {
                    return data.getHtml();
                }
            }
        }
        return null;
    }

    private String documentationAtCaret() {
        KteKotlinDocumentationProvider provider = new KteKotlinDocumentationProvider();
        PsiElement element = elementAtCaret();
        PsiElement documentationElement = documentationElementAtCaret(provider, element);
        return documentationElement == null ? null : provider.generateDoc(documentationElement, element);
    }

    private String plainDocumentationText(String documentation) {
        return StringUtil.unescapeXmlEntities(documentation
                .replace("<br>", "\n")
                .replace("&#32;", " ")
                .replaceAll("<[^>]+>", ""));
    }

    private PsiElement elementAtCaret() {
        PsiElement element = myFixture.getFile().findElementAt(Math.max(0, myFixture.getCaretOffset() - 1));
        assertNotNull(element);
        return element;
    }

    private PsiElement documentationElementAtCaret(KteKotlinDocumentationProvider provider, PsiElement element) {
        return provider.getCustomDocumentationElement(
                myFixture.getEditor(),
                myFixture.getFile(),
                element,
                Math.max(0, myFixture.getCaretOffset() - 1)
        );
    }
}
