package org.jusecase.jte.intellij.language.template;

import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;

public class KteTemplateSignatureServiceTest extends KtePluginFixtureSupport {
    public void testResolvesRequiredDefaultsAndSourceTypes() {
        addProfileClass();

        myFixture.configureByText("card.kte", """
                @import com.example.Profile
                @param profile: Profile
                @param title: String = "Profile"
                @param subtitle: String? = null
                @param tags: List<String>
                @param grouped: Map<String, List<Profile?>>
                @param content: gg.jte.Content
                @param vararg labels: String
                """);

        KteTemplateSignatureService.TemplateSignature signature =
                KteTemplateSignatureService.resolve(myFixture.getFile());

        assertEquals(7, signature.parameters().size());
        assertTrue(signature.parameter("profile").required());
        assertFalse(signature.parameter("profile").defaulted());
        assertEquals("Profile", signature.parameter("profile").rawType());
        assertFalse(signature.parameter("title").required());
        assertTrue(signature.parameter("title").defaulted());
        assertFalse(signature.parameter("subtitle").required());
        assertEquals("String?", signature.parameter("subtitle").typeText());
        assertEquals("List<String>", signature.parameter("tags").typeText());
        assertEquals("List", signature.parameter("tags").rawType());
        assertEquals("Map<String, List<Profile?>>", signature.parameter("grouped").typeText());
        assertEquals("gg.jte.Content", signature.parameter("content").rawType());
        assertTrue(signature.parameter("labels").vararg());
        assertFalse(signature.parameter("labels").required());
        assertFalse(signature.requiredParameters().contains(signature.parameter("labels")));
    }

    public void testKeepsSourceTypeTextWhenParamTypeCannotBeAnalyzed() {
        myFixture.configureByText("broken.kte", """
                @param profile: MissingProfile
                """);

        KteTemplateSignatureService.TemplateSignature signature =
                KteTemplateSignatureService.resolve(myFixture.getFile());

        KteTemplateSignatureService.Parameter profile = signature.parameter("profile");

        assertEquals("MissingProfile", profile.typeText());
        assertEquals("MissingProfile", profile.rawType());
    }

    public void testSignatureUpdatesAfterEditingTemplate() {
        myFixture.configureByText("card.kte", "@param title: String\n");
        assertEquals("String", KteTemplateSignatureService.resolve(myFixture.getFile()).parameter("title").typeText());

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            var document = myFixture.getEditor().getDocument();
            document.setText("@param title: Int\n");
            PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        });

        assertEquals("Int", KteTemplateSignatureService.resolve(myFixture.getFile()).parameter("title").typeText());
    }

    public void testExposesParamSourceRanges() {
        addProfileClass();

        myFixture.configureByText("card.kte", """
                @import com.example.Profile
                @param profile: Profile
                @param title: String = "Profile"
                """);

        KteTemplateSignatureService.TemplateSignature signature =
                KteTemplateSignatureService.resolve(myFixture.getFile());
        KteTemplateSignatureService.Parameter profile = signature.parameter("profile");
        KteTemplateSignatureService.Parameter title = signature.parameter("title");

        assertEquals("profile: Profile", text(profile.declarationRange()));
        assertEquals("profile", text(profile.nameRange()));
        assertEquals("\"Profile\"", text(title.defaultValueRange()));
    }

    public void testReturnsEmptySignatureForNonKteTemplate() {
        myFixture.configureByText("card.txt", """
                @param String title
                """);

        KteTemplateSignatureService.TemplateSignature signature =
                KteTemplateSignatureService.resolve(myFixture.getFile());

        assertTrue(signature.parameters().isEmpty());
        assertFalse(KteTemplateSignatureService.isKteTemplate(myFixture.getFile()));
    }

    private String text(TextRange range) {
        assertNotNull(range);
        return myFixture.getFile().getText().substring(range.getStartOffset(), range.getEndOffset());
    }
}
