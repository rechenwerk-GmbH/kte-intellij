package org.jusecase.jte.intellij.language;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class KteStructuralAnnotator implements Annotator {
    private final KteStructuralDiagnosticCollector collector = new KteStructuralDiagnosticCollector();

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        for (KteStructuralDiagnosticCollector.Diagnostic diagnostic : collector.collect(element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, diagnostic.message())
                    .range(diagnostic.range())
                    .create();
        }
    }
}
