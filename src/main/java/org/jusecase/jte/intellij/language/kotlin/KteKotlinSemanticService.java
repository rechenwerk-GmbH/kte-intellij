package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.api.AnalyzeKt;
import org.jetbrains.kotlin.analysis.api.KaSession;
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource;
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol;
import org.jetbrains.kotlin.analysis.api.types.KaType;
import org.jetbrains.kotlin.idea.references.KtReference;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.types.Variance;
import org.jusecase.jte.intellij.language.psi.JtePsiImport;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.template.KteTemplateSignatureService;

import java.util.Collection;

/** Resolves .kte references through the shared Kotlin model. Type probes avoid recursive model construction. */
public final class KteKotlinSemanticService {
    private static final Logger LOG = Logger.getInstance(KteKotlinSemanticService.class);
    private KteKotlinSemanticService() {
    }

    @Nullable
    public static SemanticType typeOfTypeText(@NotNull PsiElement sourceElement, @NotNull String typeText) {
        PsiFile templateFile = sourceElement.getContainingFile();
        if (templateFile == null || typeText.isBlank()) {
            return null;
        }

        String propertyName = "__kte_type_probe";
        KtFile ktFile = KteKotlinScratchFileFactory.createKtFile(
                templateFile,
                templateFile.getName() + ".type-fragment.kt",
                importPrefix(templateFile) +
                        "@Suppress(\"unused\")\n" +
                        "val " + propertyName + " = null as " + typeText + "\n"
        );

        KtProperty property = property(ktFile, propertyName);
        KtExpression initializer = property == null ? null : property.getInitializer();
        return initializer == null ? null : expressionType(ktFile, initializer);
    }

    @Nullable
    public static PsiElement resolveReferenceAtTemplateRange(@NotNull JtePsiJavaInjection injection,
                                                               @NotNull TextRange rangeInElement) {
        return resolveModelReference(injection, rangeInElement);
    }

    @Nullable
    public static PsiElement resolveParameterReferenceAtTemplateRange(@NotNull JtePsiJavaInjection injection,
                                                                        @NotNull TextRange rangeInElement) {
        return resolveModelReference(injection, rangeInElement);
    }

    @Nullable
    private static PsiElement resolveModelReference(@NotNull JtePsiJavaInjection injection,
                                                     @NotNull TextRange rangeInElement) {
        PsiFile sourceFile = injection.getContainingFile();
        if (sourceFile == null || rangeInElement.isEmpty()) {
            return null;
        }

        try {
            KteKotlinModelService.Model model = KteKotlinModelService.getInstance(sourceFile.getProject())
                    .getModel(sourceFile);
            int sourceOffset = injection.getTextRange().getStartOffset() + rangeInElement.getStartOffset()
                    + rangeInElement.getLength() / 2;
            Integer kotlinOffset = model.projection().mapTemplateOffsetToKotlin(sourceOffset);
            if (kotlinOffset == null) {
                return null;
            }

            PsiReference reference = findReference(model.ktFile(), kotlinOffset);
            PsiElement target = reference == null ? null : resolveReference(model.ktFile(), reference);
            if (target == null) {
                return null;
            }
            if (target.getContainingFile() != model.ktFile()) {
                return target.getNavigationElement();
            }
            return mapModelTargetBackToTemplate(sourceFile, model.projection(), target);
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                throw exception;
            }
            LOG.warn("Unable to resolve KTE Kotlin reference", exception);
            return null;
        }
    }

    @Nullable
    private static PsiElement mapModelTargetBackToTemplate(@NotNull PsiFile sourceFile,
                                                            @NotNull KteKotlinProjection projection,
                                                            @NotNull PsiElement target) {
        KtNamedDeclaration declaration = PsiTreeUtil.getParentOfType(target, KtNamedDeclaration.class, false);
        if (declaration != null && declaration.getName() != null) {
            if (declaration instanceof KtParameter) {
                KteTemplateSignatureService.Parameter parameter =
                        KteTemplateSignatureService.resolve(sourceFile).parameter(declaration.getName());
                if (parameter != null) {
                    JtePsiJavaInjection sourceParameter = PsiTreeUtil.getChildOfType(
                            parameter.sourceElement(), JtePsiJavaInjection.class);
                    return sourceParameter == null ? parameter.sourceElement() : sourceParameter;
                }
            }
            PsiElement nameIdentifier = declaration.getNameIdentifier();
            if (nameIdentifier != null) {
                TextRange mappedName = projection.mapKotlinRangeToTemplate(nameIdentifier.getTextRange());
                if (mappedName != null) {
                    PsiElement source = sourceFile.findElementAt(mappedName.getStartOffset());
                    JtePsiJavaInjection sourceInjection = source == null ? null :
                            PsiTreeUtil.getParentOfType(source, JtePsiJavaInjection.class, false);
                    return sourceInjection == null ? source : sourceInjection;
                }
            }
        }

        TextRange range = projection.mapKotlinRangeToTemplate(target.getTextRange());
        return range == null ? null : sourceFile.findElementAt(range.getStartOffset());
    }

    @NotNull
    private static String importPrefix(@NotNull PsiFile templateFile) {
        StringBuilder result = new StringBuilder();
        for (JtePsiImport importElement : PsiTreeUtil.findChildrenOfType(templateFile, JtePsiImport.class)) {
            JtePsiJavaInjection injection = PsiTreeUtil.getChildOfType(importElement, JtePsiJavaInjection.class);
            if (injection != null && !injection.getText().isBlank()) {
                result.append("import ").append(injection.getText().trim()).append('\n');
            }
        }
        return result.toString();
    }

    @Nullable
    private static PsiReference findReference(@NotNull KtFile ktFile, int kotlinOffset) {
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
    private static PsiElement resolveReference(@NotNull KtFile ktFile, @NotNull PsiReference reference) {
        if (!(reference instanceof KtReference ktReference)) {
            return reference.resolve();
        }

        try {
            return AnalyzeKt.analyze(ktFile, session -> {
                Collection<KaSymbol> symbols = session.resolveToSymbols(ktReference);
                for (KaSymbol symbol : symbols) {
                    PsiElement target = symbol.getPsi();
                    if (target != null) {
                        return target;
                    }
                }
                return reference.resolve();
            });
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                throw exception;
            }
            LOG.warn("Unable to resolve a synthetic Kotlin reference", exception);
            return null;
        }
    }

    @Nullable
    private static KtProperty property(@NotNull KtFile ktFile, @NotNull String name) {
        for (PsiElement child : ktFile.getChildren()) {
            if (child instanceof KtProperty property && name.equals(property.getName())) {
                return property;
            }
        }
        return null;
    }

    @Nullable
    private static SemanticType expressionType(@NotNull KtFile ktFile, @NotNull KtExpression expression) {
        try {
            return AnalyzeKt.analyze(ktFile, session -> {
                KaType type = session.getExpressionType(expression);
                return type == null ? null : semanticType(session, type);
            });
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                throw exception;
            }
            LOG.warn("Unable to analyze a KTE parameter type", exception);
            return null;
        }
    }

    @NotNull
    private static SemanticType semanticType(@NotNull KaSession session, @NotNull KaType type) {
        String shortType = session.render(type, KaTypeRendererForSource.INSTANCE.getWITH_SHORT_NAMES(), Variance.INVARIANT);
        String qualifiedType = session.render(type, KaTypeRendererForSource.INSTANCE.getWITH_QUALIFIED_NAMES(), Variance.INVARIANT);
        return new SemanticType(shortType, qualifiedType);
    }

    public record SemanticType(@NotNull String typeText, @NotNull String qualifiedTypeText) {
    }
}
