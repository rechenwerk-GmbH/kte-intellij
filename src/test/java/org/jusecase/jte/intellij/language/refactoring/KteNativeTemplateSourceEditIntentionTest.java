package org.jusecase.jte.intellij.language.refactoring;

import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

import com.intellij.codeInsight.intention.IntentionAction;

import java.util.List;

public class KteNativeTemplateSourceEditIntentionTest extends KtePluginFixtureSupport {
    public void testOptimizeImportsSortsAndDeduplicatesTopLevelImports() {
        addImportTargets();

        myFixture.configureByText("imports.kte", """
                @import com.example.Zed
                @import com.example.Alpha
                @import com.example.Zed

                @param title: String
                ${title}
                """);

        myFixture.launchAction(singleIntention("Optimize .kte imports"));

        assertEquals("""
                @import com.example.Alpha
                @import com.example.Zed

                @param title: String
                ${title}
                """, myFixture.getFile().getText());
    }

    public void testOptimizeImportsUnavailableForSeparatedImportBlocks() {
        addImportTargets();

        myFixture.configureByText("imports.kte", """
                @import com.example.Alpha

                @param title: String
                @import com.example.Zed
                ${title}
                """);

        List<IntentionAction> actions = myFixture.filterAvailableIntentions("Optimize .kte imports");

        assertTrue(actions.toString(), actions.isEmpty());
    }

    public void testInsertMissingAssignmentQuickFixAddsAssignmentAndMovesCaret() {
        addProfileClass();
        addTemplateRoot();
        addCardTemplate();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                @param title: String
                @template.components.card(profile = profile, <caret>title =, content = @`
                    ${profile.displayName}
                `)
                """);
        errorDescriptions();

        myFixture.launchAction(singleIntention("Insert missing parameter assignment"));

        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("title = , content"));
        assertEquals(myFixture.getFile().getText().indexOf("title = ") + "title = ".length(),
                myFixture.getEditor().getCaretModel().getOffset());
    }

    public void testRemoveDuplicateTemplateParameterQuickFixRemovesLaterArgument() {
        addProfileClass();
        addTemplateRoot();
        addCardTemplate();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                @param title: String
                @template.components.card(profile = profile, title = title, <caret>title = profile.displayName, content = @`
                    ${profile.displayName}
                `)
                """);
        errorDescriptions();

        myFixture.launchAction(singleIntention("Remove duplicate template parameter"));

        assertFalse(myFixture.getFile().getText(), myFixture.getFile().getText().contains("title = profile.displayName"));
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("profile = profile, title = title, content = @`"));
    }

    public void testAddMissingTemplateParametersQuickFixAppendsPlaceholdersAndMovesCaret() {
        addProfileClass();
        addTemplateRoot();
        addManualCardTemplate();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                @template.components.card(<caret>)
                """);
        errorDescriptions();

        myFixture.launchAction(singleIntention("Add missing template parameters"));

        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText()
                .contains("@template.components.card(profile = , tags = )"));
        assertEquals(myFixture.getFile().getText().indexOf("profile = ") + "profile = ".length(),
                myFixture.getEditor().getCaretModel().getOffset());
    }

    private void addImportTargets() {
        myFixture.addFileToProject("src/com/example/Alpha.kt", """
                package com.example

                class Alpha
                """);
        myFixture.addFileToProject("src/com/example/Zed.kt", """
                package com.example

                class Zed
                """);
    }

    private IntentionAction singleIntention(String text) {
        List<IntentionAction> actions = myFixture.filterAvailableIntentions(text);
        assertEquals(actions.toString(), 1, actions.size());
        return actions.get(0);
    }
}
