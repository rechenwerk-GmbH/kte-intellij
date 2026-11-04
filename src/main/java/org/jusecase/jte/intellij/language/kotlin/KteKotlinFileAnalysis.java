package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.api.AnalyzeKt;
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi;
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity;
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol;
import org.jetbrains.kotlin.idea.references.KtReference;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jusecase.jte.intellij.language.template.KteKotlinImportResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One analysis pass for source-mapped Kotlin errors and semantic token colors. */
final class KteKotlinFileAnalysis {
    private static final Logger LOG = Logger.getInstance(KteKotlinFileAnalysis.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SOURCE_OWNED_FACTORIES = Set.of(
            "NO_VALUE_FOR_PARAMETER", "ARGUMENT_PASSED_TWICE", "UNUSED_IMPORT"
    );

    @NotNull
    Result collect(@NotNull PsiFile templateFile) {
        try {
            KteKotlinModelService.Model model = KteKotlinModelService.getInstance(templateFile.getProject())
                    .getModel(templateFile);
            KteKotlinProjection projection = model.projection();
            KtFile ktFile = model.ktFile();
            Set<Diagnostic> diagnostics = new LinkedHashSet<>();
            for (PsiErrorElement error : PsiTreeUtil.findChildrenOfType(ktFile, PsiErrorElement.class)) {
                TextRange sourceRange = projection.mapKotlinErrorRangeToTemplate(error.getTextRange());
                if (sourceRange != null) {
                    diagnostics.add(new Diagnostic(HighlightSeverity.ERROR,
                            "Kotlin syntax error: " + error.getErrorDescription(), sourceRange, "SYNTAX"));
                }
            }

            List<SemanticHighlight> highlights = new ArrayList<>();
            AnalyzeKt.analyze(ktFile, session -> {
                for (KaDiagnosticWithPsi<?> diagnostic : session.collectDiagnostics(
                        ktFile, KaDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)) {
                    if (SOURCE_OWNED_FACTORIES.contains(diagnostic.getFactoryName())) {
                        continue;
                    }
                    for (TextRange range : diagnostic.getTextRanges()) {
                        TextRange fileRange = toFileRange(diagnostic.getPsi(), range);
                        TextRange sourceRange = fileRange == null ? null : projection.mapKotlinErrorRangeToTemplate(fileRange);
                        if (sourceRange != null) {
                            diagnostics.add(new Diagnostic(severity(diagnostic.getSeverity()),
                                    diagnostic.getDefaultMessage(), sourceRange, diagnostic.getFactoryName()));
                        }
                    }
                }

                for (KtNameReferenceExpression expression : PsiTreeUtil.findChildrenOfType(
                        ktFile, KtNameReferenceExpression.class)) {
                    TextRange sourceRange = projection.mapKotlinRangeToTemplate(expression.getTextRange());
                    if (sourceRange == null || isImportRange(projection, expression.getTextRange())) {
                        continue;
                    }
                    for (PsiReference reference : expression.getReferences()) {
                        if (!(reference instanceof KtReference ktReference)) {
                            continue;
                        }
                        Collection<KaSymbol> symbols = session.resolveToSymbols(ktReference);
                        for (KaSymbol symbol : symbols) {
                            TextAttributesKey key = colorFor(symbol.getPsi());
                            if (key != null) {
                                highlights.add(new SemanticHighlight(sourceRange, key));
                                break;
                            }
                        }
                        if (!symbols.isEmpty()) {
                            break;
                        }
                    }
                }
                return null;
            });

            collectDeclarationHighlights(projection, ktFile, highlights);
            collectUnusedImports(templateFile, projection, diagnostics);
            return new Result(List.copyOf(diagnostics), List.copyOf(new LinkedHashSet<>(highlights)));
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                throw exception;
            }
            LOG.warn("Unable to analyze KTE Kotlin model", exception);
            return new Result(List.of(), List.of());
        }
    }

    private void collectDeclarationHighlights(@NotNull KteKotlinProjection projection,
                                              @NotNull KtFile ktFile,
                                              @NotNull List<SemanticHighlight> highlights) {
        for (KtParameter parameter : PsiTreeUtil.findChildrenOfType(ktFile, KtParameter.class)) {
            addNameHighlight(projection, parameter.getNameIdentifier(), DefaultLanguageHighlighterColors.PARAMETER, highlights);
        }
        for (KtProperty property : PsiTreeUtil.findChildrenOfType(ktFile, KtProperty.class)) {
            addNameHighlight(projection, property.getNameIdentifier(), DefaultLanguageHighlighterColors.LOCAL_VARIABLE, highlights);
        }
    }

    private void addNameHighlight(@NotNull KteKotlinProjection projection,
                                  @Nullable PsiElement identifier,
                                  @NotNull TextAttributesKey key,
                                  @NotNull List<SemanticHighlight> highlights) {
        if (identifier == null) {
            return;
        }
        TextRange sourceRange = projection.mapKotlinRangeToTemplate(identifier.getTextRange());
        if (sourceRange != null) {
            highlights.add(new SemanticHighlight(sourceRange, key));
        }
    }

    @Nullable
    private static TextAttributesKey colorFor(@Nullable PsiElement target) {
        if (target instanceof PsiClass || target instanceof KtClassOrObject) {
            return DefaultLanguageHighlighterColors.CLASS_NAME;
        }
        if (target instanceof PsiMethod || target instanceof KtNamedFunction) {
            return DefaultLanguageHighlighterColors.FUNCTION_CALL;
        }
        if (target instanceof PsiParameter || target instanceof KtParameter) {
            return DefaultLanguageHighlighterColors.PARAMETER;
        }
        if (target instanceof PsiField) {
            return DefaultLanguageHighlighterColors.INSTANCE_FIELD;
        }
        if (target instanceof KtProperty property) {
            return property.isLocal() ? DefaultLanguageHighlighterColors.LOCAL_VARIABLE
                    : DefaultLanguageHighlighterColors.INSTANCE_FIELD;
        }
        return null;
    }

    private static boolean isImportRange(@NotNull KteKotlinProjection projection, @NotNull TextRange range) {
        for (KteKotlinSourceMapping mapping : projection.getMappings()) {
            if (mapping.getKind() == KteKotlinSourceMapping.Kind.IMPORT &&
                    mapping.getKotlinRange().contains(range)) {
                return true;
            }
        }
        return false;
    }

    private void collectUnusedImports(@NotNull PsiFile templateFile,
                                      @NotNull KteKotlinProjection projection,
                                      @NotNull Set<Diagnostic> diagnostics) {
        // Be conservative: a matching identifier anywhere in copied non-import Kotlin counts as use.
        // This avoids false warnings for aliases, types, and temporarily incomplete expressions.
        Set<String> usedNames = new HashSet<>();
        for (KteKotlinSourceMapping mapping : projection.getMappings()) {
            if (mapping.getKind() == KteKotlinSourceMapping.Kind.IMPORT) {
                continue;
            }
            TextRange range = mapping.getKotlinRange();
            Matcher matcher = IDENTIFIER.matcher(projection.getText().substring(
                    range.getStartOffset(), range.getEndOffset()));
            while (matcher.find()) {
                usedNames.add(matcher.group());
            }
        }

        for (var directive : new KteKotlinImportResolver(templateFile).importDirectives()) {
            if (directive.info().star()) {
                continue;
            }
            String visibleName = directive.info().visibleName();
            boolean unresolved = diagnostics.stream().anyMatch(diagnostic ->
                    "UNRESOLVED_IMPORT".equals(diagnostic.factoryName()) &&
                            diagnostic.range().intersects(directive.source().getTextRange()));
            if (!unresolved && !usedNames.contains(visibleName)) {
                diagnostics.add(new Diagnostic(HighlightSeverity.WARNING,
                        "Unused KTE import", directive.source().getTextRange(), "UNUSED_IMPORT"));
            }
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

    private static HighlightSeverity severity(@NotNull KaSeverity severity) {
        return switch (severity) {
            case ERROR -> HighlightSeverity.ERROR;
            case WARNING -> HighlightSeverity.WARNING;
            case INFO -> HighlightSeverity.INFORMATION;
        };
    }

    record Result(@NotNull List<Diagnostic> diagnostics, @NotNull List<SemanticHighlight> highlights) {
    }

    record Diagnostic(@NotNull HighlightSeverity severity,
                      @NotNull String message,
                      @NotNull TextRange range,
                      @NotNull String factoryName) {
    }

    record SemanticHighlight(@NotNull TextRange range, @NotNull TextAttributesKey key) {
    }
}
