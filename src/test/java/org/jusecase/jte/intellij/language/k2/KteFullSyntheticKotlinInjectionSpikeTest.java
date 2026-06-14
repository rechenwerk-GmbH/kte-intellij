package org.jusecase.jte.intellij.language.k2;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Key;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.analysis.api.AnalyzeKt;
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.psi.KtCodeFragment;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFile;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.JtePsiOutput;
import org.jusecase.jte.intellij.language.psi.JtePsiParam;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KteFullSyntheticKotlinInjectionSpikeTest extends KteK2FixtureSupport {
    public void spikeFullSyntheticInjectionProducesKtFileAtMappedReceiverCaret() {
        registerFullSyntheticKotlinInjector();
        addProfileClassWithKotlinPropertiesInSourceRoot();

        PsiFile configuredFile = configureKteFile("src/main/jte/profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${profile.display<caret>Name}
                """);

        PsiElement injectedElement = injectedElementNearCaret(configuredFile);

        assertNotNull(injectedElement);
        assertTrue(injectedElement.getContainingFile() instanceof KtFile);
        assertTrue(injectedElement.getContainingFile().getText().contains("class DummyTemplate"));
        assertTrue(injectedElement.getContainingFile().getText().contains("profile.displayName"));
    }

    public void spikeFullSyntheticInjectionReachesKotlinAndDrivesNativeCompletion() {
        registerFullSyntheticKotlinInjector();
        addProfileClassWithKotlinPropertiesInSourceRoot();

        PsiFile configuredFile = configureKteFile("src/main/jte/profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${profile.<caret>}
                """);

        PsiElement injectedElement = injectedElementNearCaret(configuredFile);
        assertNotNull(injectedElement);
        assertTrue(injectedElement.getContainingFile() instanceof KtFile);

        LookupElement[] elements = myFixture.completeBasic();
        assertNotNull(elements);
        Set<String> lookupStrings = lookupStrings(elements);
        String debug = "lookups=" + lookupStrings +
                "\nreceiverType=" + receiverTypeDebug((KtFile) injectedElement.getContainingFile()) +
                "\ninjectedText=\n" + injectedElement.getContainingFile().getText();
        assertTrue(debug, lookupStrings.contains("displayName"));
        assertTrue(debug, lookupStrings.contains("email"));
    }

    public void spikeMinimalBlockContextInjectionCanUseNativeReceiverCompletion() {
        registerMinimalBlockContextKotlinInjector();
        addProfileClassWithKotlinPropertiesInSourceRoot();

        PsiFile configuredFile = configureKteFile("src/main/jte/profile.kte", """
                @import com.example.Profile
                @param profile: Profile
                ${profile.<caret>}
                """);

        PsiElement injectedElement = injectedElementNearCaret(configuredFile);
        assertNotNull(injectedElement);
        assertTrue(injectedElement.getContainingFile() instanceof KtFile);
        configureInjectedKotlinContext(configuredFile, injectedElement);

        LookupElement[] elements = myFixture.completeBasic();
        assertNotNull(elements);

        Set<String> lookupStrings = lookupStrings(elements);
        String receiverType = receiverTypeDebug((KtFile) injectedElement.getContainingFile());
        assertTrue("receiverType=" + receiverType + ", lookups=" + lookupStrings, lookupStrings.contains("displayName"));
        assertTrue("receiverType=" + receiverType + ", lookups=" + lookupStrings, lookupStrings.contains("email"));
    }

    public void testSpikeClassIsDocumentationOnly() {
        // Exploratory injector variants stay in this class for future research but must not run in the main suite.
    }

    private void registerFullSyntheticKotlinInjector() {
        InjectionDebug.reset();
        InjectedLanguageManager.getInstance(getProject())
                .registerMultiHostInjector(new FullSyntheticKotlinInjector(), getTestRootDisposable());
    }

    private void registerMinimalBlockContextKotlinInjector() {
        InjectionDebug.reset();
        InjectedLanguageManager.getInstance(getProject())
                .registerMultiHostInjector(new MinimalBlockContextKotlinInjector(), getTestRootDisposable());
    }

    private void addProfileClassWithKotlinPropertiesInSourceRoot() {
        myFixture.addFileToProject("src/main/kotlin/com/example/Profile.kt", """
                package com.example

                data class Profile(
                    val displayName: String,
                    val email: String,
                    val active: Boolean,
                    val tags: List<String>,
                    val manager: Profile? = null,
                )
                """);
    }

    @NotNull
    private PsiFile configureKteFile(@NotNull String path, @NotNull String text) {
        PsiFile file = (PsiFile) myFixture.addFileToProject(path, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    @NotNull
    private PsiElement injectedElementNearCaret(@NotNull PsiFile configuredFile) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        PsiFile kteFile = findKtePsiFile(configuredFile);
        assertNotNull(kteFile);
        KtePsiJavaContent host = PsiTreeUtil.findChildOfType(kteFile, KtePsiJavaContent.class);
        assertNotNull("No KTE root host in " + kteFile.getClass().getName() + ":\n" + kteFile.getText(), host);
        int hostCaretOffset = currentHostCaretOffset();

        List<Pair<PsiElement, TextRange>> injectedFiles = manager.getInjectedPsiFiles(host);
        assertNotNull(injectionDebugMessage(host, injectedFiles, hostCaretOffset), injectedFiles);
        assertFalse(injectionDebugMessage(host, injectedFiles, hostCaretOffset), injectedFiles.isEmpty());

        PsiElement injectedElement = findInjectedElementAt(manager, configuredFile, hostCaretOffset);
        if (injectedElement == null) {
            injectedElement = findInjectedElementAt(manager, kteFile, hostCaretOffset);
        }
        if (injectedElement == null && hostCaretOffset > 0) {
            injectedElement = findInjectedElementAt(manager, configuredFile, hostCaretOffset - 1);
        }
        if (injectedElement == null && hostCaretOffset > 0) {
            injectedElement = findInjectedElementAt(manager, kteFile, hostCaretOffset - 1);
        }
        if (injectedElement == null) {
            injectedElement = findInjectedElementViaDocumentWindow(manager, kteFile, hostCaretOffset);
        }
        if (injectedElement == null && hostCaretOffset > 0) {
            injectedElement = findInjectedElementViaDocumentWindow(manager, kteFile, hostCaretOffset - 1);
        }
        assertNotNull(injectionDebugMessage(host, injectedFiles, hostCaretOffset), injectedElement);
        return injectedElement;
    }

    private int currentHostCaretOffset() {
        if (myFixture.getEditor().getDocument() instanceof DocumentWindow documentWindow) {
            return documentWindow.injectedToHost(myFixture.getCaretOffset());
        }

        return myFixture.getCaretOffset();
    }

    private void configureInjectedKotlinContext(@NotNull PsiFile configuredFile, @NotNull PsiElement injectedElement) {
        if (!(injectedElement.getContainingFile() instanceof KtFile ktFile)) {
            return;
        }

        PsiFile kteFile = findKtePsiFile(configuredFile);
        KteSyntheticKotlinAnalysisContextService contextService =
                KteSyntheticKotlinAnalysisContextService.getInstance(getProject());
        PsiElement analysisContext = contextService.findAnalysisContext(
                kteFile,
                contextService.findModuleSourceRoot(kteFile)
        );
        KteSyntheticKotlinPsiFactory.configureAnalysisContext(getProject(), ktFile, analysisContext);
        if (ktFile instanceof KtCodeFragment codeFragment) {
            GlobalSearchScope scope = contextService.resolveSearchScope(kteFile);
            codeFragment.forceResolveScope(scope);
        }
    }

    @NotNull
    private static String receiverTypeDebug(@NotNull KtFile ktFile) {
        KtExpression receiver = null;
        for (KtExpression expression : PsiTreeUtil.findChildrenOfType(ktFile, KtExpression.class)) {
            if ("profile".equals(expression.getText())) {
                receiver = expression;
            }
        }
        if (receiver == null) {
            return "no profile expression in " + ktFile.getText();
        }

        KtExpression finalReceiver = receiver;
        try {
            Object type = AnalyzeKt.analyze(ktFile, session -> session.getExpressionType(finalReceiver));
            return String.valueOf(type);
        } catch (RuntimeException exception) {
            return exception.getClass().getName() + ": " + exception.getMessage();
        }
    }

    private PsiElement findInjectedElementAt(@NotNull InjectedLanguageManager manager,
                                             @NotNull PsiFile file,
                                             int offset) {
        return manager.findInjectedElementAt(file, offset);
    }

    private PsiElement findInjectedElementViaDocumentWindow(@NotNull InjectedLanguageManager manager,
                                                            @NotNull PsiFile file,
                                                            int hostOffset) {
        List<DocumentWindow> documents = manager.getCachedInjectedDocumentsInRange(file, TextRange.create(hostOffset, hostOffset));
        for (DocumentWindow document : documents) {
            if (!document.isValid() || document.getHostRange(hostOffset) == null) {
                continue;
            }

            int injectedOffset = document.hostToInjected(hostOffset);
            for (Pair<PsiElement, TextRange> injectedFile : manager.getInjectedPsiFiles(findKteHost(file))) {
                PsiFile psiFile = injectedFile.getFirst().getContainingFile();
                if (psiFile.getViewProvider().getDocument() == document) {
                    return psiFile.findElementAt(Math.max(0, Math.min(injectedOffset, psiFile.getTextLength() - 1)));
                }
            }
        }

        return null;
    }

    @NotNull
    private KtePsiJavaContent findKteHost(@NotNull PsiFile kteFile) {
        KtePsiJavaContent host = PsiTreeUtil.findChildOfType(kteFile, KtePsiJavaContent.class);
        assertNotNull("No KTE root host in " + kteFile.getClass().getName() + ":\n" + kteFile.getText(), host);
        return host;
    }

    @NotNull
    private PsiFile findKtePsiFile(@NotNull PsiFile configuredFile) {
        PsiFile psiFile = findPsiFileWithKteHost(configuredFile.getViewProvider());
        if (psiFile != null) {
            return psiFile;
        }

        PsiFile topLevelFile = InjectedLanguageManager.getInstance(getProject()).getTopLevelFile(configuredFile);
        if (topLevelFile != configuredFile) {
            psiFile = findPsiFileWithKteHost(topLevelFile.getViewProvider());
            if (psiFile != null) {
                return psiFile;
            }
        }

        fail("No KTE PSI root with KtePsiJavaContent. configured=" + fileDebug(configuredFile) +
                ", topLevel=" + fileDebug(topLevelFile));
        throw new AssertionError("unreachable");
    }

    private PsiFile findPsiFileWithKteHost(@NotNull FileViewProvider viewProvider) {
        for (com.intellij.lang.Language language : viewProvider.getLanguages()) {
            PsiFile psiFile = viewProvider.getPsi(language);
            if (psiFile != null && PsiTreeUtil.findChildOfType(psiFile, KtePsiJavaContent.class) != null) {
                return psiFile;
            }
        }

        return null;
    }

    @NotNull
    private static String fileDebug(@NotNull PsiFile file) {
        StringBuilder result = new StringBuilder();
        FileViewProvider viewProvider = file.getViewProvider();
        result.append(file.getClass().getName())
                .append("[language=").append(file.getLanguage().getID())
                .append(", provider=").append(viewProvider.getClass().getName())
                .append(", base=").append(viewProvider.getBaseLanguage().getID())
                .append(", languages=");
        for (com.intellij.lang.Language language : viewProvider.getLanguages()) {
            PsiFile psiFile = viewProvider.getPsi(language);
            result.append(language.getID())
                    .append(':')
                    .append(psiFile == null ? "null" : psiFile.getClass().getName())
                    .append(' ');
        }
        result.append(']');
        return result.toString();
    }

    @NotNull
    private String injectionDebugMessage(@NotNull KtePsiJavaContent host,
                                         @NotNull List<Pair<PsiElement, TextRange>> injectedFiles,
                                         int hostCaretOffset) {
        StringBuilder result = new StringBuilder("hostRange=" + host.getTextRange() +
                ", hostTextLength=" + host.getTextLength() +
                ", injectedFiles=" + injectedFiles.size() +
                ", injectorCalls=" + InjectionDebug.calls +
                ", lastDebug=" + InjectionDebug.lastDebug);
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(host.getProject());
        List<DocumentWindow> documents = manager.getCachedInjectedDocumentsInRange(host.getContainingFile(), TextRange.create(hostCaretOffset, hostCaretOffset));
        result.append(", hostCaretOffset=").append(hostCaretOffset)
                .append(", fixtureCaretOffset=").append(myFixture.getCaretOffset())
                .append(", cachedDocumentsAtCaret=").append(documents.size());
        for (DocumentWindow document : documents) {
            result.append("\n  document valid=")
                    .append(document.isValid())
                    .append(", textLength=")
                    .append(document.getTextLength())
                    .append(", hostRangeAtCaret=")
                    .append(document.getHostRange(hostCaretOffset))
                    .append(", hostToInjected=")
                    .append(document.hostToInjected(hostCaretOffset))
                    .append(", hostRanges=");
            for (com.intellij.openapi.util.Segment range : document.getHostRanges()) {
                result.append('(')
                        .append(range.getStartOffset())
                        .append(',')
                        .append(range.getEndOffset())
                        .append(')');
            }
        }
        for (Pair<PsiElement, TextRange> injectedFile : injectedFiles) {
            PsiElement injectedRoot = injectedFile.getFirst();
            result.append("\n  injected=")
                    .append(injectedRoot.getClass().getName())
                    .append(", containingFile=")
                    .append(injectedRoot.getContainingFile().getClass().getName())
                    .append(", range=")
                    .append(injectedFile.getSecond())
                    .append(", text='")
                    .append(injectedRoot.getText().replace("\n", "\\n"))
                    .append('\'');
        }
        return result.toString();
    }

    private static final class InjectionDebug {
        private static int calls;
        private static String lastDebug = "not called";

        private static void reset() {
            calls = 0;
            lastDebug = "not called";
        }
    }

    private static final class FullSyntheticKotlinInjector implements MultiHostInjector {
        @Override
        public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
            if (!(context instanceof KtePsiJavaContent host)) {
                return;
            }

            InjectionDebug.calls++;
            KteSyntheticKotlinFile syntheticFile = new KteSyntheticKotlinFileBuilder().build(host.getContainingFile());
            List<KteSyntheticKotlinRangeMapping> mappings = sourceMappingsInsideHost(host, syntheticFile);
            InjectionDebug.lastDebug = "fullSynthetic hostRange=" + host.getTextRange() +
                    ", syntheticLength=" + syntheticFile.getText().length() +
                    ", allMappings=" + syntheticFile.getMappings().size() +
                    ", hostMappings=" + mappings.size();
            if (mappings.isEmpty()) {
                return;
            }

            registrar.startInjecting(KotlinFileType.INSTANCE.getLanguage())
                    .frankensteinInjection(true)
                    .makeInspectionsLenient(true);
            Key<KaModule> contextModuleKey = KteSyntheticKotlinModuleContext.contextModuleUserDataKey();
            if (contextModuleKey != null) {
                KteSyntheticKotlinAnalysisContextService contextService =
                        KteSyntheticKotlinAnalysisContextService.getInstance(host.getProject());
                PsiElement analysisContext = contextService.findAnalysisContext(
                        host.getContainingFile(),
                        contextService.findModuleSourceRoot(host.getContainingFile())
                );
                registrar.putInjectedFileUserData(
                        contextModuleKey,
                        KteSyntheticKotlinModuleContext.contextModule(host.getProject(), analysisContext)
                );
            }

            String syntheticText = syntheticFile.getText();
            int syntheticOffset = 0;
            for (int index = 0; index < mappings.size(); index++) {
                KteSyntheticKotlinRangeMapping mapping = mappings.get(index);
                TextRange kotlinRange = mapping.getKotlinRange();
                String prefix = syntheticText.substring(syntheticOffset, kotlinRange.getStartOffset());
                syntheticOffset = kotlinRange.getEndOffset();
                String suffix = index + 1 == mappings.size()
                        ? syntheticText.substring(syntheticOffset)
                        : "";

                registrar.addPlace(
                        prefix,
                        suffix,
                        host,
                        toHostRange(host, mapping.getTemplateRange())
                );
            }

            registrar.doneInjecting();
        }

        @Override
        public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
            return List.of(KtePsiJavaContent.class);
        }

        @NotNull
        private static List<KteSyntheticKotlinRangeMapping> sourceMappingsInsideHost(@NotNull KtePsiJavaContent host,
                                                                                    @NotNull KteSyntheticKotlinFile syntheticFile) {
            List<KteSyntheticKotlinRangeMapping> result = new ArrayList<>();
            for (KteSyntheticKotlinRangeMapping mapping : syntheticFile.getMappings()) {
                if (isInside(host.getTextRange(), mapping.getTemplateRange())) {
                    result.add(mapping);
                }
            }

            result.sort(Comparator.comparingInt(mapping -> mapping.getKotlinRange().getStartOffset()));
            return result;
        }

        private static boolean isInside(@NotNull TextRange container, @NotNull TextRange range) {
            return range.getStartOffset() >= container.getStartOffset() &&
                    range.getEndOffset() <= container.getEndOffset();
        }

        @NotNull
        private static TextRange toHostRange(@NotNull KtePsiJavaContent host, @NotNull TextRange templateRange) {
            return templateRange.shiftLeft(host.getTextRange().getStartOffset());
        }
    }

    private static final class MinimalBlockContextKotlinInjector implements MultiHostInjector {
        private static final Pattern KOTLIN_PARAMETER =
                Pattern.compile("^\\s*(?:vararg\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([^=]+?)(?:\\s*=.*)?$");

        @Override
        public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
            if (!(context instanceof KtePsiJavaContent host)) {
                return;
            }

            InjectionDebug.calls++;
            KtePsiFileContext fileContext = KtePsiFileContext.from(host);
            List<JtePsiOutput> outputs = new ArrayList<>(PsiTreeUtil.findChildrenOfType(host, JtePsiOutput.class));
            InjectionDebug.lastDebug = "minimalBlock hostRange=" + host.getTextRange() +
                    ", params=" + fileContext.parameters.size() +
                    ", outputs=" + outputs.size();
            if (outputs.isEmpty()) {
                return;
            }

            registrar.startInjecting(KotlinFileType.INSTANCE.getLanguage())
                    .makeInspectionsLenient(true);
            Key<KaModule> contextModuleKey = KteSyntheticKotlinModuleContext.contextModuleUserDataKey();
            if (contextModuleKey != null) {
                registrar.putInjectedFileUserData(contextModuleKey, fileContext.contextModule());
            }

            String prefix = fileContext.localVariablePrefix();
            for (int index = 0; index < outputs.size(); index++) {
                JtePsiJavaInjection expression = PsiTreeUtil.getChildOfType(outputs.get(index), JtePsiJavaInjection.class);
                if (expression == null) {
                    continue;
                }

                registrar.addPlace(
                        prefix,
                        "\n",
                        host,
                        expression.getTextRange().shiftLeft(host.getTextRange().getStartOffset())
                );
                prefix = "";
            }

            registrar.doneInjecting();
        }

        @Override
        public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
            return List.of(KtePsiJavaContent.class);
        }

        private record KtePsiFileContext(@NotNull Map<String, String> parameters, @NotNull KaModule contextModule) {
            @NotNull
            private static KtePsiFileContext from(@NotNull KtePsiJavaContent host) {
                Map<String, String> parameters = new LinkedHashMap<>();
                KteKotlinImportResolver importResolver = new KteKotlinImportResolver(host.getContainingFile());
                for (JtePsiParam param : PsiTreeUtil.findChildrenOfType(host, JtePsiParam.class)) {
                    JtePsiJavaInjection declaration = PsiTreeUtil.getChildOfType(param, JtePsiJavaInjection.class);
                    if (declaration == null) {
                        continue;
                    }

                    Matcher matcher = KOTLIN_PARAMETER.matcher(declaration.getText());
                    if (!matcher.matches()) {
                        continue;
                    }

                    String name = matcher.group(1);
                    String type = matcher.group(2).trim();
                    PsiClass psiClass = importResolver.resolveClass(type);
                    String qualifiedType = psiClass == null || psiClass.getQualifiedName() == null
                            ? type
                            : psiClass.getQualifiedName();
                    parameters.put(name, qualifiedType);
                }

                KteSyntheticKotlinAnalysisContextService contextService =
                        KteSyntheticKotlinAnalysisContextService.getInstance(host.getProject());
                PsiElement analysisContext = contextService.findAnalysisContext(
                        host.getContainingFile(),
                        contextService.findModuleSourceRoot(host.getContainingFile())
                );
                KaModule contextModule = KteSyntheticKotlinModuleContext.contextModule(host.getProject(), analysisContext);
                return new KtePsiFileContext(parameters, contextModule);
            }

            @NotNull
            private String localVariablePrefix() {
                StringBuilder result = new StringBuilder();
                for (Map.Entry<String, String> parameter : parameters.entrySet()) {
                    result.append("val ")
                            .append(parameter.getKey())
                            .append(" = null as ")
                            .append(parameter.getValue())
                            .append('\n');
                }
                return result.toString();
            }
        }
    }
}
