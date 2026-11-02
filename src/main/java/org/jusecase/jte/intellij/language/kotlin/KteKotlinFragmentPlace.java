package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;

record KteKotlinFragmentPlace(@NotNull JtePsiJavaInjection injection,
                              @NotNull String prefix,
                              @NotNull String suffix,
                              @NotNull TextRange range) {
}
