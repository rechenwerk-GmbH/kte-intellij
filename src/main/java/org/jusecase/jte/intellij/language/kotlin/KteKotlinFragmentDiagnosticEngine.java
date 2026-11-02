package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.api.AnalyzeKt;
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity;
import org.jetbrains.kotlin.psi.KtFile;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Collects Kotlin diagnostics in a scratch file and returns only source-mapped errors. */
final class KteKotlinFragmentDiagnosticEngine {
    @NotNull
    List<Diagnostic> collect(@NotNull JtePsiJavaInjection injection) {
        KtePsiJavaContent host = PsiTreeUtil.getParentOfType(injection, KtePsiJavaContent.class, false);
        if (host == null) {
            return List.of();
        }

        try {
            KteKotlinFragmentContext context = KteKotlinFragmentContext.from(host);
            KteKotlinFragmentPlace place = context.placeFor(injection);
            if (place == null) {
                return List.of();
            }

            KteKotlinFragmentProjection projection = KteKotlinFragmentProjection.forAnalysis(context, place);
            PsiFile templateFile = host.getContainingFile();
            KtFile ktFile = KteKotlinScratchFileFactory.createKtFile(
                    templateFile, templateFile.getName() + ".fragment.kt", projection.text());
            return AnalyzeKt.analyze(ktFile, session -> {
                Set<Diagnostic> result = new LinkedHashSet<>();
                for (KaDiagnosticWithPsi<?> diagnostic : session.collectDiagnostics(
                        ktFile, KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)) {
                    if (diagnostic.getSeverity() != KaSeverity.ERROR ||
                            !"UNRESOLVED_REFERENCE".equals(diagnostic.getFactoryName())) {
                        continue;
                    }

                    for (TextRange range : diagnostic.getTextRanges()) {
                        TextRange fileRange = toFileRange(diagnostic.getPsi(), range);
                        TextRange templateRange = fileRange == null ? null : projection.toTemplateRange(fileRange);
                        if (templateRange != null) {
                            result.add(new Diagnostic(diagnostic.getDefaultMessage(), templateRange));
                        }
                    }
                }
                return List.copyOf(result);
            });
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                throw exception;
            }
            return List.of();
        }
    }

    @Nullable
    private static TextRange toFileRange(@NotNull PsiElement psi, @NotNull TextRange range) {
        TextRange psiRange = psi.getTextRange();
        if (psiRange == null) {
            return null;
        }
        if (psiRange.contains(range)) {
            return range;
        }
        if (range.getStartOffset() >= 0 && range.getEndOffset() <= psi.getTextLength()) {
            return range.shiftRight(psiRange.getStartOffset());
        }
        return null;
    }

    record Diagnostic(@NotNull String message, @NotNull TextRange range) {
    }
}
