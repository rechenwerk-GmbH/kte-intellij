package org.jusecase.jte.intellij.language.k2;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.JtePsiStatement;
import org.jusecase.jte.intellij.language.template.KteKotlinTypeText;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KteCompletionLookupPolicy {
    private static final Pattern GENERATED_COMPONENT_FUNCTION = Pattern.compile("component\\d+");
    private static final Pattern LOCAL_DECLARATION =
            Pattern.compile("\\b(?:val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private KteCompletionLookupPolicy() {
    }

    static boolean isHiddenNativeResult(@NotNull CompletionResult completionResult,
                                        @NotNull Set<String> hiddenLookupStrings) {
        if (isHiddenLookupString(completionResult.getLookupElement().getLookupString(), hiddenLookupStrings)) {
            return true;
        }

        for (String lookupString : completionResult.getLookupElement().getAllLookupStrings()) {
            if (isHiddenLookupString(lookupString, hiddenLookupStrings)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHiddenLookupString(@NotNull String lookupString,
                                                @NotNull Set<String> hiddenLookupStrings) {
        return isGeneratedComponentFunction(lookupString) ||
                KteGeneratedKotlinNames.isGeneratedLookup(lookupString) ||
                hiddenLookupStrings.contains(lookupString) ||
                "INSTANCE".equals(lookupString);
    }

    static boolean isGeneratedComponentFunction(@NotNull String lookupString) {
        return GENERATED_COMPONENT_FUNCTION.matcher(lookupString).matches();
    }

    @NotNull
    static Set<String> hiddenNativeLookupStrings(@NotNull PsiFile kteFile, int hostOffset) {
        Set<String> result = futureLocalNames(kteFile, hostOffset);
        result.addAll(kteOwnedInsertionLookupStrings(kteFile, hostOffset));
        return result;
    }

    @NotNull
    static Set<String> futureLocalNames(@NotNull PsiFile kteFile, int hostOffset) {
        Set<String> result = new HashSet<>();
        for (JtePsiStatement statement : PsiTreeUtil.findChildrenOfType(kteFile, JtePsiStatement.class)) {
            JtePsiJavaInjection injection = PsiTreeUtil.getChildOfType(statement, JtePsiJavaInjection.class);
            if (injection == null || injection.getTextRange().getStartOffset() <= hostOffset) {
                continue;
            }

            Matcher matcher = LOCAL_DECLARATION.matcher(injection.getText());
            if (matcher.find()) {
                result.add(matcher.group(1));
            }
        }
        return result;
    }

    @NotNull
    static Set<String> kteOwnedInsertionLookupStrings(@NotNull PsiFile kteFile, int hostOffset) {
        Set<String> result = new HashSet<>();
        KteKotlinImportResolver importResolver = new KteKotlinImportResolver(kteFile);
        for (KteKotlinImportResolver.ImportInfo importInfo : importResolver.imports()) {
            if (!importInfo.star() && importResolver.resolveClass(importInfo.visibleName()) == null) {
                result.add(importInfo.visibleName());
            }
        }

        String prefix = completionIdentifierPrefix(kteFile.getText(), hostOffset);
        if (!prefix.isBlank()) {
            for (KteKotlinImportResolver.ImportCandidate candidate : importResolver.importCandidatesByPrefix(prefix, true)) {
                result.add(KteKotlinTypeText.shortName(candidate.qualifiedName()));
            }
        }
        return result;
    }

    @NotNull
    static String completionIdentifierPrefix(@NotNull String text, int offset) {
        return identifierPrefix(text, offset)
                .replace(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "");
    }

    @NotNull
    static String identifierPrefix(@NotNull String text, int offset) {
        int safeOffset = Math.clamp(offset, 0, text.length());
        int start = identifierStart(text, safeOffset);
        return text.substring(start, safeOffset);
    }

    static int identifierStart(@NotNull CharSequence text, int offset) {
        int index = Math.clamp(offset, 0, text.length());
        while (index > 0 && Character.isJavaIdentifierPart(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }
}
