package org.jusecase.jte.intellij.language.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.KteLanguage;
import org.jusecase.jte.intellij.language.completion.KteKotlinCompletionSupplement;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.JtePsiParam;
import org.jusecase.jte.intellij.language.psi.KtePsiFile;

public class KteParameterKotlinCompletionProvider extends CompletionProvider<CompletionParameters> {
    private final KteKotlinCompletionSupplement completionSupplement = new KteKotlinCompletionSupplement();

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        JtePsiJavaInjection completionInjection = findParameterInjection(parameters);
        if (completionInjection == null) {
            return;
        }

        completionSupplement.addParameterTypeCompletions(result, completionInjection.getContainingFile(), parameters.getOffset());
    }

    @Nullable
    private JtePsiJavaInjection findParameterInjection(@NotNull CompletionParameters parameters) {
        PsiElement position = parameters.getPosition();
        JtePsiJavaInjection injection = PsiTreeUtil.getParentOfType(position, JtePsiJavaInjection.class, false);
        if (injection == null && position instanceof JtePsiJavaInjection javaInjection) {
            injection = javaInjection;
        }

        if (injection == null || !isKteFile(injection.getContainingFile())) {
            return null;
        }

        return PsiTreeUtil.getParentOfType(injection, JtePsiParam.class, false) == null ? null : injection;
    }

    private boolean isKteFile(@NotNull PsiElement element) {
        return element instanceof KtePsiFile ||
                element.getContainingFile().getViewProvider().getPsi(KteLanguage.INSTANCE) instanceof KtePsiFile;
    }
}
