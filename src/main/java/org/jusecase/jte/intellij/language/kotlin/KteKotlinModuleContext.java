package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.api.projectStructure.DanglingFilesKt;
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule;
import org.jetbrains.kotlin.idea.base.projectStructure.ApiKt;
import org.jetbrains.kotlin.psi.KtCodeFragment;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtPsiFactoryKt;

final class KteKotlinModuleContext {
    private KteKotlinModuleContext() {
    }

    static void configure(@NotNull Project project,
                          @NotNull KtFile ktFile,
                          @Nullable PsiElement analysisContext) {
        if (analysisContext == null) {
            return;
        }

        KaModule contextModule = contextModule(project, analysisContext);
        KtPsiFactoryKt.setAnalysisContext(ktFile, analysisContext);
        if (ktFile instanceof KtCodeFragment codeFragment) {
            DanglingFilesKt.setRefinedContextModule(codeFragment, contextModule);
            return;
        }

        DanglingFilesKt.setContextModule(ktFile, contextModule);
    }

    static void attachCompletionContext(@NotNull MultiHostRegistrar registrar, @NotNull KaModule module) {
        Key<KaModule> contextModuleKey = contextModuleUserDataKey();
        if (contextModuleKey != null) {
            registrar.putInjectedFileUserData(contextModuleKey, module);
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static Key<KaModule> contextModuleUserDataKey() {
        try {
            Class.forName(DanglingFilesKt.class.getName(), true, DanglingFilesKt.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }

        return (Key<KaModule>) Key.findKeyByName("CONTEXT_MODULE");
    }

    @NotNull
    static KaModule contextModule(@NotNull Project project, @NotNull PsiElement analysisContext) {
        KaModule contextModule = ApiKt.getKaModule(analysisContext, project, null);
        // Injected Kotlin completion needs the source module when one is available.
        Module openApiModule = ModuleUtilCore.findModuleForPsiElement(analysisContext);
        KaModule sourceModule = openApiModule == null ? null :
                ApiKt.toKaSourceModuleWithElementSourceModuleKindOrProduction(openApiModule, analysisContext);
        return sourceModule == null ? contextModule : sourceModule;
    }
}
