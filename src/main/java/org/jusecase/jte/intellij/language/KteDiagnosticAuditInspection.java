package org.jusecase.jte.intellij.language;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.kotlin.KteKotlinFileAnalysis;
import org.jusecase.jte.intellij.language.psi.KtePsiFile;

import java.util.ArrayList;
import java.util.List;

/** Opt-in batch inspection for auditing the diagnostics that KTE annotators report. */
public final class KteDiagnosticAuditInspection extends LocalInspectionTool {
    private static final Logger LOG = Logger.getInstance(KteDiagnosticAuditInspection.class);

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file,
                                                    @NotNull InspectionManager manager,
                                                    boolean onTheFly) {
        if (!(file instanceof KtePsiFile)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        List<ProblemDescriptor> problems = new ArrayList<>();
        try {
            for (PsiErrorElement error : PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class)) {
                add(problems, manager, file, error.getTextRange(), "PARSER", "SYNTAX",
                        error.getErrorDescription(), HighlightSeverity.ERROR, onTheFly);
            }
            for (KteStructuralDiagnosticCollector.Diagnostic diagnostic :
                    new KteStructuralDiagnosticCollector().collect(file)) {
                add(problems, manager, file, diagnostic.range(), "STRUCTURE", diagnostic.code(),
                        diagnostic.message(), HighlightSeverity.ERROR, onTheFly);
            }
            for (KteTemplateDiagnostic diagnostic : new KteTemplateDiagnosticCollector().collect(file)) {
                add(problems, manager, file, diagnostic.range(), "CONTRACT", diagnostic.code(),
                        diagnostic.message(), diagnostic.severity(), onTheFly);
            }
            for (KteKotlinFileAnalysis.Diagnostic diagnostic : KteKotlinFileAnalysis.auditDiagnostics(file)) {
                add(problems, manager, file, diagnostic.range(), "KOTLIN", diagnostic.factoryName(),
                        diagnostic.message(), diagnostic.severity(), onTheFly);
            }
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            LOG.warn("KTE diagnostic audit failed for " + file.getVirtualFile(), exception);
            add(problems, manager, file, TextRange.from(0, Math.min(1, file.getTextLength())),
                    "AUDIT", "ANALYSIS_FAILURE", exception.getClass().getSimpleName() + ": " +
                            String.valueOf(exception.getMessage()), HighlightSeverity.ERROR, onTheFly);
        }
        return problems.toArray(ProblemDescriptor[]::new);
    }

    private void add(List<ProblemDescriptor> problems, InspectionManager manager, PsiFile file,
                     TextRange range, String origin, String code, String message,
                     HighlightSeverity severity, boolean onTheFly) {
        if (severity != HighlightSeverity.ERROR && severity != HighlightSeverity.WARNING) {
            return;
        }
        if (range.isEmpty() || range.getStartOffset() < 0 || range.getEndOffset() > file.getTextLength()) {
            return;
        }
        ProblemHighlightType type = severity == HighlightSeverity.ERROR
                ? ProblemHighlightType.ERROR : ProblemHighlightType.WARNING;
        problems.add(manager.createProblemDescriptor(file, range,
                "[KTE/" + origin + "/" + code + "] " + message, type, onTheFly));
    }
}
