package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;

import java.util.List;

public final class KteKotlinFragmentInjector implements MultiHostInjector {
    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (CompletionService.getCompletionService().getCurrentCompletion() == null) {
            return;
        }

        if (!(context instanceof KtePsiJavaContent host)) {
            return;
        }

        KteKotlinFragmentContext injectionContext = KteKotlinFragmentContext.from(host);
        if (injectionContext.places().isEmpty()) {
            return;
        }

        Key<KaModule> contextModuleKey = KteKotlinModuleContext.contextModuleUserDataKey();
        for (KteKotlinFragmentPlace place : injectionContext.places()) {
            MultiHostRegistrar kotlinRegistrar = registrar.startInjecting(KotlinFileType.INSTANCE.getLanguage())
                    .makeInspectionsLenient(true);
            if (contextModuleKey != null) {
                kotlinRegistrar.putInjectedFileUserData(contextModuleKey, injectionContext.contextModule());
            }
            kotlinRegistrar.addPlace(injectionContext.prefix(place), injectionContext.suffix(place), host, place.range());
            kotlinRegistrar.doneInjecting();
        }
    }

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(KtePsiJavaContent.class);
    }
}
