package org.jusecase.jte.intellij.language.k2;

public class KteNativeKotlinCompletionInsertionTest extends KteK2FixtureSupport {
    public void testNativeCompletionReplaysImportedTopLevelFunctionCallFromSourceExpression() {
        addNoArgTopLevelCompletionFunctions();
        addJteRuntimeStubs();

        myFixture.configureByText("helpers.kte", """
                @import com.example.i18n
                ${i18<caret>}
                """);

        chooseCompletion("i18n");

        assertTopLevelFileEquals("""
                @import com.example.i18n
                ${i18n()}
                """);
    }

    public void testNativeCompletionReplaysAutoImportForTopLevelFunctionFromSourceExpression() {
        addNoArgTopLevelCompletionFunctions();
        addJteRuntimeStubs();

        myFixture.configureByText("helpers.kte", """
                ${i18<caret>}
                """);

        chooseCompletion("i18n");

        assertTopLevelFileEquals("""
                @import com.example.i18n
                ${i18n()}
                """);
    }

    public void testNativeCompletionReplaysImportedKotlinPropertyFromSourceExpression() {
        addProfileClassWithKotlinProperties();
        addJteRuntimeStubs();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${profile.dis<caret>}
                """);

        chooseCompletion("displayName");

        assertTopLevelFileEquals("""
                @import com.example.Profile
                @param profile: Profile
                ${profile.displayName}
                """);
    }

    public void testNativeCompletionReplaysTemplateParamFromSourceExpression() {
        addJteRuntimeStubs();

        myFixture.configureByText("profile.kte", """
                @param profile: String
                @param project: String
                ${prof<caret>}
                """);

        chooseCompletion("profile");

        assertTopLevelFileEquals("""
                @param profile: String
                @param project: String
                ${profile}
                """);
    }

    public void testNativeCompletionReplaysStatementLocalAfterDeclaration() {
        addCareOfferingFixture();
        addJteRuntimeStubs();

        myFixture.configureByText("facility.kte", """
                @import com.example.Page
                @param page: Page
                !{val careOfferingForm = requireNotNull(page.careOfferingForm)}
                ${care<caret>}
                """);

        chooseCompletion("careOfferingForm");

        assertTopLevelFileEquals("""
                @import com.example.Page
                @param page: Page
                !{val careOfferingForm = requireNotNull(page.careOfferingForm)}
                ${careOfferingForm}
                """);
    }

    public void testNativeCompletionReplaysSafeCallMemberFromSourceExpression() {
        addProfileClassWithKotlinProperties();
        addJteRuntimeStubs();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile?
                ${profile?.dis<caret>}
                """);

        chooseCompletion("displayName");

        assertTopLevelFileEquals("""
                @import com.example.Profile
                @param profile: Profile?
                ${profile?.displayName}
                """);
    }

    public void testNativeCompletionReplaysImportedExtensionFunctionAfterReceiverDot() {
        addNestedObjectAndExtensionFixture();
        addJteRuntimeStubs();

        myFixture.configureByText("base.kte", """
                @import com.example.navigation.breadcrumb.Breadcrumb
                @import com.example.navigation.routing.RoutingUtils.isCurrentPage
                @param breadcrumbs: List<Breadcrumb>?
                @if(breadcrumbs.is<caret>)
                    ${breadcrumbs}
                @endif
                """);

        chooseCompletion("isCurrentPage");

        assertTopLevelFileContains("breadcrumbs.isCurrentPage");
    }

    public void testNativeCompletionReplaysImportedTypeInParamType() {
        addProfileClass();
        addJteRuntimeStubs();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Pro<caret>
                ${profile}
                """);

        chooseCompletion("Profile");

        assertTopLevelFileEquals("""
                @import com.example.Profile
                @param profile: Profile
                ${profile}
                """);
    }

    public void testNativeCompletionReplaysEnumEntryInTemplateArgument() {
        addSupportHelpers();
        addJteRuntimeStubs();

        myFixture.configureByText("form.kte", """
                @import com.example.HiddenHttpMethod
                @template.form.hidden_http_method(method = HiddenHttpMethod.P<caret>)
                """);

        chooseCompletion("PUT");

        assertTopLevelFileEquals("""
                @import com.example.HiddenHttpMethod
                @template.form.hidden_http_method(method = HiddenHttpMethod.PUT)
                """);
    }

    public void testNativeCompletionReplaysStarImportedTopLevelFunctionCall() {
        addNoArgTopLevelCompletionFunctions();
        addJteRuntimeStubs();

        myFixture.configureByText("helpers.kte", """
                @import com.example.*
                ${i18<caret>}
                """);

        chooseCompletion("i18n");

        assertTopLevelFileEquals("""
                @import com.example.*
                ${i18n()}
                """);
    }

    public void testNativeCompletionReplaysCompanionMember() {
        myFixture.addFileToProject("src/com/example/Profile.kt", """
                package com.example

                class Profile(val displayName: String) {
                    companion object {
                        val DEFAULT = Profile("Default")
                    }
                }
                """);
        addJteRuntimeStubs();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                ${Profile.DEF<caret>}
                """);

        chooseCompletion("DEFAULT");

        assertTopLevelFileEquals("""
                @import com.example.Profile
                ${Profile.DEFAULT}
                """);
    }

    public void testNativeCompletionReplaysNestedObjectMember() {
        addNestedObjectAndExtensionFixture();
        addJteRuntimeStubs();

        myFixture.configureByText("base.kte", """
                @import com.example.navigation.config.PathConfig
                ${PathConfig.Front<caret>}
                """);

        chooseCompletion("FrontOffice");

        assertTopLevelFileEquals("""
                @import com.example.navigation.config.PathConfig
                ${PathConfig.FrontOffice}
                """);
    }

    public void testNativeCompletionReplaysCallReceiverMember() {
        addProfileClassWithKotlinProperties();
        addJteRuntimeStubs();

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${requireNotNull(profile.manager).dis<caret>}
                """);

        chooseCompletion("displayName");

        assertTopLevelFileEquals("""
                @import com.example.Profile
                @param profile: Profile
                ${requireNotNull(profile.manager).displayName}
                """);
    }

    public void testNativeCompletionReplaysBuiltinTypeInParamType() {
        addJteRuntimeStubs();

        myFixture.configureByText("profile.kte", """
                @param title: Str<caret>
                ${title}
                """);

        chooseCompletion("String");

        assertTopLevelFileEquals("""
                @param title: String
                ${title}
                """);
    }

    public void testNativeCompletionReplaysAutoImportForParamType() {
        addProfileClass();
        addJteRuntimeStubs();

        myFixture.configureByText("profile.kte", """
                @param profile: Pro<caret>
                ${profile}
                """);

        chooseCompletion("Profile");

        assertTopLevelFileEquals("""
                @import com.example.Profile
                @param profile: Profile
                ${profile}
                """);
    }

    public void testNativeCompletionReplaysClassLookupInExpression() {
        myFixture.addFileToProject("src/com/example/Widget.kt", """
                package com.example

                class Widget
                """);
        addJteRuntimeStubs();

        myFixture.configureByText("widget.kte", """
                @import com.example.Widget
                ${Wid<caret>}
                """);

        chooseCompletion("Widget");

        assertTopLevelFileEquals("""
                @import com.example.Widget
                ${Widget}
                """);
    }

    private void addNoArgTopLevelCompletionFunctions() {
        myFixture.addFileToProject("src/com/example/TopLevelFunctions.kt", """
                package com.example

                fun i18n(): String = ""

                fun icon(): String = ""
                """);
    }

    private void assertTopLevelFileEquals(String expected) {
        assertEquals(
                expected,
                topLevelFileText()
        );
    }

    private void assertTopLevelFileContains(String expected) {
        String text = topLevelFileText();
        assertTrue(
                "Expected top-level file to contain '" + expected + "':\n" + text,
                text.contains(expected)
        );
    }
}
