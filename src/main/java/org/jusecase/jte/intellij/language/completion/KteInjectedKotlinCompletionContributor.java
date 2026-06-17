package org.jusecase.jte.intellij.language.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.completion.KteTemplateParamCompletionProvider;

import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public final class KteInjectedKotlinCompletionContributor extends CompletionContributor {
    public KteInjectedKotlinCompletionContributor() {
        extend(CompletionType.BASIC, psiElement(), new KteInjectedKotlinCompletionProvider());
    }

    private static final class KteInjectedKotlinCompletionProvider extends CompletionProvider<CompletionParameters> {
        private final KteKotlinCompletionSupplement completionSupplement = new KteKotlinCompletionSupplement();

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            KteCompletionHostContext hostContext = KteCompletionHostContext.fromInjectedKotlinCompletion(parameters);
            if (hostContext == null) {
                return;
            }

            if (KteTemplateParamCompletionProvider.addCompletions(
                    hostContext.hostElement(),
                    hostContext.hostOffset(),
                    result
            )) {
                result.stopHere();
                return;
            }

            Set<String> hiddenLookupStrings =
                    KteCompletionLookupPolicy.hiddenNativeLookupStrings(hostContext.kteFile(), hostContext.hostOffset());
            result.runRemainingContributors(parameters, completionResult -> {
                if (!KteCompletionLookupPolicy.isHiddenNativeResult(completionResult, hiddenLookupStrings)) {
                    result.passResult(completionResult);
                }
            });

            completionSupplement.addExpressionCompletions(result, hostContext.kteFile(), hostContext.hostOffset());
            result.stopHere();
        }
    }
}
