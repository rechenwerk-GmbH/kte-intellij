package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;

final class KteKotlinFragmentPlace {
    private final JtePsiJavaInjection injection;
    private final String prefix;
    private final String suffix;
    private final TextRange range;
    private int loopDepth;

    KteKotlinFragmentPlace(@NotNull JtePsiJavaInjection injection,
                                   @NotNull String prefix,
                                   @NotNull String suffix,
                                   @NotNull TextRange range) {
        this.injection = injection;
        this.prefix = prefix;
        this.suffix = suffix;
        this.range = range;
    }

    @NotNull
    JtePsiJavaInjection injection() {
        return injection;
    }

    @NotNull
    String prefix() {
        return prefix;
    }

    @NotNull
    String suffix() {
        return suffix;
    }

    @NotNull
    TextRange range() {
        return range;
    }

    int loopDepth() {
        return loopDepth;
    }

    void setLoopDepth(int loopDepth) {
        this.loopDepth = loopDepth;
    }
}
