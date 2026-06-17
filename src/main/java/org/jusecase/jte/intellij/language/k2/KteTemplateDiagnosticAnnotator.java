package org.jusecase.jte.intellij.language.k2;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;

public final class KteTemplateDiagnosticAnnotator implements Annotator {
    private final KteTemplateDiagnosticCollector collector = new KteTemplateDiagnosticCollector();

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof KtePsiJavaContent)) {
            return;
        }

        PsiFile templateFile = element.getContainingFile();
        for (KteTemplateDiagnostic diagnostic : collector.collect(templateFile)) {
            AnnotationBuilder builder = holder.newAnnotation(diagnostic.severity(), diagnostic.message())
                    .range(diagnostic.range());
            for (IntentionAction fix : diagnostic.fixes()) {
                builder = builder.withFix(fix);
            }
            builder.create();
        }
    }
}
