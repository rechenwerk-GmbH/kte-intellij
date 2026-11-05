package org.jusecase.jte.intellij.language;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import org.jetbrains.kotlin.psi.KtClass;
import org.jusecase.jte.intellij.language.template.KteTemplateSignatureService;

public class KteKotlinReferenceRefactoringTest extends KtePluginFixtureSupport {
    public void testRenameTemplateParameterUpdatesUsagesInSameFile() {
        var file = myFixture.configureByText("simple.kte", """
                @param showBreadcrumb: Boolean
                @if(showBreadcrumb)
                    ${showBreadcrumb}
                @endif
                """);
        var parameter = KteTemplateSignatureService.resolve(file).parameter("showBreadcrumb");
        assertNotNull(parameter);

        new RenameProcessor(getProject(), parameter.sourceElement(), "showCrumb", false, false).run();

        assertEquals("""
                @param showCrumb: Boolean
                @if(showCrumb)
                    ${showCrumb}
                @endif
                """, file.getText());
    }

    public void testHandleRenameUpdatesOutputPropertyReference() {
        myFixture.addFileToProject("src/com/example/Profile.kt", """
                package com.example

                data class Profile(val displayName: String)
                """);

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${profile.display<caret>Name}
                """);

        renameReferenceAtCaret("fullName");

        assertEquals("""
                @import com.example.Profile
                @param profile: Profile
                ${profile.fullName}
                """, myFixture.getFile().getText());
    }

    public void testHandleRenameUpdatesParamTypeReference() {
        myFixture.addFileToProject("src/com/example/Profile.kt", """
                package com.example

                data class Profile(val displayName: String)
                """);

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profile: Pro<caret>file
                ${profile.displayName}
                """);

        renameReferenceAtCaret("Account");

        assertEquals("""
                @import com.example.Profile
                @param profile: Account
                ${profile.displayName}
                """, myFixture.getFile().getText());
    }

    public void testHandleRenameUpdatesGenericParamTypeReference() {
        myFixture.addFileToProject("src/com/example/Profile.kt", """
                package com.example

                data class Profile(val displayName: String)
                """);

        myFixture.configureByText("profile.kte", """
                @import com.example.Profile
                @param profiles: List<Pro<caret>file>
                ${profiles}
                """);

        renameReferenceAtCaret("Account");

        assertEquals("""
                @import com.example.Profile
                @param profiles: List<Account>
                ${profiles}
                """, myFixture.getFile().getText());
    }

    public void testBindToElementUpdatesImportReferenceToQualifiedKotlinClassName() {
        PsiElement accountFile = myFixture.addFileToProject("src/com/other/Account.kt", """
                package com.other

                data class Account(val displayName: String)
                """);
        KtClass accountClass = PsiTreeUtil.findChildOfType(accountFile, KtClass.class);
        assertNotNull(accountClass);

        myFixture.addFileToProject("src/com/example/Profile.kt", """
                package com.example

                data class Profile(val displayName: String)
                """);

        myFixture.configureByText("profile.kte", """
                @import com.example.Pro<caret>file
                @param profile: Profile
                ${profile.displayName}
                """);

        bindReferenceAtCaret(accountClass);

        assertEquals("""
                @import com.other.Account
                @param profile: Profile
                ${profile.displayName}
                """, myFixture.getFile().getText());
    }

    private void renameReferenceAtCaret(String newName) {
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () -> referenceAtCaret().handleElementRename(newName));
    }

    private void bindReferenceAtCaret(PsiElement element) {
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () -> referenceAtCaret().bindToElement(element));
    }
}
