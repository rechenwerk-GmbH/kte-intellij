package org.jusecase.jte.intellij.language.k2;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.api.AnalyzeKt;
import org.jetbrains.kotlin.analysis.api.KaSession;
import org.jetbrains.kotlin.analysis.api.components.KaRendererKt;
import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKind;
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource;
import org.jetbrains.kotlin.analysis.api.scopes.KaScope;
import org.jetbrains.kotlin.analysis.api.scopes.KaTypeScope;
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature;
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature;
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature;
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind;
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation;
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin;
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol;
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol;
import org.jetbrains.kotlin.analysis.api.types.KaType;
import org.jetbrains.kotlin.idea.KotlinIcons;
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider;
import org.jetbrains.kotlin.idea.references.KtReference;
import org.jetbrains.kotlin.name.CallableId;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.types.Variance;
import org.jusecase.jte.intellij.language.KteLanguage;
import org.jusecase.jte.intellij.language.refactoring.KteNativeTemplateSourceEditUtil;
import org.jusecase.jte.intellij.language.template.KteKotlinTypeText;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KtePublicKotlinCompletionBridge {
    public boolean complete(@NotNull CompletionResultSet result,
                            @NotNull PsiFile templateFile,
                            int hostOffset) {
        KteCompletionContext context = createContext(templateFile, hostOffset);
        if (context == null) {
            return false;
        }

        String prefix = identifierPrefix(templateFile.getText(), hostOffset);
        CompletionResultSet prefixedResult = result.withPrefixMatcher(prefix);
        List<CompletionCandidate> candidates = collectCandidates(context, prefix);
        for (CompletionCandidate candidate : candidates) {
            prefixedResult.addElement(lookupElement(candidate));
        }
        return !candidates.isEmpty();
    }

    @Nullable
    private KteCompletionContext createContext(@NotNull PsiFile templateFile, int hostOffset) {
        KteSyntheticKotlinModel model =
                KteSyntheticKotlinModelService.getInstance(templateFile.getProject()).getCompletionModel(templateFile, hostOffset);
        Integer kotlinOffset = model.getSyntheticFile().mapTemplateOffsetToKotlin(hostOffset);
        if (kotlinOffset == null) {
            return null;
        }

        return new KteCompletionContext(templateFile, model, kotlinOffset);
    }

    @NotNull
    private List<CompletionCandidate> collectCandidates(@NotNull KteCompletionContext context, @NotNull String prefix) {
        try {
            return AnalyzeKt.analyze(context.model().getKtFile(), session -> {
                LinkedHashMap<String, CompletionCandidate> result = new LinkedHashMap<>();
                ReceiverAccess receiverAccess = receiverAccess(context);
                if (receiverAccess != null) {
                    addReceiverCandidates(session, context, receiverAccess, result);
                } else {
                    addScopeCandidates(session, context, result);
                    addAutoImportCandidates(session, context, prefix, result);
                    addBuiltinTypeCandidates(result);
                    addKeywordCandidates(result);
                }

                return new ArrayList<>(result.values());
            });
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            if (com.intellij.openapi.application.ApplicationManager.getApplication().isUnitTestMode()) {
                throw exception;
            }
            return List.of();
        }
    }

    private void addReceiverCandidates(@NotNull KaSession session,
                                       @NotNull KteCompletionContext context,
                                       @NotNull ReceiverAccess receiverAccess,
                                       @NotNull Map<String, CompletionCandidate> result) {
        KtExpression receiverExpression = receiverExpression(context.model().getKtFile(), receiverAccess.receiverEndOffset());
        if (receiverExpression != null) {
            KaType receiverType = session.getExpressionType(receiverExpression);
            if (receiverType != null) {
                KaTypeScope syntheticJavaPropertiesScope = session.getSyntheticJavaPropertiesScope(receiverType);
                Set<String> syntheticJavaAccessors = syntheticJavaPropertyAccessorNames(syntheticJavaPropertiesScope);
                addTypeScopeCandidates(session, session.getScope(receiverType), false, syntheticJavaAccessors, result);
                addTypeScopeCandidates(session, syntheticJavaPropertiesScope, false, Set.of(), result);
            }
        }

        KaClassSymbol classSymbol = referencedClassSymbol(session, context.model().getKtFile(), receiverAccess.receiverEndOffset());
        if (classSymbol != null) {
            addScopeCandidates(session, session.getStaticMemberScope(classSymbol), false, result);
            addScopeCandidates(session, session.getMemberScope(classSymbol), false, result);
            if (classSymbol instanceof KaNamedClassSymbol namedClassSymbol &&
                    namedClassSymbol.getCompanionObject() != null) {
                addScopeCandidates(session, session.getMemberScope(namedClassSymbol.getCompanionObject()), false, result);
            }
        }

        addImportedVisibleNames(context.templateFile(), result);
    }

    private void addScopeCandidates(@NotNull KaSession session,
                                    @NotNull KteCompletionContext context,
                                    @NotNull Map<String, CompletionCandidate> result) {
        KtElement position = ktElementAt(context.model().getKtFile(), context.kotlinOffset());
        if (position == null) {
            return;
        }

        for (KaScopeWithKind scopeWithKind : session.scopeContext(context.model().getKtFile(), position).getScopes()) {
            addScopeCandidates(session, scopeWithKind.getScope(), true, result);
        }
    }

    private void addScopeCandidates(@NotNull KaSession session,
                                    @NotNull KaScope scope,
                                    boolean callNoArgFunctions,
                                    @NotNull Map<String, CompletionCandidate> result) {
        addScopeCandidates(session, scope, callNoArgFunctions, Set.of(), result);
    }

    private void addScopeCandidates(@NotNull KaSession session,
                                    @NotNull KaScope scope,
                                    boolean callNoArgFunctions,
                                    @NotNull Set<String> hiddenLookupStrings,
                                    @NotNull Map<String, CompletionCandidate> result) {
        for (KaCallableSymbol symbol : iterable(scope.callables(name -> true))) {
            addCallableCandidate(session, symbol, null, callNoArgFunctions, hiddenLookupStrings, result);
        }
        for (KaClassifierSymbol symbol : iterable(scope.classifiers(name -> true))) {
            addClassifierCandidate(symbol, null, result);
        }
    }

    private void addTypeScopeCandidates(@NotNull KaSession session,
                                        @NotNull KaTypeScope scope,
                                        boolean callNoArgFunctions,
                                        @NotNull Set<String> hiddenLookupStrings,
                                        @NotNull Map<String, CompletionCandidate> result) {
        for (KaCallableSignature<?> signature : iterable(scope.getCallableSignatures(name -> true))) {
            addCallableCandidate(session, signature, null, callNoArgFunctions, hiddenLookupStrings, result);
        }
        for (KaClassifierSymbol symbol : iterable(scope.getClassifierSymbols(name -> true))) {
            addClassifierCandidate(symbol, null, result);
        }
    }

    private void addImportedVisibleNames(@NotNull PsiFile templateFile,
                                         @NotNull Map<String, CompletionCandidate> result) {
        KteKotlinImportResolver resolver = new KteKotlinImportResolver(templateFile.getOriginalFile());
        for (KteKotlinImportResolver.ImportInfo importInfo : resolver.imports()) {
            if (!importInfo.star()) {
                addCandidate(result, new CompletionCandidate(importInfo.visibleName(), null, false, null, null, null, null));
            }
        }
    }

    private void addAutoImportCandidates(@NotNull KaSession session,
                                         @NotNull KteCompletionContext context,
                                         @NotNull String prefix,
                                         @NotNull Map<String, CompletionCandidate> result) {
        String candidatePrefix = KteKotlinTypeText.shortName(prefix);
        if (candidatePrefix.isBlank()) {
            return;
        }

        GlobalSearchScope scope = KteSyntheticKotlinAnalysisContextService.getInstance(context.templateFile().getProject())
                .resolveSearchScope(context.templateFile());
        KtSymbolFromIndexProvider provider = new KtSymbolFromIndexProvider(context.model().getKtFile());
        kotlin.jvm.functions.Function1<Name, Boolean> nameFilter =
                name -> !name.isSpecial() && name.asString().startsWith(candidatePrefix);

        for (KaClassLikeSymbol symbol : iterable(provider.getKotlinClassesByNameFilter(
                session,
                nameFilter,
                scope,
                declaration -> true
        ))) {
            String importToAdd = classImportName(symbol);
            if (importToAdd != null) {
                addClassifierCandidate(symbol, importToAdd, result);
            }
        }

        for (KaNamedClassSymbol symbol : iterable(provider.getJavaClassesByNameFilter(
                session,
                nameFilter,
                scope,
                psiClass -> true
        ))) {
            String importToAdd = classImportName(symbol);
            if (importToAdd != null) {
                addClassifierCandidate(symbol, importToAdd, result);
            }
        }

        for (KaCallableSymbol symbol : iterable(provider.getTopLevelCallableSymbolsByNameFilterIncludingExtensions(
                session,
                nameFilter,
                scope,
                declaration -> true
        ))) {
            String importToAdd = callableImportName(symbol);
            if (importToAdd != null) {
                addCallableCandidate(session, symbol, importToAdd, true, Set.of(), result);
            }
        }
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
            addCandidate(result, new CompletionCandidate(typeName, null, false, null, null, null, KotlinIcons.CLASS));
        }
    }

    private void addKeywordCandidates(@NotNull Map<String, CompletionCandidate> result) {
        for (String keyword : List.of(
                "true",
                "false",
                "null",
                "if",
                "else",
                "when",
                "for",
                "while",
                "return"
        )) {
            addCandidate(result, new CompletionCandidate(keyword, null, false, null, null, null, null));
        }
    }

    private void addCallableCandidate(@NotNull KaSession session,
                                      @NotNull KaCallableSignature<?> signature,
                                      @Nullable String importToAdd,
                                      boolean callNoArgFunctions,
                                      @NotNull Set<String> hiddenLookupStrings,
                                      @NotNull Map<String, CompletionCandidate> result) {
        KaCallableSymbol symbol = signature.getSymbol();
        if (!(symbol instanceof KaNamedSymbol namedSymbol)) {
            return;
        }

        String lookupString = namedSymbol.getName().asString();
        if (hiddenLookupStrings.contains(lookupString) ||
                KteCompletionLookupPolicy.isGeneratedSourceMemberComponentFunction(lookupString, symbol)) {
            return;
        }

        boolean noArgFunction = callNoArgFunctions &&
                signature instanceof KaFunctionSignature<?> functionSignature &&
                functionSignature.getValueParameters().isEmpty();
        addCandidate(result, new CompletionCandidate(
                lookupString,
                importToAdd,
                noArgFunction,
                symbol.getPsi(),
                callableTailText(session, signature),
                renderType(session, signature.getReturnType()),
                callableIcon(symbol)
        ));
    }

    private void addCallableCandidate(@NotNull KaSession session,
                                      @NotNull KaCallableSymbol symbol,
                                      @Nullable String importToAdd,
                                      boolean callNoArgFunctions,
                                      @NotNull Set<String> hiddenLookupStrings,
                                      @NotNull Map<String, CompletionCandidate> result) {
        if (!(symbol instanceof KaNamedSymbol namedSymbol)) {
            return;
        }

        String lookupString = namedSymbol.getName().asString();
        if (hiddenLookupStrings.contains(lookupString) ||
                KteCompletionLookupPolicy.isGeneratedSourceMemberComponentFunction(lookupString, symbol)) {
            return;
        }

        boolean noArgFunction = callNoArgFunctions &&
                symbol instanceof KaFunctionSymbol functionSymbol &&
                functionSymbol.getValueParameters().isEmpty();
        addCandidate(result, new CompletionCandidate(
                lookupString,
                importToAdd,
                noArgFunction,
                symbol.getPsi(),
                callableTailText(session, symbol),
                renderType(session, symbol.getReturnType()),
                callableIcon(symbol)
        ));
    }

    private void addClassifierCandidate(@NotNull KaClassifierSymbol symbol,
                                        @Nullable String importToAdd,
                                        @NotNull Map<String, CompletionCandidate> result) {
        String lookupString = symbol.getName().asString();
        addCandidate(result, new CompletionCandidate(
                lookupString,
                importToAdd,
                false,
                symbol.getPsi(),
                importToAdd == null ? null : " (" + packageName(importToAdd) + ")",
                null,
                classifierIcon(symbol)
        ));
    }

    private void addCandidate(@NotNull Map<String, CompletionCandidate> result,
                              @NotNull CompletionCandidate candidate) {
        if (candidate.lookupString().isBlank() ||
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
        if (candidate.icon() != null) {
            builder = builder.withIcon(candidate.icon());
        }
        if (candidate.typeText() != null) {
            builder = builder.withTypeText(candidate.typeText(), true);
        } else if (candidate.importToAdd() != null) {
            builder = builder.withTypeText(candidate.importToAdd(), true);
        }
        if (candidate.tailText() != null) {
            builder = builder.withTailText(candidate.tailText(), true);
        } else if (candidate.insertCallParentheses()) {
            builder = builder.withTailText("()", true);
        }

        return builder.withInsertHandler((context, item) -> handleInsert(context, candidate));
    }

    private void handleInsert(@NotNull com.intellij.codeInsight.completion.InsertionContext context,
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

    private int addImportIfRequired(@NotNull com.intellij.codeInsight.completion.InsertionContext context,
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
    private PsiFile insertionKteFile(@NotNull com.intellij.codeInsight.completion.InsertionContext context) {
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
        return KteNativeTemplateSourceEditUtil.isKteFile(ktePsi) ? ktePsi : null;
    }

    @Nullable
    private ReceiverAccess receiverAccess(@NotNull KteCompletionContext context) {
        String text = context.model().getSyntheticFile().getText();
        int insertionStart = identifierStart(text, context.kotlinOffset());
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

    @Nullable
    private KtElement ktElementAt(@NotNull KtFile ktFile, int kotlinOffset) {
        PsiElement element = ktFile.findElementAt(Math.clamp(kotlinOffset, 0, Math.max(0, ktFile.getTextLength() - 1)));
        if (element instanceof KtElement ktElement) {
            return ktElement;
        }
        return PsiTreeUtil.getParentOfType(element, KtElement.class, false);
    }

    @NotNull
    private static <T> Iterable<T> iterable(@NotNull kotlin.sequences.Sequence<T> sequence) {
        return sequence::iterator;
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

    @Nullable
    private String classImportName(@NotNull KaClassLikeSymbol symbol) {
        ClassId classId = symbol.getClassId();
        return classId.isLocal() ? null : classId.asSingleFqName().asString();
    }

    @Nullable
    private String callableImportName(@NotNull KaCallableSymbol symbol) {
        CallableId callableId = symbol.getCallableId();
        if (callableId == null || callableId.isLocal()) {
            return null;
        }
        return callableId.asSingleFqName().asString();
    }

    @Nullable
    private String callableTailText(@NotNull KaSession session, @NotNull KaCallableSignature<?> signature) {
        if (signature instanceof KaFunctionSignature<?> functionSignature) {
            return functionTailText(
                    session,
                    functionSignature.getValueParameters()
                            .stream()
                            .map(ParameterPresentation::fromSignature)
                            .toList()
            );
        }
        return null;
    }

    @Nullable
    private String callableTailText(@NotNull KaSession session, @NotNull KaCallableSymbol symbol) {
        if (symbol instanceof KaFunctionSymbol functionSymbol) {
            return functionTailText(
                    session,
                    functionSymbol.getValueParameters()
                            .stream()
                            .map(ParameterPresentation::fromSymbol)
                            .toList()
            );
        }
        return null;
    }

    @NotNull
    private String functionTailText(@NotNull KaSession session, @NotNull List<ParameterPresentation> parameters) {
        if (parameters.isEmpty()) {
            return "()";
        }

        List<String> renderedParameters = new ArrayList<>();
        for (ParameterPresentation parameter : parameters) {
            StringBuilder rendered = new StringBuilder();
            if (parameter.vararg()) {
                rendered.append("vararg ");
            }
            if (parameter.name() != null && !parameter.name().isBlank()) {
                rendered.append(parameter.name()).append(": ");
            }
            rendered.append(renderType(session, parameter.type()));
            renderedParameters.add(rendered.toString());
        }
        return "(" + String.join(", ", renderedParameters) + ")";
    }

    @Nullable
    private String renderType(@NotNull KaSession session, @Nullable KaType type) {
        if (type == null) {
            return null;
        }
        return KaRendererKt.render(
                session,
                type,
                KaTypeRendererForSource.INSTANCE.getWITH_SHORT_NAMES(),
                Variance.INVARIANT
        );
    }

    @Nullable
    private Icon callableIcon(@NotNull KaCallableSymbol symbol) {
        if (symbol instanceof KaParameterSymbol) {
            return KotlinIcons.PARAMETER;
        }
        if (symbol instanceof KaNamedFunctionSymbol functionSymbol) {
            if (functionSymbol.isSuspend()) {
                return KotlinIcons.SUSPEND_FUNCTION;
            }
            return functionSymbol.isExtension() ? KotlinIcons.EXTENSION_FUNCTION : KotlinIcons.FUNCTION;
        }
        if (symbol instanceof KaVariableSymbol variableSymbol) {
            if (symbol instanceof KaLocalVariableSymbol || symbol.getLocation() == KaSymbolLocation.LOCAL) {
                return variableSymbol.isVal() ? KotlinIcons.VAL : KotlinIcons.VAR;
            }
            return variableSymbol.isVal() ? KotlinIcons.FIELD_VAL : KotlinIcons.FIELD_VAR;
        }
        return null;
    }

    @Nullable
    private Icon classifierIcon(@NotNull KaClassifierSymbol symbol) {
        if (symbol instanceof KaTypeAliasSymbol) {
            return KotlinIcons.TYPE_ALIAS;
        }
        if (symbol instanceof KaClassSymbol classSymbol) {
            KaClassKind classKind = classSymbol.getClassKind();
            return switch (classKind) {
                case ANNOTATION_CLASS -> KotlinIcons.ANNOTATION;
                case ENUM_CLASS -> KotlinIcons.ENUM;
                case INTERFACE -> KotlinIcons.INTERFACE;
                case OBJECT, COMPANION_OBJECT -> KotlinIcons.OBJECT;
                default -> KotlinIcons.CLASS;
            };
        }
        return KotlinIcons.CLASS;
    }

    @NotNull
    private String packageName(@NotNull String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
    }

    @NotNull
    private static String identifierPrefix(@NotNull String text, int offset) {
        return KteCompletionLookupPolicy.identifierPrefix(text, offset);
    }

    private static int identifierStart(@NotNull CharSequence text, int offset) {
        return KteCompletionLookupPolicy.identifierStart(text, offset);
    }

    private static int skipWhitespaceBackward(@NotNull CharSequence text, int offset) {
        int index = Math.clamp(offset, 0, text.length());
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static boolean startsWith(@NotNull Document document, int offset, @NotNull String text) {
        int safeOffset = Math.clamp(offset, 0, document.getTextLength());
        TextRange range = TextRange.create(safeOffset, Math.min(document.getTextLength(), safeOffset + text.length()));
        return text.contentEquals(document.getText(range));
    }

    private static int firstDifferenceOffset(@NotNull String before, @NotNull String after) {
        int max = Math.min(before.length(), after.length());
        int index = 0;
        while (index < max && before.charAt(index) == after.charAt(index)) {
            index++;
        }
        return index;
    }

    private record KteCompletionContext(@NotNull PsiFile templateFile,
                                        @NotNull KteSyntheticKotlinModel model,
                                        int kotlinOffset) {
    }

    private record ReceiverAccess(int receiverEndOffset) {
    }

    private record CompletionCandidate(@NotNull String lookupString,
                                       @Nullable String importToAdd,
                                       boolean insertCallParentheses,
                                       @Nullable PsiElement psiElement,
                                       @Nullable String tailText,
                                       @Nullable String typeText,
                                       @Nullable Icon icon) {
    }

    private record ParameterPresentation(@Nullable String name,
                                         @Nullable KaType type,
                                         boolean vararg) {
        private static ParameterPresentation fromSignature(@NotNull KaVariableSignature<KaValueParameterSymbol> signature) {
            KaValueParameterSymbol symbol = signature.getSymbol();
            Name name = signature.getName();
            return new ParameterPresentation(
                    name == null || name.isSpecial() ? null : name.asString(),
                    signature.getReturnType(),
                    symbol.isVararg()
            );
        }

        private static ParameterPresentation fromSymbol(@NotNull KaValueParameterSymbol symbol) {
            Name name = symbol.getName();
            return new ParameterPresentation(
                    name == null || name.isSpecial() ? null : name.asString(),
                    symbol.getReturnType(),
                    symbol.isVararg()
            );
        }
    }
}
