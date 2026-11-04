package org.jusecase.jte.intellij.language;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;

import java.util.Arrays;
import java.util.List;

public class KteDiagnosticAuditInspectionTest extends KtePluginFixtureSupport {
    public void testAuditReportsTheProblemsShownByEditorAnnotators() {
        addTemplateRoot();
        myFixture.addFileToProject("components/card.kte", "@param title: String\n");
        String source = """
                @template.components.card()
                @if(missing)
                """;
        myFixture.configureByText("audit.kte", source);

        List<String> editor = errorDescriptions();
        ProblemDescriptor[] exported = new KteDiagnosticAuditInspection().checkFile(
                myFixture.getFile(), InspectionManager.getInstance(getProject()), false);
        List<String> descriptions = Arrays.stream(exported)
                .map(ProblemDescriptor::getDescriptionTemplate)
                .toList();

        assertTrue(editor.toString(), editor.stream().anyMatch(message -> message.startsWith("Missing required parameters:")));
        assertTrue(descriptions.toString(), descriptions.stream().anyMatch(message ->
                message.startsWith("[KTE/CONTRACT/MISSING_REQUIRED_ARGUMENT] Missing required parameters:")));
        assertTrue(editor.toString(), editor.stream().anyMatch(message -> message.contains("Unresolved reference")));
        assertTrue(descriptions.toString(), descriptions.stream().anyMatch(message ->
                message.startsWith("[KTE/KOTLIN/UNRESOLVED_REFERENCE]") && message.contains("Unresolved reference")));
        assertTrue(editor.toString(), editor.stream().anyMatch(message -> message.equals("Missing @endif")));
        assertTrue(descriptions.toString(), descriptions.stream().anyMatch(message ->
                message.equals("[KTE/STRUCTURE/MISSING_ENDIF] Missing @endif")));
        assertTrue(descriptions.toString(), Arrays.stream(exported).allMatch(descriptor ->
                descriptor.getTextRangeInElement().getStartOffset() >= 0 &&
                        descriptor.getTextRangeInElement().getEndOffset() <= source.length()));
    }
}
