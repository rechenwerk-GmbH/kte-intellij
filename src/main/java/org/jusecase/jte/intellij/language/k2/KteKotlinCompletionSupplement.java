package org.jusecase.jte.intellij.language.k2;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.api.AnalyzeKt;
import org.jetbrains.kotlin.analysis.api.KaSession;
import org.jetbrains.kotlin.analysis.api.scopes.KaScope;
import org.jetbrains.kotlin.analysis.api.scopes.KaTypeScope;
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature;
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol;
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol;
import org.jetbrains.kotlin.analysis.api.types.KaType;
import org.jetbrains.kotlin.idea.references.KtReference;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jusecase.jte.intellij.language.KteLanguage;
import org.jusecase.jte.intellij.language.refactoring.KteNativeTemplateSourceEditUtil;
import org.jusecase.jte.intellij.language.psi.KtePsiFile;
import org.jusecase.jte.intellij.language.template.KteKotlinTypeText;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KteKotlinCompletionSupplement {
    public void addExpressionCompletions(@NotNull CompletionResultSet result,
                                         @NotNull PsiFile templateFile,
                                         int hostOffset) {
        String prefix = KteCompletionLookupPolicy.completionIdentifierPrefix(templateFile.getText(), hostOffset);

        CompletionResultSet prefixedResult = result.withPrefixMatcher(prefix);
        Map<String, CompletionCandidate> candidates = new LinkedHashMap<>();
        if (receiverAccess(templateFile.getText(), hostOffset) != null) {
            addReceiverCandidates(candidates, templateFile, hostOffset);
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

    private void addReceiverCandidates(@NotNull Map<String, CompletionCandidate> result,
                                       @NotNull PsiFile templateFile,
                                       int hostOffset) {
        try {
            KteSyntheticKotlinModel model =
                    KteSyntheticKotlinModelService.getInstance(templateFile.getProject()).getCompletionModel(templateFile, hostOffset);
            Integer kotlinOffset = model.getSyntheticFile().mapTemplateOffsetToKotlin(hostOffset);
            if (kotlinOffset == null) {
                return;
            }

            ReceiverAccess receiverAccess = receiverAccess(model.getSyntheticFile().getText(), kotlinOffset);
            if (receiverAccess == null) {
                return;
            }

            AnalyzeKt.analyze(model.getKtFile(), session -> {
                KtExpression receiverExpression = receiverExpression(model.getKtFile(), receiverAccess.receiverEndOffset());
                if (receiverExpression != null) {
                    KaType receiverType = session.getExpressionType(receiverExpression);
                    if (receiverType != null) {
                        KaTypeScope syntheticJavaPropertiesScope = session.getSyntheticJavaPropertiesScope(receiverType);
                        Set<String> syntheticJavaAccessors = syntheticJavaPropertyAccessorNames(syntheticJavaPropertiesScope);
                        addTypeScopeCandidates(session, session.getScope(receiverType), syntheticJavaAccessors, result);
                        addTypeScopeCandidates(session, syntheticJavaPropertiesScope, Set.of(), result);
                    }
                }

                KaClassSymbol classSymbol = referencedClassSymbol(session, model.getKtFile(), receiverAccess.receiverEndOffset());
                if (classSymbol != null) {
                    addScopeCandidates(session, session.getStaticMemberScope(classSymbol), result);
                    addScopeCandidates(session, session.getMemberScope(classSymbol), result);
                    if (classSymbol instanceof KaNamedClassSymbol namedClassSymbol &&
                            namedClassSymbol.getCompanionObject() != null) {
                        addScopeCandidates(session, session.getMemberScope(namedClassSymbol.getCompanionObject()), result);
                    }
                }
                return null;
            });
        } catch (ProcessCanceledException exception) {
            throw exception;
        }
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

    private void addScopeCandidates(@NotNull KaSession session,
                                    @NotNull KaScope scope,
                                    @NotNull Map<String, CompletionCandidate> result) {
        for (KaCallableSymbol symbol : iterable(scope.callables(name -> true))) {
            addCallableCandidate(symbol, Set.of(), result);
        }
        for (KaClassifierSymbol symbol : iterable(scope.classifiers(name -> true))) {
            addClassifierCandidate(symbol, result);
        }
    }

    private void addTypeScopeCandidates(@NotNull KaSession session,
                                        @NotNull KaTypeScope scope,
                                        @NotNull Set<String> hiddenLookupStrings,
                                        @NotNull Map<String, CompletionCandidate> result) {
        for (KaCallableSignature<?> signature : iterable(scope.getCallableSignatures(name -> true))) {
            addCallableCandidate(signature.getSymbol(), hiddenLookupStrings, result);
        }
        for (KaClassifierSymbol symbol : iterable(scope.getClassifierSymbols(name -> true))) {
            addClassifierCandidate(symbol, result);
        }
    }

    private void addCallableCandidate(@NotNull KaCallableSymbol symbol,
                                      @NotNull Set<String> hiddenLookupStrings,
                                      @NotNull Map<String, CompletionCandidate> result) {
        if (!(symbol instanceof KaNamedSymbol namedSymbol)) {
            return;
        }

        String lookupString = namedSymbol.getName().asString();
        if (hiddenLookupStrings.contains(lookupString)) {
            return;
        }

        addCandidate(result, new CompletionCandidate(
                lookupString,
                null,
                false,
                symbol.getPsi()
        ));
    }

    private void addClassifierCandidate(@NotNull KaClassifierSymbol symbol,
                                        @NotNull Map<String, CompletionCandidate> result) {
        Name name = symbol.getName();
        if (name.isSpecial()) {
            return;
        }

        addCandidate(result, new CompletionCandidate(
                name.asString(),
                null,
                false,
                symbol.getPsi()
        ));
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
    private KtExpression receiverExpression(@NotNull KtFile ktFile, int receiverEndOffset) {
        PsiElement leaf = ktFile.findElementAt(Math.max(0, receiverEndOffset - 1));
        KtExpression best = null;
        for (PsiElement current = leaf; current != null && current != ktFile; current = current.getParent()) {
            if (current instanceof KtExpression expression &&
                    expression.getTextRange().getEndOffset() <= receiverEndOffset) {
                best = expression;
            }
        }

        return best;
    }

    @Nullable
    private KaClassSymbol referencedClassSymbol(@NotNull KaSession session,
                                                @NotNull KtFile ktFile,
                                                int receiverEndOffset) {
        PsiReference reference = KteSyntheticKotlinSemanticService.findSyntheticReference(
                ktFile,
                Math.max(0, receiverEndOffset - 1)
        );
        if (!(reference instanceof KtReference ktReference)) {
            return null;
        }

        Collection<KaSymbol> symbols = session.resolveToSymbols(ktReference);
        for (KaSymbol symbol : symbols) {
            if (symbol instanceof KaClassSymbol classSymbol) {
                return classSymbol;
            }
        }
        return null;
    }

    @NotNull
    private Set<String> syntheticJavaPropertyAccessorNames(@NotNull KaTypeScope scope) {
        Set<String> result = new HashSet<>();
        for (KaCallableSignature<?> signature : iterable(scope.getCallableSignatures(name -> true))) {
            if (signature.getSymbol() instanceof KaSyntheticJavaPropertySymbol syntheticJavaPropertySymbol) {
                addNamedFunctionName(result, syntheticJavaPropertySymbol.getJavaGetterSymbol());
                addNamedFunctionName(result, syntheticJavaPropertySymbol.getJavaSetterSymbol());
            }
        }
        return result;
    }

    private void addNamedFunctionName(@NotNull Set<String> result, @Nullable KaNamedFunctionSymbol symbol) {
        if (symbol != null) {
            result.add(symbol.getName().asString());
        }
    }

    @NotNull
    private static <T> Iterable<T> iterable(@NotNull kotlin.sequences.Sequence<T> sequence) {
        return sequence::iterator;
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
