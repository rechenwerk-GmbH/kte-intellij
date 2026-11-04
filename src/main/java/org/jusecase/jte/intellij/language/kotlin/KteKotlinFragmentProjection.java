package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;

/** A completion-only Kotlin fragment and the source span represented at its caret. */
record KteKotlinFragmentProjection(@NotNull String text,
                                   @NotNull String prefix,
                                   @NotNull String suffix,
                                   @NotNull TextRange sourceInKotlin,
                                   @NotNull TextRange sourceInTemplate) {
    @NotNull
    static KteKotlinFragmentProjection forCompletion(@NotNull KteKotlinFragmentContext context,
                                                     @NotNull KteKotlinFragmentPlace place) {
        KteKotlinFragmentContext.Scaffolding scaffolding = context.scaffolding(place);
        JtePsiJavaInjection injection = place.injection();
        String prefix = scaffolding.prefix();
        String suffix = scaffolding.suffix();
        int start = prefix.length();
        String text = prefix + injection.getText() + suffix;
        return new KteKotlinFragmentProjection(
                text,
                prefix,
                suffix,
                TextRange.from(start, injection.getTextLength()),
                injection.getTextRange()
        );
    }
}
