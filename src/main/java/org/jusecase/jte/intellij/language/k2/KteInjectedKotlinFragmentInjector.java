package org.jusecase.jte.intellij.language.k2;

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

public final class KteInjectedKotlinFragmentInjector implements MultiHostInjector {
    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (CompletionService.getCompletionService().getCurrentCompletion() == null) {
            return;
        }

        if (!(context instanceof KtePsiJavaContent host)) {
            return;
        }

        KteInjectedKotlinFragmentContext injectionContext = KteInjectedKotlinFragmentContext.from(host);
        if (injectionContext.places().isEmpty()) {
            return;
        }

        Key<KaModule> contextModuleKey = KteSyntheticKotlinModuleContext.contextModuleUserDataKey();
        for (KteInjectedKotlinFragmentPlace place : injectionContext.places()) {
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
