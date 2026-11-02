package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;

public final class KteKotlinDiagnosticAnnotator implements Annotator {
    private final KteKotlinFragmentDiagnosticEngine engine = new KteKotlinFragmentDiagnosticEngine();

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof JtePsiJavaInjection injection)) {
            return;
        }

        for (KteKotlinFragmentDiagnosticEngine.Diagnostic diagnostic : engine.collect(injection)) {
            holder.newAnnotation(HighlightSeverity.ERROR, diagnostic.message())
                    .range(diagnostic.range())
                    .create();
        }
    }
}
