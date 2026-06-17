package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtPsiFactory;

public final class KteKotlinScratchFileFactory {
    private KteKotlinScratchFileFactory() {
    }

    @NotNull
    public static KtFile createKtFile(@NotNull Project project,
                                      @NotNull String fileName,
                                      @NotNull String text) {
        return createKtFile(project, fileName, text, null);
    }

    @NotNull
    public static KtFile createKtFile(@NotNull Project project,
                                      @NotNull String fileName,
                                      @NotNull String text,
                                      @Nullable PsiElement analysisContext) {
        PsiFile psiFile;
        if (analysisContext == null) {
            psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                    fileName,
                    KotlinLanguage.INSTANCE,
                    text
            );
        } else {
            psiFile = KtPsiFactory.Companion.contextual(analysisContext, false, false)
                    .createFile(fileName, text);
        }

        if (psiFile instanceof KtFile ktFile) {
            configureAnalysisContext(project, ktFile, analysisContext);
            return ktFile;
        }

        throw new IllegalStateException("Kotlin scratch text did not produce a KtFile: " + psiFile.getClass().getName());
    }

    static void configureAnalysisContext(@NotNull Project project,
                                         @NotNull KtFile ktFile,
                                         @Nullable PsiElement analysisContext) {
        KteKotlinModuleContext.configure(project, ktFile, analysisContext);
    }
}
