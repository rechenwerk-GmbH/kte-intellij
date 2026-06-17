package org.jusecase.jte.intellij.language.k2;

import com.intellij.codeInsight.lookup.LookupElement;

import java.util.Set;

public class KteCompletionGeneratedSymbolFilteringTest extends KteK2FixtureSupport {
    public void testDoesNotCompleteGeneratedWrapperSymbols() {
        myFixture.configureByText("wrapper.kte", """
                ${jt<caret>}
                """);

        LookupElement[] elements = myFixture.completeBasic();
        if (elements == null) {
            assertDoesNotContainGeneratedWrapperSymbols(topLevelFileText());
            return;
        }

        Set<String> lookupStrings = lookupStrings(elements);
        assertDoesNotContainLookup(lookupStrings, "jteOutput");
        assertDoesNotContainLookup(lookupStrings, "dummyCall");
        assertDoesNotContainLookup(lookupStrings, "DummyTemplate");
    }

    private void assertDoesNotContainGeneratedWrapperSymbols(String text) {
        assertFalse(text.contains("jteOutput"));
        assertFalse(text.contains("dummyCall"));
        assertFalse(text.contains("DummyTemplate"));
    }
}
