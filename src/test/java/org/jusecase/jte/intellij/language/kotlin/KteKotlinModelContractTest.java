package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

import java.util.Set;

/** The full Kotlin model and completion fragment must agree about source declarations. */
public class KteKotlinModelContractTest extends KtePluginFixtureSupport {
    public void testImportedAliasAndPreviousLocalWorkInBothModels() {
        addProfileClassWithKotlinProperties();
        String source = """
                @import com.example.Profile as Person
                @param profile: Person
                !{val selected = profile}
                ${selected.displayName}
                ${selected.dis<caret>}
                """;
        myFixture.configureByText("alias-local.kte", source);

        assertNoErrorAt("selected.displayName");
        assertCompletionContains("displayName");
    }

    public void testNestedImportedTypeWorksInBothModels() {
        myFixture.addFileToProject("src/com/example/Outer.kt", """
                package com.example
                class Outer { data class Inner(val label: String) }
                """);
        myFixture.configureByText("nested-type.kte", """
                @import com.example.Outer
                @param item: Outer.Inner
                ${item.label}
                ${item.la<caret>}
                """);

        assertNoErrorAt("item.label");
        assertCompletionContains("label");
    }

    private void assertNoErrorAt(String text) {
        String source = myFixture.getFile().getText();
        int start = source.indexOf(text);
        assertTrue(start >= 0);
        TextRange range = TextRange.from(start, text.length());
        KteKotlinFileAnalysis.Result result = new KteKotlinFileAnalysis().collect(myFixture.getFile());
        assertFalse(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.severity() == HighlightSeverity.ERROR && diagnostic.range().intersects(range)));
    }

    private void assertCompletionContains(String name) {
        LookupElement[] elements = myFixture.completeBasic();
        if (elements == null) {
            String completed = myFixture.getFile().getText();
            assertTrue(completed, completed.indexOf(name) != completed.lastIndexOf(name));
            return;
        }
        Set<String> names = lookupStrings(elements);
        assertTrue(names.toString(), names.contains(name));
    }
}
