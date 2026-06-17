package org.jusecase.jte.intellij.language.k2;

import java.util.List;

public class KteTemplateDiagnosticAnnotatorTest extends KteK2FixtureSupport {
    public void testValidProfileFixtureTemplateCallsDoNotReportNativeContractDiagnostics() {
        addProfileClassWithKotlinProperties();
        addTemplateRoot();
        addLayoutTemplate();
        addHeaderTemplate();
        addManualCardTemplate();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                @param profiles: List<Profile>
                @param title: String = "K2 profile"
                @template.layout(title = title, content = @`
                    <section>
                        @template.components.header(title = title)
                        @template.components.card(profile = profile, tags = profile.tags)
                    </section>
                `)
                """);

        assertNoContractErrors();
    }

    public void testReportsNativeTemplateCallContractErrors() {
        addProfileClass();
        addTemplateRoot();
        addCardTemplate();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                @param title: String
                @template.components.card(profile = profile, title = title, title = profile, content = @`
                    ${profile}
                `)
                """);

        List<String> descriptions = errorDescriptions();

        assertTrue(descriptions.toString(), descriptions.stream()
                .anyMatch(description -> description.startsWith("Duplicate parameter title")));
    }
}
