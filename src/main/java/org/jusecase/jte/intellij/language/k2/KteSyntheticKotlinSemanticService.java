package org.jusecase.jte.intellij.language.k2;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.api.AnalyzeKt;
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticKt;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity;

import java.util.ArrayList;
import java.util.List;

/**
 * Facade for K2 Analysis API access and synthetic/template range mapping.
 */
public final class KteSyntheticKotlinSemanticService {
    public KteSyntheticKotlinSemanticService(@NotNull Project project) {
    }

    @NotNull
    public static KteSyntheticKotlinSemanticService getInstance(@NotNull Project project) {
        return project.getService(KteSyntheticKotlinSemanticService.class);
    }

    @NotNull
    public List<KteSyntheticKotlinDiagnosticCollector.Diagnostic> collectKotlinDiagnostics(
            @NotNull PsiFile templateFile,
            @NotNull KteSyntheticKotlinModel model) {
        List<KteSyntheticKotlinDiagnosticCollector.Diagnostic> diagnostics = new ArrayList<>();
        collectMappedSyntaxErrors(model, diagnostics);
        diagnostics.addAll(collectAnalysisApiDiagnostics(templateFile, model));
        return diagnostics;
    }

    @Nullable
    private TextRange mapKotlinErrorRangeToTemplate(@NotNull KteSyntheticKotlinModel model,
                                                    @NotNull TextRange kotlinRange) {
        return model.getSyntheticFile().mapKotlinErrorRangeToTemplate(kotlinRange);
    }

    private void collectMappedSyntaxErrors(
            @NotNull KteSyntheticKotlinModel model,
            @NotNull List<KteSyntheticKotlinDiagnosticCollector.Diagnostic> diagnostics) {
        for (PsiErrorElement errorElement : PsiTreeUtil.findChildrenOfType(model.getKtFile(), PsiErrorElement.class)) {
            TextRange templateRange = mapKotlinErrorRangeToTemplate(model, errorElement.getTextRange());
            if (templateRange != null && !templateRange.isEmpty()) {
                diagnostics.add(new KteSyntheticKotlinDiagnosticCollector.Diagnostic(
                        HighlightSeverity.ERROR,
                        "Kotlin syntax error: " + errorElement.getErrorDescription(),
                        templateRange
                ));
            }
        }
    }

    @NotNull
    private List<KteSyntheticKotlinDiagnosticCollector.Diagnostic> collectAnalysisApiDiagnostics(
            @NotNull PsiFile templateFile,
            @NotNull KteSyntheticKotlinModel model) {
        try {
            return AnalyzeKt.analyze(model.getKtFile(), session -> {
                List<KteSyntheticKotlinDiagnosticCollector.Diagnostic> diagnostics = new ArrayList<>();
                for (KaDiagnosticWithPsi<?> kotlinDiagnostic :
                        session.collectDiagnostics(
                                model.getKtFile(),
                                KaDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS
                        )) {
                    collectDiagnostic(templateFile, model, kotlinDiagnostic, diagnostics);
                }

                return diagnostics;
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

    private void collectDiagnostic(
            @NotNull PsiFile templateFile,
            @NotNull KteSyntheticKotlinModel model,
            @NotNull KaDiagnosticWithPsi<?> kotlinDiagnostic,
            @NotNull List<KteSyntheticKotlinDiagnosticCollector.Diagnostic> diagnostics) {
        HighlightSeverity severity = severity(kotlinDiagnostic.getSeverity());
        String message = KaDiagnosticKt.getDefaultMessageWithFactoryName(kotlinDiagnostic);
        for (TextRange kotlinRange : kotlinRanges(kotlinDiagnostic, model.getKtFile().getTextLength())) {
            TextRange templateRange = mapKotlinErrorRangeToTemplate(model, kotlinRange);
            if (templateRange == null || templateRange.isEmpty()) {
                continue;
            }

            diagnostics.add(new KteSyntheticKotlinDiagnosticCollector.Diagnostic(
                    severity,
                    message,
                    templateRange,
                    KteSyntheticKotlinDiagnosticCollector.Origin.SYNTHETIC_KOTLIN
            ));
        }
    }

    @NotNull
    private List<TextRange> kotlinRanges(@NotNull KaDiagnosticWithPsi<?> diagnostic, int fileLength) {
        List<TextRange> ranges = new ArrayList<>();
        PsiElement psi = diagnostic.getPsi();
        TextRange psiRange = psi.getTextRange();
        for (TextRange range : diagnostic.getTextRanges()) {
            TextRange fileRange = toFileRange(range, psiRange, fileLength);
            if (fileRange != null && !fileRange.isEmpty()) {
                ranges.add(fileRange);
            }
        }

        if (ranges.isEmpty() && psiRange != null && !psiRange.isEmpty()) {
            ranges.add(psiRange);
        }

        return ranges;
    }

    @Nullable
    private TextRange toFileRange(@NotNull TextRange range, @Nullable TextRange psiRange, int fileLength) {
        if (range.getStartOffset() >= 0 &&
                range.getEndOffset() <= fileLength &&
                (psiRange == null || range.intersects(psiRange) || psiRange.contains(range))) {
            return range;
        }

        if (psiRange != null &&
                range.getStartOffset() >= 0 &&
                range.getEndOffset() <= psiRange.getLength()) {
            return range.shiftRight(psiRange.getStartOffset());
        }

        return null;
    }

    @NotNull
    private HighlightSeverity severity(@NotNull KaSeverity severity) {
        return switch (severity) {
            case ERROR -> HighlightSeverity.ERROR;
            case WARNING -> HighlightSeverity.WARNING;
            case INFO -> HighlightSeverity.INFORMATION;
        };
    }
}
