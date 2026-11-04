package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.KtePsiFile;
import org.jusecase.jte.intellij.language.refactoring.KteRemoveImportIntention;

public final class KteKotlinDiagnosticAnnotator implements Annotator {
    private final KteKotlinFileAnalysis analysis = new KteKotlinFileAnalysis();

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof KtePsiFile templateFile)) {
            return;
        }
        if (CompletionService.getCompletionService().getCurrentCompletion() != null) {
            return;
        }

        KteKotlinFileAnalysis.Result result = analysis.collectForEditor(templateFile);
        if (result.analysisUnavailable()) {
            if (templateFile.getTextLength() > 0) {
                holder.newAnnotation(HighlightSeverity.WARNING,
                                "KTE Kotlin analysis unavailable; see the IDE log")
                        .range(TextRange.from(0, 1))
                        .create();
            }
            return;
        }
        for (KteKotlinFileAnalysis.Diagnostic diagnostic : result.diagnostics()) {
            AnnotationBuilder builder = holder.newAnnotation(diagnostic.severity(), diagnostic.message())
                    .range(diagnostic.range());
            if ("UNUSED_IMPORT".equals(diagnostic.factoryName())) {
                builder = builder.withFix(KteRemoveImportIntention.unused(diagnostic.range()));
            }
            builder.create();
        }
        for (KteKotlinFileAnalysis.SemanticHighlight highlight : result.highlights()) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(highlight.range())
                    .textAttributes(highlight.key())
                    .create();
        }
    }
}
