package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
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

        for (KteKotlinFragmentPlace place : injectionContext.places()) {
            MultiHostRegistrar kotlinRegistrar = registrar.startInjecting(KotlinFileType.INSTANCE.getLanguage())
                    .makeInspectionsLenient(true);
            KteKotlinModuleContext.attachCompletionContext(kotlinRegistrar, injectionContext.contextModule());
            KteKotlinFragmentContext.Scaffolding scaffolding = injectionContext.scaffolding(place);
            kotlinRegistrar.addPlace(scaffolding.prefix(), scaffolding.suffix(), host, place.range());
            kotlinRegistrar.doneInjecting();
        }
    }

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(KtePsiJavaContent.class);
    }
}
