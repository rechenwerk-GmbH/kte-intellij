package org.jusecase.jte.intellij.language.k2;

import com.intellij.openapi.application.ApplicationManager;
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
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.types.Variance;
import org.jusecase.jte.intellij.language.psi.JtePsiImport;
import org.jusecase.jte.intellij.language.psi.JtePsiFor;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.JtePsiParam;
import org.jusecase.jte.intellij.language.psi.JtePsiStatement;
import org.jusecase.jte.intellij.language.psi.JtePsiTemplate;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KteKotlinFragmentSemanticService {
    private static final Pattern LOCAL_DECLARATION =
            Pattern.compile("\\b(?:val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private KteKotlinFragmentSemanticService() {
    }

    @Nullable
    static SemanticType typeOfTypeText(@NotNull PsiElement sourceElement, @NotNull String typeText) {
        PsiFile templateFile = sourceElement.getContainingFile();
        if (templateFile == null || typeText.isBlank()) {
            return null;
        }

        String propertyName = "__kte_type_probe";
        KtFile ktFile = createKtFile(
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
    static PsiElement resolveReferenceAtTemplateRange(@NotNull JtePsiJavaInjection injection,
                                                      @NotNull TextRange rangeInElement) {
        KteInjectedKotlinFragmentContext context = context(injection);
        if (context == null) {
            return null;
        }

        KteInjectedKotlinFragmentPlace place = place(context, injection);
        if (place == null) {
            return null;
        }

        String prefix = context.prefix(place);
        String suffix = context.suffix(place);
        String injectionPrefix = referenceInjectionPrefix(injection);
        String injectionSuffix = referenceInjectionSuffix(injection);
        String fragmentText = prefix + injectionPrefix + injection.getText() + injectionSuffix + suffix;
        KtFile ktFile = createFragmentFile(context, fragmentText);

        int offsetInElement = rangeInElement.getStartOffset() + rangeInElement.getLength() / 2;
        int injectionStartInFragment = prefix.length() + injectionPrefix.length();
        int kotlinOffset = Math.clamp(injectionStartInFragment + offsetInElement, 0, Math.max(0, fragmentText.length() - 1));
        PsiReference reference = findReference(ktFile, kotlinOffset);
        if (reference == null) {
            return null;
        }

        PsiElement target = resolveReference(ktFile, reference);
        if (target == null) {
            return null;
        }

        return mapTargetBackToTemplate(context, place, ktFile, target, injectionStartInFragment);
    }

    @NotNull
    private static String referenceInjectionPrefix(@NotNull JtePsiJavaInjection injection) {
        return PsiTreeUtil.getParentOfType(injection, JtePsiTemplate.class, false) == null ? "" : "dummyCall(";
    }

    @NotNull
    private static String referenceInjectionSuffix(@NotNull JtePsiJavaInjection injection) {
        return PsiTreeUtil.getParentOfType(injection, JtePsiTemplate.class, false) == null ? "" : ")";
    }

    @Nullable
    private static KteInjectedKotlinFragmentContext context(@NotNull JtePsiJavaInjection injection) {
        KtePsiJavaContent host = PsiTreeUtil.getParentOfType(injection, KtePsiJavaContent.class, false);
        return host == null ? null : KteInjectedKotlinFragmentContext.from(host);
    }

    @Nullable
    private static KteInjectedKotlinFragmentPlace place(@NotNull KteInjectedKotlinFragmentContext context,
                                                       @NotNull JtePsiJavaInjection injection) {
        for (KteInjectedKotlinFragmentPlace place : context.places()) {
            if (place.injection() == injection || place.injection().getTextRange().equals(injection.getTextRange())) {
                return place;
            }
        }
        return null;
    }

    @NotNull
    private static KtFile createFragmentFile(@NotNull KteInjectedKotlinFragmentContext context,
                                             @NotNull String text) {
        return createKtFile(context.host().getContainingFile(), context.host().getContainingFile().getName() + ".fragment.kt", text);
    }

    @NotNull
    private static KtFile createKtFile(@NotNull PsiFile containingFile,
                                       @NotNull String fileName,
                                       @NotNull String text) {
        KteSyntheticKotlinAnalysisContextService contextService =
                KteSyntheticKotlinAnalysisContextService.getInstance(containingFile.getProject());
        PsiElement analysisContext = contextService.findAnalysisContext(
                containingFile,
                contextService.findModuleSourceRoot(containingFile)
        );
        return KteSyntheticKotlinPsiFactory.createKtFile(
                containingFile.getProject(),
                new KteSyntheticKotlinFile(fileName, text, List.of()),
                analysisContext
        );
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
            return null;
        }
    }

    @NotNull
    private static SemanticType semanticType(@NotNull KaSession session, @NotNull KaType type) {
        return new SemanticType(renderShortType(session, type), renderQualifiedType(session, type));
    }

    @NotNull
    private static String renderShortType(@NotNull KaSession session, @NotNull KaType type) {
        return session.render(
                type,
                KaTypeRendererForSource.INSTANCE.getWITH_SHORT_NAMES(),
                Variance.INVARIANT
        );
    }

    @NotNull
    private static String renderQualifiedType(@NotNull KaSession session, @NotNull KaType type) {
        return session.render(
                type,
                KaTypeRendererForSource.INSTANCE.getWITH_QUALIFIED_NAMES(),
                Variance.INVARIANT
        );
    }

    @Nullable
    private static PsiElement mapTargetBackToTemplate(@NotNull KteInjectedKotlinFragmentContext context,
                                                     @NotNull KteInjectedKotlinFragmentPlace place,
                                                     @NotNull KtFile ktFile,
                                                     @NotNull PsiElement target,
                                                     int injectionStartInFragment) {
        if (target.getContainingFile() != ktFile) {
            return target;
        }

        TextRange targetRange = target.getTextRange();
        if (targetRange == null) {
            return null;
        }

        int injectionEndInFragment = injectionStartInFragment + place.injection().getTextLength();
        if (targetRange.getStartOffset() >= injectionStartInFragment &&
                targetRange.getStartOffset() <= injectionEndInFragment) {
            int offsetInInjection = targetRange.getStartOffset() - injectionStartInFragment;
            return templateElementAt(place.injection(), offsetInInjection);
        }

        KtNamedDeclaration declaration = PsiTreeUtil.getParentOfType(target, KtNamedDeclaration.class, false);
        String name = declaration == null ? null : declaration.getName();
        if (name == null) {
            return null;
        }

        PsiElement parameter = parameterSourceElement(context, name);
        if (parameter != null) {
            return parameter;
        }

        PsiElement local = visibleLocalSourceElement(place.injection(), name);
        if (local != null) {
            return local;
        }

        PsiElement imported = importedSourceElement(context, name);
        if (imported != null) {
            return imported;
        }

        return null;
    }

    @Nullable
    private static PsiElement templateElementAt(@NotNull JtePsiJavaInjection injection, int offsetInInjection) {
        int templateOffset = injection.getTextRange().getStartOffset() +
                Math.clamp(offsetInInjection, 0, Math.max(0, injection.getTextLength() - 1));
        return injection.getContainingFile().findElementAt(templateOffset);
    }

    @Nullable
    private static PsiElement parameterSourceElement(@NotNull KteInjectedKotlinFragmentContext context,
                                                    @NotNull String name) {
        KteTemplateSignatureService.Parameter parameter =
                KteTemplateSignatureService.resolve(context.host().getContainingFile()).parameter(name);
        if (parameter == null) {
            return null;
        }

        JtePsiJavaInjection injection = PsiTreeUtil.getChildOfType(parameter.sourceElement(), JtePsiJavaInjection.class);
        return injection == null ? parameter.sourceElement() : injection;
    }

    @Nullable
    private static PsiElement importedSourceElement(@NotNull KteInjectedKotlinFragmentContext context,
                                                   @NotNull String name) {
        return new KteKotlinImportResolver(context.host().getContainingFile()).resolveImportedVisibleName(name);
    }

    @Nullable
    private static PsiElement visibleLocalSourceElement(@NotNull JtePsiJavaInjection targetInjection,
                                                       @NotNull String name) {
        PsiElement loopVariable = enclosingLoopVariable(targetInjection, name);
        if (loopVariable != null) {
            return loopVariable;
        }

        KtePsiJavaContent host = PsiTreeUtil.getParentOfType(targetInjection, KtePsiJavaContent.class, false);
        if (host == null) {
            return null;
        }

        for (JtePsiStatement statement : PsiTreeUtil.findChildrenOfType(host, JtePsiStatement.class)) {
            JtePsiJavaInjection statementInjection = PsiTreeUtil.getChildOfType(statement, JtePsiJavaInjection.class);
            if (statementInjection != null &&
                    statementInjection.getTextRange().getEndOffset() <= targetInjection.getTextRange().getStartOffset() &&
                    KteKotlinFragmentVisibility.isVisibleStatement(statement, targetInjection) &&
                    name.equals(localDeclarationName(statementInjection.getText()))) {
                return statementInjection;
            }
        }
        return null;
    }

    @Nullable
    private static PsiElement enclosingLoopVariable(@NotNull JtePsiJavaInjection targetInjection,
                                                   @NotNull String name) {
        for (JtePsiFor current = PsiTreeUtil.getParentOfType(targetInjection, JtePsiFor.class, false);
             current != null;
             current = PsiTreeUtil.getParentOfType(current, JtePsiFor.class, true)) {
            JtePsiJavaInjection condition = PsiTreeUtil.getChildOfType(current, JtePsiJavaInjection.class);
            if (condition != null &&
                    condition != targetInjection &&
                    condition.getTextRange().getEndOffset() <= targetInjection.getTextRange().getStartOffset() &&
                    name.equals(forLoopVariableName(condition.getText()))) {
                return condition;
            }
        }
        return null;
    }

    @Nullable
    private static String localDeclarationName(@NotNull String text) {
        Matcher matcher = LOCAL_DECLARATION.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Nullable
    private static String forLoopVariableName(@NotNull String text) {
        int inOffset = text.indexOf(" in ");
        if (inOffset <= 0) {
            return null;
        }

        String variableName = text.substring(0, inOffset).trim();
        return variableName.matches("[A-Za-z_][A-Za-z0-9_]*") ? variableName : null;
    }

    record SemanticType(@NotNull String typeText, @NotNull String qualifiedTypeText) {
    }
}
