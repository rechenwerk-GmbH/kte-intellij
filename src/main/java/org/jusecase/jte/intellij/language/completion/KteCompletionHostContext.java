package org.jusecase.jte.intellij.language.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.KteLanguage;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;
import org.jusecase.jte.intellij.language.psi.KtePsiFile;

record KteCompletionHostContext(@NotNull PsiFile kteFile, int hostOffset, @NotNull PsiElement hostElement) {
    @Nullable
    static KteCompletionHostContext fromInjectedKotlinCompletion(@NotNull CompletionParameters parameters) {
        PsiFile completionFile = parameters.getPosition().getContainingFile();
        InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(completionFile.getProject());
        PsiFile topLevelFile = injectedLanguageManager.getTopLevelFile(completionFile);
        PsiFile kteFile = findKteFile(topLevelFile);
        if (kteFile == null) {
            return null;
        }

        int hostOffset = hostOffset(parameters, completionFile);
        PsiElement hostElement = hostElement(kteFile, hostOffset);
        if (hostElement == null || !isInsideKotlinInjection(hostElement)) {
            return null;
        }

        return new KteCompletionHostContext(kteFile, hostOffset, hostElement);
    }

    @Nullable
    static PsiFile findKteFile(@NotNull PsiFile topLevelFile) {
        if (topLevelFile instanceof KtePsiFile) {
            return topLevelFile;
        }

        PsiFile ktePsi = topLevelFile.getViewProvider().getPsi(KteLanguage.INSTANCE);
        return ktePsi instanceof KtePsiFile ? ktePsi : null;
    }

    private static int hostOffset(@NotNull CompletionParameters parameters, @NotNull PsiFile completionFile) {
        Document editorDocument = parameters.getEditor().getDocument();
        if (editorDocument instanceof DocumentWindow documentWindow) {
            return documentWindow.injectedToHost(parameters.getOffset());
        }

        Document fileDocument = completionFile.getViewProvider().getDocument();
        if (fileDocument instanceof DocumentWindow documentWindow) {
            return documentWindow.injectedToHost(parameters.getOffset());
        }

        return parameters.getOffset();
    }

    private static boolean isInsideKotlinInjection(@NotNull PsiElement element) {
        if (element instanceof JtePsiJavaInjection) {
            return true;
        }
        return PsiTreeUtil.getParentOfType(element, JtePsiJavaInjection.class, false) != null;
    }

    @Nullable
    private static PsiElement hostElement(@NotNull PsiFile kteFile, int hostOffset) {
        int offset = Math.clamp(hostOffset, 0, Math.max(0, kteFile.getTextLength() - 1));
        PsiElement element = kteFile.findElementAt(offset);
        if (element == null && offset > 0) {
            element = kteFile.findElementAt(offset - 1);
        }
        if (element != null && !isInsideKotlinInjection(element)) {
            element = containingInjection(element, hostOffset);
        }
        return element;
    }

    @Nullable
    private static JtePsiJavaInjection containingInjection(@NotNull PsiElement element, int hostOffset) {
        KtePsiJavaContent host = PsiTreeUtil.getParentOfType(element, KtePsiJavaContent.class, false);
        if (host == null && element instanceof KtePsiJavaContent javaContent) {
            host = javaContent;
        }
        if (host == null) {
            return null;
        }

        for (JtePsiJavaInjection injection : PsiTreeUtil.findChildrenOfType(host, JtePsiJavaInjection.class)) {
            if (injection.getTextRange().containsOffset(hostOffset) ||
                    injection.getTextRange().getEndOffset() == hostOffset) {
                return injection;
            }
        }
        return null;
    }
}
