package org.jusecase.jte.intellij.language.k2;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jusecase.jte.intellij.language.KteLanguage;
import org.jusecase.jte.intellij.language.refactoring.KteNativeTemplateSourceEditUtil;
import org.jusecase.jte.intellij.language.psi.KtePsiFile;
import org.jusecase.jte.intellij.language.template.KteKotlinTypeText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KteKotlinCompletionSupplement {
    public void addExpressionCompletions(@NotNull CompletionResultSet result,
                                         @NotNull PsiFile templateFile,
                                         int hostOffset) {
        String prefix = KteCompletionLookupPolicy.completionIdentifierPrefix(templateFile.getText(), hostOffset);

        CompletionResultSet prefixedResult = result.withPrefixMatcher(prefix);
        Map<String, CompletionCandidate> candidates = new LinkedHashMap<>();
        if (receiverAccess(templateFile.getText(), hostOffset) != null) {
            addSimpleReceiverCandidates(candidates, templateFile, hostOffset);
            addImportedVisibleNameCandidates(candidates, templateFile);
        } else if (!prefix.isBlank()) {
            candidates.putAll(importCandidates(templateFile, prefix, true));
        }

        for (CompletionCandidate candidate : candidates.values()) {
            prefixedResult.addElement(lookupElement(candidate));
        }
    }

    public void addParameterTypeCompletions(@NotNull CompletionResultSet result,
                                            @NotNull PsiFile templateFile,
                                            int hostOffset) {
        String prefix = KteCompletionLookupPolicy.completionIdentifierPrefix(templateFile.getText(), hostOffset);
        CompletionResultSet prefixedResult = result.withPrefixMatcher(prefix);

        Map<String, CompletionCandidate> candidates = new LinkedHashMap<>();
        addBuiltinTypeCandidates(candidates);
        if (!prefix.isBlank()) {
            candidates.putAll(importCandidates(templateFile, prefix, false));
        }

        for (CompletionCandidate candidate : candidates.values()) {
            prefixedResult.addElement(lookupElement(candidate));
        }
    }

    @NotNull
    private Map<String, CompletionCandidate> importCandidates(@NotNull PsiFile templateFile,
                                                              @NotNull String prefix,
                                                              boolean includeCallables) {
        KteKotlinImportResolver importResolver = new KteKotlinImportResolver(templateFile);
        Map<String, CompletionCandidate> result = new LinkedHashMap<>();

        addExplicitImportCandidates(result, importResolver, includeCallables);
        addPrefixImportCandidates(result, importResolver, prefix, includeCallables);

        return result;
    }

    private void addSimpleReceiverCandidates(@NotNull Map<String, CompletionCandidate> result,
                                             @NotNull PsiFile templateFile,
                                             int hostOffset) {
        ReceiverAccess receiverAccess = receiverAccess(templateFile.getText(), hostOffset);
        if (receiverAccess == null) {
            return;
        }

        String receiverText = receiverText(templateFile.getText(), receiverAccess.receiverEndOffset());
        if (receiverText == null) {
            return;
        }

        addParameterTypeMembers(result, templateFile, receiverText);
        addKotlinObjectMembers(result, templateFile, receiverText);
    }

    private void addParameterTypeMembers(@NotNull Map<String, CompletionCandidate> result,
                                         @NotNull PsiFile templateFile,
                                         @NotNull String receiverText) {
        if (receiverText.contains(".")) {
            return;
        }

        KteTemplateSignatureService.Parameter parameter =
                KteTemplateSignatureService.resolve(templateFile).parameter(receiverText);
        if (parameter == null || parameter.typeClass() == null) {
            return;
        }

        addPsiClassMembers(result, parameter.typeClass());
    }

    private void addKotlinObjectMembers(@NotNull Map<String, CompletionCandidate> result,
                                        @NotNull PsiFile templateFile,
                                        @NotNull String receiverText) {
        KtClassOrObject classOrObject = resolveKotlinClassOrObject(templateFile, receiverText);
        if (classOrObject == null) {
            return;
        }

        addKtClassOrObjectMembers(result, classOrObject);
        KtClassOrObject companion = classOrObject.getCompanionObjects().isEmpty()
                ? null
                : classOrObject.getCompanionObjects().getFirst();
        if (companion != null) {
            addKtClassOrObjectMembers(result, companion);
        }
    }

    @Nullable
    private KtClassOrObject resolveKotlinClassOrObject(@NotNull PsiFile templateFile,
                                                       @NotNull String receiverText) {
        String[] parts = receiverText.split("\\.");
        if (parts.length == 0) {
            return null;
        }

        KteKotlinImportResolver importResolver = new KteKotlinImportResolver(templateFile);
        KtClassOrObject current = null;
        for (KteKotlinImportResolver.ImportCandidate candidate : importResolver.importCandidates(parts[0], false)) {
            PsiElement navigationElement = importResolver.navigationElement(candidate.element());
            if (navigationElement instanceof KtClassOrObject classOrObject) {
                current = classOrObject;
                break;
            }
        }
        if (current == null) {
            return null;
        }

        for (int index = 1; index < parts.length; index++) {
            current = nestedClassOrObject(current, parts[index]);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @Nullable
    private KtClassOrObject nestedClassOrObject(@NotNull KtClassOrObject parent, @NotNull String name) {
        for (KtDeclaration declaration : parent.getDeclarations()) {
            if (declaration instanceof KtClassOrObject classOrObject && name.equals(classOrObject.getName())) {
                return classOrObject;
            }
        }
        return null;
    }

    private void addKtClassOrObjectMembers(@NotNull Map<String, CompletionCandidate> result,
                                           @NotNull KtClassOrObject classOrObject) {
        for (KtDeclaration declaration : classOrObject.getDeclarations()) {
            if (declaration instanceof KtNamedDeclaration namedDeclaration && namedDeclaration.getName() != null) {
                addCandidate(result, new CompletionCandidate(
                        namedDeclaration.getName(),
                        null,
                        declaration instanceof KtNamedFunction function && function.getValueParameters().isEmpty(),
                        declaration
                ));
            }
        }
    }

    private void addPsiClassMembers(@NotNull Map<String, CompletionCandidate> result,
                                    @NotNull PsiClass psiClass) {
        for (PsiField field : psiClass.getAllFields()) {
            addCandidate(result, new CompletionCandidate(field.getName(), null, false, field));
        }
        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            if (innerClass.getName() != null) {
                addCandidate(result, new CompletionCandidate(innerClass.getName(), null, false, innerClass));
            }
        }
        for (PsiMethod method : psiClass.getAllMethods()) {
            addBeanPropertyCandidate(result, method);
        }
    }

    private void addBeanPropertyCandidate(@NotNull Map<String, CompletionCandidate> result,
                                          @NotNull PsiMethod method) {
        if (method.getParameterList().getParametersCount() != 0) {
            return;
        }

        String propertyName = propertyName(method.getName());
        if (propertyName != null) {
            addCandidate(result, new CompletionCandidate(propertyName, null, false, method));
        }
    }

    @Nullable
    private String propertyName(@NotNull String methodName) {
        if (methodName.startsWith("get") && methodName.length() > "get".length()) {
            return decapitalize(methodName.substring("get".length()));
        }
        if (methodName.startsWith("is") && methodName.length() > "is".length()) {
            return decapitalize(methodName.substring("is".length()));
        }
        return null;
    }

    @NotNull
    private String decapitalize(@NotNull String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private void addImportedVisibleNameCandidates(@NotNull Map<String, CompletionCandidate> result,
                                                  @NotNull PsiFile templateFile) {
        KteKotlinImportResolver importResolver = new KteKotlinImportResolver(templateFile);
        for (KteKotlinImportResolver.ImportInfo importInfo : importResolver.imports()) {
            if (!importInfo.star()) {
                addCandidate(result, new CompletionCandidate(importInfo.visibleName(), null, false, null));
            }
        }
    }

    @Nullable
    private ReceiverAccess receiverAccess(@NotNull String text, int offset) {
        int insertionStart = KteCompletionLookupPolicy.identifierStart(text, offset);
        int before = skipWhitespaceBackward(text, insertionStart);
        if (before <= 0 || text.charAt(before - 1) != '.') {
            return null;
        }

        int dotOffset = before - 1;
        int receiverEnd = dotOffset > 0 && text.charAt(dotOffset - 1) == '?' ? dotOffset - 1 : dotOffset;
        return receiverEnd <= 0 ? null : new ReceiverAccess(receiverEnd);
    }

    @Nullable
    private String receiverText(@NotNull String text, int receiverEndOffset) {
        int index = Math.clamp(receiverEndOffset, 0, text.length());
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }

        int end = index;
        while (index > 0) {
            char current = text.charAt(index - 1);
            if (Character.isJavaIdentifierPart(current) || current == '.') {
                index--;
            } else {
                break;
            }
        }

        if (index == end) {
            return null;
        }

        String result = text.substring(index, end);
        return result.endsWith(".") || result.startsWith(".") || result.contains("..") ? null : result;
    }

    private void addExplicitImportCandidates(@NotNull Map<String, CompletionCandidate> result,
                                             @NotNull KteKotlinImportResolver importResolver,
                                             boolean includeCallables) {
        for (KteKotlinImportResolver.ImportInfo importInfo : importResolver.imports()) {
            if (importInfo.star()) {
                continue;
            }

            KteKotlinImportResolver.ImportCandidate resolvedCandidate =
                    resolveImport(importResolver, importInfo, includeCallables);
            PsiElement element = resolvedCandidate == null ? null : resolvedCandidate.element();
            if (!includeCallables && !isClassLike(element)) {
                continue;
            }

            addCandidate(result, new CompletionCandidate(
                    importInfo.visibleName(),
                    null,
                    includeCallables && isNoArgFunction(element),
                    element
            ));
        }
    }

    @Nullable
    private KteKotlinImportResolver.ImportCandidate resolveImport(@NotNull KteKotlinImportResolver importResolver,
                                                                  @NotNull KteKotlinImportResolver.ImportInfo importInfo,
                                                                  boolean includeCallables) {
        for (KteKotlinImportResolver.ImportCandidate candidate : importResolver.importCandidates(importInfo.visibleName(), includeCallables)) {
            if (candidate.qualifiedName().equals(importInfo.qualifiedName())) {
                return candidate;
            }
        }
        return null;
    }

    private void addPrefixImportCandidates(@NotNull Map<String, CompletionCandidate> result,
                                           @NotNull KteKotlinImportResolver importResolver,
                                           @NotNull String prefix,
                                           boolean includeCallables) {
        for (KteKotlinImportResolver.ImportCandidate candidate : importResolver.importCandidatesByPrefix(prefix, includeCallables)) {
            String lookupString = KteKotlinTypeText.shortName(candidate.qualifiedName());
            String importToAdd = isAlreadyImported(importResolver, candidate) ? null : candidate.qualifiedName();
            addCandidate(result, new CompletionCandidate(
                    lookupString,
                    importToAdd,
                    includeCallables && isNoArgFunction(candidate.element()),
                    candidate.element()
            ));
        }
    }

    private boolean isAlreadyImported(@NotNull KteKotlinImportResolver importResolver,
                                      @NotNull KteKotlinImportResolver.ImportCandidate candidate) {
        for (KteKotlinImportResolver.ImportInfo importInfo : importResolver.imports()) {
            if (!importInfo.star() && importInfo.qualifiedName().equals(candidate.qualifiedName())) {
                return true;
            }
            if (importInfo.star() && candidate.qualifiedName().startsWith(importInfo.packageName() + ".")) {
                return true;
            }
        }
        return false;
    }

    private void addBuiltinTypeCandidates(@NotNull Map<String, CompletionCandidate> result) {
        for (String typeName : List.of(
                "Any",
                "Boolean",
                "Byte",
                "Char",
                "Double",
                "Float",
                "Int",
                "Long",
                "Short",
                "String",
                "Unit",
                "List",
                "MutableList",
                "Set",
                "MutableSet",
                "Map",
                "MutableMap"
        )) {
            addCandidate(result, new CompletionCandidate(typeName, null, false, null));
        }
    }

    private void addCandidate(@NotNull Map<String, CompletionCandidate> result,
                              @NotNull CompletionCandidate candidate) {
        if (candidate.lookupString().isBlank() ||
                KteCompletionLookupPolicy.isGeneratedComponentFunction(candidate.lookupString()) ||
                KteSyntheticKotlinGeneratedNames.isGeneratedLookup(candidate.lookupString()) ||
                "Companion".equals(candidate.lookupString())) {
            return;
        }

        CompletionCandidate existing = result.get(candidate.lookupString());
        if (existing == null || existing.importToAdd() != null && candidate.importToAdd() == null) {
            result.put(candidate.lookupString(), candidate);
        }
    }

    @NotNull
    private LookupElement lookupElement(@NotNull CompletionCandidate candidate) {
        LookupElementBuilder builder = candidate.psiElement() == null
                ? LookupElementBuilder.create(candidate.lookupString())
                : LookupElementBuilder.createWithSmartPointer(candidate.lookupString(), candidate.psiElement());
        if (candidate.importToAdd() != null) {
            builder = builder.withTypeText(candidate.importToAdd(), true);
        }
        if (candidate.insertCallParentheses()) {
            builder = builder.withTailText("()", true);
        }

        return builder.withInsertHandler((context, item) -> handleInsert(context, candidate));
    }

    private void handleInsert(@NotNull InsertionContext context,
                              @NotNull CompletionCandidate candidate) {
        Document document = context.getDocument();
        int caretOffset = context.getTailOffset();
        if (candidate.insertCallParentheses() && !startsWith(document, caretOffset, "(")) {
            document.insertString(caretOffset, "()");
            caretOffset += 2;
            context.setTailOffset(caretOffset);
        }

        int importShift = addImportIfRequired(context, candidate, caretOffset);
        caretOffset += importShift;
        context.commitDocument();
        context.getEditor().getCaretModel().moveToOffset(Math.clamp(caretOffset, 0, document.getTextLength()));
    }

    private int addImportIfRequired(@NotNull InsertionContext context,
                                    @NotNull CompletionCandidate candidate,
                                    int caretOffset) {
        if (candidate.importToAdd() == null) {
            return 0;
        }

        PsiFile file = insertionKteFile(context);
        if (file == null) {
            return 0;
        }

        Document document = context.getDocument();
        Document importDocument = PsiDocumentManager.getInstance(context.getProject()).getDocument(file);
        Document measuredDocument = importDocument == null || document instanceof DocumentWindow ? document : importDocument;
        int beforeLength = measuredDocument.getTextLength();
        String beforeText = measuredDocument.getText();
        KteNativeTemplateSourceEditUtil.addImport(file, candidate.importToAdd());
        if (importDocument != null) {
            PsiDocumentManager.getInstance(context.getProject()).commitDocument(importDocument);
        }
        if (document instanceof DocumentWindow) {
            return 0;
        }

        int lengthDelta = measuredDocument.getTextLength() - beforeLength;
        if (lengthDelta <= 0) {
            return 0;
        }

        int insertionOffset = firstDifferenceOffset(beforeText, measuredDocument.getText());
        return insertionOffset <= caretOffset ? lengthDelta : 0;
    }

    @Nullable
    private PsiFile insertionKteFile(@NotNull InsertionContext context) {
        PsiFile file = context.getFile().getOriginalFile();
        if (KteNativeTemplateSourceEditUtil.isKteFile(file)) {
            return file;
        }

        file = context.getFile();
        if (KteNativeTemplateSourceEditUtil.isKteFile(file)) {
            return file;
        }

        PsiFile topLevelFile = InjectedLanguageManager.getInstance(context.getProject()).getTopLevelFile(file);
        if (KteNativeTemplateSourceEditUtil.isKteFile(topLevelFile)) {
            return topLevelFile;
        }

        PsiFile ktePsi = topLevelFile.getViewProvider().getPsi(KteLanguage.INSTANCE);
        return ktePsi instanceof KtePsiFile ? ktePsi : null;
    }

    private boolean isClassLike(@Nullable PsiElement element) {
        PsiElement navigationElement = navigationElement(element);
        return navigationElement instanceof KtClassOrObject ||
                navigationElement instanceof com.intellij.psi.PsiClass;
    }

    private boolean isNoArgFunction(@Nullable PsiElement element) {
        PsiElement navigationElement = navigationElement(element);
        return navigationElement instanceof KtNamedFunction function && function.getValueParameters().isEmpty();
    }

    @Nullable
    private PsiElement navigationElement(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }

        PsiElement navigationElement = element.getNavigationElement();
        return navigationElement == null ? element : navigationElement;
    }

    private static boolean startsWith(@NotNull Document document, int offset, @NotNull String text) {
        int safeOffset = Math.clamp(offset, 0, document.getTextLength());
        return document.getText().startsWith(text, safeOffset);
    }

    private static int firstDifferenceOffset(@NotNull String before, @NotNull String after) {
        int max = Math.min(before.length(), after.length());
        int index = 0;
        while (index < max && before.charAt(index) == after.charAt(index)) {
            index++;
        }
        return index;
    }

    private static int skipWhitespaceBackward(@NotNull CharSequence text, int offset) {
        int index = Math.clamp(offset, 0, text.length());
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private record CompletionCandidate(@NotNull String lookupString,
                                       @Nullable String importToAdd,
                                       boolean insertCallParentheses,
                                       @Nullable PsiElement psiElement) {
    }

    private record ReceiverAccess(int receiverEndOffset) {
    }
}
