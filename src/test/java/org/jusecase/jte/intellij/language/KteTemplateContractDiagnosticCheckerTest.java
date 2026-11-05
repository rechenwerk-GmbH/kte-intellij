package org.jusecase.jte.intellij.language;

import com.intellij.psi.util.PsiTreeUtil;
import org.jusecase.jte.intellij.language.psi.JtePsiTemplate;
import org.jusecase.jte.intellij.language.psi.KtePsiParamName;
import org.jusecase.jte.intellij.language.template.KteTemplateCallArguments;

import java.util.Arrays;
import java.util.List;

public class KteTemplateContractDiagnosticCheckerTest extends KtePluginFixtureSupport {
    public void testValidNamedTemplateCallIsStructurallyClean() {
        addProfileClass();
        addTemplateRoot();
        addCardTemplate();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                @param title: String
                @template.components.card(profile = profile, title = title, content = @`
                    ${profile.displayName}
                `)
                """);

        assertTrue(contractDescriptions().toString(), contractDescriptions().isEmpty());
    }

    public void testReportsMissingParameterAssignment() {
        addProfileClass();
        addTemplateRoot();
        addCardTemplate();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                @template.components.card(profile = profile, title = , content = @`
                    ${profile.displayName}
                `)
                """);

        assertContainsDescription(contractDescriptions(), "Missing parameter assignment");
    }

    public void testOnlyDeterministicNativeContractProblemsAreReportedByStructuralChecker() {
        addProfileClassWithKotlinProperties();
        addTemplateRoot();
        addManualCardTemplate();
        addTagListTemplate();

        myFixture.configureByText("template-positionals.kte", """
                @import com.example.Profile
                @param profile: Profile
                @param title: String
                @template.components.card(title = title, title = profile.displayName, unknown = title)
                @template.components.card(title, profile.tags)
                @template.components.card(profile)
                @template.components.tagList(profile.tags, profile.tags)
                """);

        List<String> descriptions = contractDescriptions();

        assertContainsDescription(descriptions, "Duplicate parameter title");
        assertTrue(descriptions.toString(), descriptions.stream()
                .anyMatch(description -> description.startsWith("Missing required parameters:")));
        assertFalse(descriptions.toString(), descriptions.stream().anyMatch(description ->
                description.contains("No value passed for parameter") ||
                        description.contains("No parameter with name") ||
                        description.contains("Too many arguments") ||
                        description.contains("Argument type mismatch")));
    }

    public void testNestedExpressionsAndInlineContentDoNotCreateContractProblems() {
        addTemplateRoot();
        myFixture.addFileToProject("components/card.kte", """
                @param title: String
                @param disabled: Boolean
                @param content: gg.jte.Content
                """);
        myFixture.configureByText("expression-arguments.kte", """
                @param selected: String?
                @param count: Int
                @template.components.card(
                    title = selected ?: listOf("fallback").first(),
                    disabled = count >= 2 || count == 0,
                    content = @`
                        ${selected}
                    `,
                )
                """);

        JtePsiTemplate template = PsiTreeUtil.findChildOfType(myFixture.getFile(), JtePsiTemplate.class);
        assertNotNull(template);
        List<String> argumentNames = Arrays.stream(template.getChildren())
                .filter(KtePsiParamName.class::isInstance)
                .map(element -> ((KtePsiParamName) element).getName())
                .toList();
        assertEquals(List.of("title", "disabled", "content"), argumentNames);
        assertTrue(contractDescriptions().toString(), contractDescriptions().isEmpty());
    }

    public void testLambdaParameterCommaStaysInsideTemplateArgument() {
        assertExpressionArgument("{ left: String, right: String -> left + right }");
    }

    public void testGenericTypeArgumentCommaStaysInsideTemplateArgument() {
        assertExpressionArgument("mapOf<String, Int>(\"a\" to 1)");
    }

    public void testCharLiteralCommaStaysInsideTemplateArgument() {
        assertExpressionArgument("','");
    }

    public void testRawStringCommaStaysInsideTemplateArgument() {
        assertExpressionArgument("\"\"\"a\"b,c\"\"\"");
    }

    private void assertExpressionArgument(String expression) {
        addTemplateRoot();
        myFixture.addFileToProject("components/card.kte", "@param value: Any?\n@param disabled: Boolean\n");
        myFixture.configureByText("edge.kte", "@template.components.card(value = " + expression + ", disabled = true)\n");

        JtePsiTemplate template = PsiTreeUtil.findChildOfType(myFixture.getFile(), JtePsiTemplate.class);
        assertNotNull(template);
        var arguments = KteTemplateCallArguments.parse(template);
        assertEquals(expression + ": " + arguments, 2, arguments.size());
        assertEquals("value", arguments.getFirst().name());
        assertEquals(expression, arguments.getFirst().valueText());
        assertEquals("disabled", arguments.get(1).name());
        assertEquals("true", arguments.get(1).valueText());
        assertTrue(contractDescriptions().toString(), contractDescriptions().isEmpty());
    }

    private List<String> contractDescriptions() {
        return new KteTemplateContractDiagnosticChecker().collect(myFixture.getFile())
                .stream()
                .map(KteTemplateDiagnostic::message)
                .toList();
    }
}
