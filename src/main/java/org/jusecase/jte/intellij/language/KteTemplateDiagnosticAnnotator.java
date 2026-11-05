package org.jusecase.jte.intellij.language;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;

public final class KteTemplateDiagnosticAnnotator implements Annotator {
    private final KteTemplateContractDiagnosticChecker checker = new KteTemplateContractDiagnosticChecker();

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof KtePsiJavaContent)) {
            return;
        }

        PsiFile templateFile = element.getContainingFile();
        for (KteTemplateDiagnostic diagnostic : checker.collect(templateFile)) {
            AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, diagnostic.message())
                    .range(diagnostic.range());
            for (IntentionAction fix : diagnostic.fixes()) {
                builder = builder.withFix(fix);
            }
            builder.create();
        }
    }
}
