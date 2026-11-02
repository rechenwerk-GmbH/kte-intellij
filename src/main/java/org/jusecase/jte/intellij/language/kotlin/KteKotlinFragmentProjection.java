package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.JtePsiTemplate;

/** A scoped Kotlin file and the only source range that may be mapped back to a .kte file. */
record KteKotlinFragmentProjection(@NotNull String text,
                                   @NotNull String prefix,
                                   @NotNull String suffix,
                                   @NotNull TextRange sourceInKotlin,
                                   @NotNull TextRange sourceInTemplate) {
    @NotNull
    static KteKotlinFragmentProjection forAnalysis(@NotNull KteKotlinFragmentContext context,
                                                   @NotNull KteKotlinFragmentPlace place) {
        return create(context, place, true);
    }

    @NotNull
    static KteKotlinFragmentProjection forCompletion(@NotNull KteKotlinFragmentContext context,
                                                     @NotNull KteKotlinFragmentPlace place) {
        return create(context, place, false);
    }

    @NotNull
    private static KteKotlinFragmentProjection create(@NotNull KteKotlinFragmentContext context,
                                                      @NotNull KteKotlinFragmentPlace place,
                                                      boolean wrapTemplateArgument) {
        KteKotlinFragmentContext.Scaffolding scaffolding = context.scaffolding(place);
        JtePsiJavaInjection injection = place.injection();
        boolean templateArgument = wrapTemplateArgument &&
                PsiTreeUtil.getParentOfType(injection, JtePsiTemplate.class, false) != null;
        String injectionPrefix = templateArgument ? "dummyCall(" : "";
        String injectionSuffix = templateArgument ? ")" : "";
        String prefix = scaffolding.prefix() + injectionPrefix;
        String suffix = injectionSuffix + scaffolding.suffix();
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

    @Nullable
    TextRange toTemplateRange(@NotNull TextRange kotlinRange) {
        if (kotlinRange.isEmpty() ||
                kotlinRange.getStartOffset() < sourceInKotlin.getStartOffset() ||
                kotlinRange.getEndOffset() > sourceInKotlin.getEndOffset()) {
            return null;
        }

        return kotlinRange.shiftLeft(sourceInKotlin.getStartOffset())
                .shiftRight(sourceInTemplate.getStartOffset());
    }
}
