package org.jusecase.jte.intellij.language.k2;

import java.util.Set;

public class KteTemplateSignatureCompletionTest extends KteK2FixtureSupport {
    public void testKteTemplateParamNameCompletionUsesKteSignature() {
        addTemplateRoot();
        addSignatureTemplate();

        myFixture.configureByText("caller.kte", """
                @import com.example.Profile
                @param profile: Profile
                @template.components.signatureKitchenSink(profile = profile, <caret>)
                """);

        Set<String> lookupStrings = completeBasicLookupStrings();

        assertDoesNotContainLookup(lookupStrings, "profile =");
        assertContainsLookup(lookupStrings, "title =");
        assertContainsLookup(lookupStrings, "tags =");
        assertContainsLookup(lookupStrings, "content =");
        assertContainsLookup(lookupStrings, "labels =");
    }

    public void testKteTemplateNameCompletionInsertsRequiredKteParams() {
        addTemplateRoot();
        addSignatureTemplate();

        myFixture.configureByText("caller.kte", """
                @template.components.<caret>
                """);

        chooseCompletion("signatureKitchenSink");

        assertEquals("""
                @template.components.signatureKitchenSink(profile = a, tags = a, content = a)
                """, myFixture.getFile().getText());
    }

}
