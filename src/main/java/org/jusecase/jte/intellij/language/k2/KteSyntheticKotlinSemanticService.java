package org.jusecase.jte.intellij.language.k2;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.api.AnalyzeKt;
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticKt;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity;
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol;
import org.jetbrains.kotlin.idea.references.KtReference;
import org.jetbrains.kotlin.psi.KtFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Facade for K2 Analysis API access and synthetic/template range mapping.
 */
public final class KteSyntheticKotlinSemanticService {
    private final Project project;

    public KteSyntheticKotlinSemanticService(@NotNull Project project) {
        this.project = project;
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
    public PsiElement resolveReferenceAtTemplateRange(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
        int offsetInElement = rangeInElement.getStartOffset() + rangeInElement.getLength() / 2;
        int templateOffset = element.getTextRange().getStartOffset() + offsetInElement;
        return resolveReferenceAtTemplateOffset(element.getContainingFile(), templateOffset);
    }

    @Nullable
    public PsiElement resolveReferenceAtTemplateOffset(@NotNull PsiFile templateFile, int templateOffset) {
        KteSyntheticKotlinModel model = KteSyntheticKotlinModelService.getInstance(project).getModel(templateFile);

        Integer kotlinOffset = mapTemplateOffsetToKotlin(model, templateOffset);
        if (kotlinOffset == null) {
            return null;
        }

        PsiReference syntheticReference = findSyntheticReference(model.getKtFile(), kotlinOffset);
        if (syntheticReference == null) {
            return null;
        }

        PsiElement analysisTarget = resolveReferenceWithAnalysisApi(model, templateFile, syntheticReference);
        if (analysisTarget != null) {
            return analysisTarget;
        }

        PsiElement target = syntheticReference.resolve();
        return target == null ? null : mapSyntheticTargetBackToTemplate(model, templateFile, target);
    }

    @Nullable
    public Integer mapTemplateOffsetToKotlin(@NotNull KteSyntheticKotlinModel model, int templateOffset) {
        return model.getSyntheticFile().mapTemplateOffsetToKotlin(templateOffset);
    }

    @Nullable
    public TextRange mapKotlinErrorRangeToTemplate(@NotNull KteSyntheticKotlinModel model,
                                                   @NotNull TextRange kotlinRange) {
        return model.getSyntheticFile().mapKotlinErrorRangeToTemplate(kotlinRange);
    }

    @Nullable
    private static PsiReference findSyntheticReference(@NotNull KtFile ktFile, int kotlinOffset) {
        PsiElement leaf = ktFile.findElementAt(kotlinOffset);
        for (PsiElement current = leaf; current != null && current != ktFile; current = current.getParent()) {
            for (PsiReference reference : current.getReferences()) {
                TextRange referenceRange = reference.getRangeInElement().shiftRight(current.getTextRange().getStartOffset());
                if (referenceRange.contains(kotlinOffset)) {
                    return reference;
                }
            }
        }

        return null;
    }

    @Nullable
    private PsiElement resolveReferenceWithAnalysisApi(@NotNull KteSyntheticKotlinModel model,
                                                       @NotNull PsiFile templateFile,
                                                       @NotNull PsiReference syntheticReference) {
        if (!(syntheticReference instanceof KtReference ktReference)) {
            return null;
        }

        try {
            return AnalyzeKt.analyze(model.getKtFile(), session -> {
                Collection<KaSymbol> symbols = session.resolveToSymbols(ktReference);
                for (KaSymbol symbol : symbols) {
                    PsiElement target = symbol.getPsi();
                    if (target != null) {
                        return mapSyntheticTargetBackToTemplate(model, templateFile, target);
                    }
                }

                return null;
            });
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                throw exception;
            }
            return null;
        }
    }

    @NotNull
    public PsiElement mapSyntheticTargetBackToTemplate(@NotNull KteSyntheticKotlinModel model,
                                                       @NotNull PsiFile templateFile,
                                                       @NotNull PsiElement target) {
        if (target.getContainingFile() != model.getKtFile()) {
            return target;
        }

        TextRange targetRange = target.getTextRange();
        if (targetRange == null) {
            return target;
        }

        Integer templateOffset = model.getSyntheticFile().mapKotlinOffsetToTemplate(targetRange.getStartOffset());
        if (templateOffset == null) {
            return target;
        }

        PsiElement templateTarget = templateFile.findElementAt(templateOffset);
        return templateTarget == null ? target : templateTarget;
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
