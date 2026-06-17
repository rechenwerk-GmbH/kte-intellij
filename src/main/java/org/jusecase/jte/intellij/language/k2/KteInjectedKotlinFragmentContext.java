package org.jusecase.jte.intellij.language.k2;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule;
import org.jusecase.jte.intellij.language.psi.JtePsiElseIf;
import org.jusecase.jte.intellij.language.psi.JtePsiFor;
import org.jusecase.jte.intellij.language.psi.JtePsiIf;
import org.jusecase.jte.intellij.language.psi.JtePsiImport;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.JtePsiOutput;
import org.jusecase.jte.intellij.language.psi.JtePsiParam;
import org.jusecase.jte.intellij.language.psi.JtePsiStatement;
import org.jusecase.jte.intellij.language.psi.JtePsiTemplate;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;
import org.jusecase.jte.intellij.language.template.KteKotlinTypeText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record KteInjectedKotlinFragmentContext(@NotNull Map<String, String> parameters,
                                        @NotNull String importPrefix,
                                        @NotNull KaModule contextModule,
                                        @NotNull KtePsiJavaContent host,
                                        @NotNull List<KteInjectedKotlinFragmentPlace> places) {
    private static final Pattern KOTLIN_PARAMETER =
            Pattern.compile("^\\s*(?:vararg\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([^=]+?)(?:\\s*=.*)?$");
    private static final Pattern TYPE_REFERENCE = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final List<String> KNOWN_TYPE_NAMES = List.of(
            "Any", "Array", "Boolean", "Byte", "Char", "Collection", "Double", "Float", "Int", "Iterable",
            "List", "Long", "Map", "MutableList", "MutableMap", "MutableSet", "Nothing", "Sequence", "Set",
            "Short", "String", "Unit"
    );
    private static final List<String> TYPE_KEYWORDS = List.of("in", "out", "where");

    @NotNull
    static KteInjectedKotlinFragmentContext from(@NotNull KtePsiJavaContent host) {
        return new KteInjectedKotlinFragmentContext(
                parameters(host),
                importPrefix(host),
                contextModule(host),
                host,
                places(host)
        );
    }

    @NotNull
    String prefix(@NotNull KteInjectedKotlinFragmentPlace place) {
        StringBuilder result = new StringBuilder();
        result.append(importPrefix)
                .append("@Suppress(\"unused\", \"UNUSED_PARAMETER\")\n")
                .append("fun __jte_render() {\n");
        appendLocalVariables(result);
        int loopDepth = appendEnclosingLoops(result, place.injection());
        appendPreviousStatements(result, place.injection());
        result.append(place.prefix());
        place.setLoopDepth(loopDepth);
        return result.toString();
    }

    @NotNull
    String suffix(@NotNull KteInjectedKotlinFragmentPlace place) {
        StringBuilder result = new StringBuilder(place.suffix());
        for (int i = 0; i < place.loopDepth(); i++) {
            result.append("}\n");
        }
        result.append("}\n");
        return result.toString();
    }

    @NotNull
    private static Map<String, String> parameters(@NotNull KtePsiJavaContent host) {
        Map<String, String> result = new LinkedHashMap<>();
        KteKotlinImportResolver importResolver = new KteKotlinImportResolver(host.getContainingFile());
        for (JtePsiParam param : PsiTreeUtil.findChildrenOfType(host, JtePsiParam.class)) {
            JtePsiJavaInjection declaration = PsiTreeUtil.getChildOfType(param, JtePsiJavaInjection.class);
            if (declaration == null) {
                continue;
            }

            Matcher matcher = KOTLIN_PARAMETER.matcher(declaration.getText());
            if (matcher.matches()) {
                result.put(matcher.group(1), qualifyType(matcher.group(2).trim(), importResolver));
            }
        }
        return result;
    }

    @NotNull
    private static String importPrefix(@NotNull KtePsiJavaContent host) {
        StringBuilder result = new StringBuilder();
        KteKotlinImportResolver importResolver = new KteKotlinImportResolver(host.getContainingFile());
        for (KteKotlinImportResolver.ImportInfo importInfo : importResolver.imports()) {
            if (importInfo.star() || !IDENTIFIER.matcher(importInfo.visibleName()).matches()) {
                continue;
            }

            PsiClass psiClass = importResolver.resolveClass(importInfo.visibleName());
            if (psiClass != null && psiClass.getQualifiedName() != null) {
                result.append("typealias ")
                        .append(importInfo.visibleName())
                        .append(" = ")
                        .append(psiClass.getQualifiedName())
                        .append('\n');
            } else {
                result.append("@Suppress(\"unused\")\n")
                        .append("fun ")
                        .append(importInfo.visibleName())
                        .append("(): Any? = null\n")
                        .append("@Suppress(\"unused\")\n")
                        .append("fun Any?.")
                        .append(importInfo.visibleName())
                        .append("(vararg __jteArgs: Any?): Any? = null\n");
            }
        }
        return result.toString();
    }

    @NotNull
    private static String qualifyType(@NotNull String type, @NotNull KteKotlinImportResolver importResolver) {
        Matcher matcher = TYPE_REFERENCE.matcher(type);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(qualifyTypeReference(matcher.group(), importResolver)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @NotNull
    private static String qualifyTypeReference(@NotNull String typeName, @NotNull KteKotlinImportResolver importResolver) {
        if (typeName.contains(".") ||
                TYPE_KEYWORDS.contains(typeName) ||
                KNOWN_TYPE_NAMES.contains(KteKotlinTypeText.shortName(typeName))) {
            return typeName;
        }

        PsiClass psiClass = importResolver.resolveClass(typeName);
        return psiClass == null || psiClass.getQualifiedName() == null ? typeName : psiClass.getQualifiedName();
    }

    @NotNull
    private static KaModule contextModule(@NotNull KtePsiJavaContent host) {
        KteSyntheticKotlinAnalysisContextService contextService =
                KteSyntheticKotlinAnalysisContextService.getInstance(host.getProject());
        PsiElement analysisContext = contextService.findAnalysisContext(
                host.getContainingFile(),
                contextService.findModuleSourceRoot(host.getContainingFile())
        );
        return KteSyntheticKotlinModuleContext.contextModule(host.getProject(), analysisContext);
    }

    @NotNull
    private static List<KteInjectedKotlinFragmentPlace> places(@NotNull KtePsiJavaContent host) {
        List<KteInjectedKotlinFragmentPlace> result = new ArrayList<>();
        int hostStartOffset = host.getTextRange().getStartOffset();
        for (JtePsiJavaInjection injection : PsiTreeUtil.findChildrenOfType(host, JtePsiJavaInjection.class)) {
            KteInjectedKotlinFragmentPlace place = place(hostStartOffset, injection);
            if (place != null) {
                result.add(place);
            }
        }
        return result;
    }

    private static KteInjectedKotlinFragmentPlace place(int hostStartOffset, @NotNull JtePsiJavaInjection injection) {
        if (PsiTreeUtil.getParentOfType(injection, JtePsiImport.class, false) != null ||
                PsiTreeUtil.getParentOfType(injection, JtePsiParam.class, false) != null) {
            return null;
        }

        TextRange range = injection.getTextRange().shiftLeft(hostStartOffset);
        JtePsiIf ifElement = PsiTreeUtil.getParentOfType(injection, JtePsiIf.class, false);
        if (ifElement != null && PsiTreeUtil.getChildOfType(ifElement, JtePsiJavaInjection.class) == injection) {
            return new KteInjectedKotlinFragmentPlace(injection, "if (", ") {\n}\n", range);
        }
        JtePsiElseIf elseIfElement = PsiTreeUtil.getParentOfType(injection, JtePsiElseIf.class, false);
        if (elseIfElement != null && PsiTreeUtil.getChildOfType(elseIfElement, JtePsiJavaInjection.class) == injection) {
            return new KteInjectedKotlinFragmentPlace(injection, "if (true) {\n} else if (", ") {\n}\n", range);
        }
        JtePsiFor forElement = PsiTreeUtil.getParentOfType(injection, JtePsiFor.class, false);
        if (forElement != null && PsiTreeUtil.getChildOfType(forElement, JtePsiJavaInjection.class) == injection) {
            return new KteInjectedKotlinFragmentPlace(injection, "for (", ") {\n}\n", range);
        }
        if (PsiTreeUtil.getParentOfType(injection, JtePsiOutput.class, false) != null ||
                PsiTreeUtil.getParentOfType(injection, JtePsiStatement.class, false) != null ||
                PsiTreeUtil.getParentOfType(injection, JtePsiTemplate.class, false) != null) {
            return new KteInjectedKotlinFragmentPlace(injection, "", "\n", range);
        }

        return null;
    }

    private void appendLocalVariables(@NotNull StringBuilder result) {
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            result.append("val ")
                    .append(parameter.getKey())
                    .append(" = null as ")
                    .append(parameter.getValue())
                    .append('\n');
        }
    }

    private int appendEnclosingLoops(@NotNull StringBuilder result, @NotNull JtePsiJavaInjection injection) {
        List<JtePsiFor> enclosingLoops = new ArrayList<>();
        for (JtePsiFor current = PsiTreeUtil.getParentOfType(injection, JtePsiFor.class, false);
             current != null;
             current = PsiTreeUtil.getParentOfType(current, JtePsiFor.class, true)) {
            JtePsiJavaInjection condition = PsiTreeUtil.getChildOfType(current, JtePsiJavaInjection.class);
            if (condition != null && condition != injection &&
                    condition.getTextRange().getEndOffset() <= injection.getTextRange().getStartOffset()) {
                enclosingLoops.add(current);
            }
        }

        Collections.reverse(enclosingLoops);
        for (JtePsiFor loop : enclosingLoops) {
            JtePsiJavaInjection condition = PsiTreeUtil.getChildOfType(loop, JtePsiJavaInjection.class);
            if (condition != null) {
                result.append("for (")
                        .append(condition.getText())
                        .append(") {\n");
            }
        }
        return enclosingLoops.size();
    }

    private void appendPreviousStatements(@NotNull StringBuilder result, @NotNull JtePsiJavaInjection injection) {
        for (JtePsiStatement statement : PsiTreeUtil.findChildrenOfType(host, JtePsiStatement.class)) {
            JtePsiJavaInjection statementInjection = PsiTreeUtil.getChildOfType(statement, JtePsiJavaInjection.class);
            if (statementInjection == null ||
                    statementInjection.getTextRange().getEndOffset() > injection.getTextRange().getStartOffset() ||
                    !KteKotlinFragmentVisibility.isVisibleStatement(statement, injection)) {
                continue;
            }

            String statementText = statementInjection.getText().trim();
            if (!statementText.isEmpty()) {
                result.append(statementText).append('\n');
            }
        }
    }
}
