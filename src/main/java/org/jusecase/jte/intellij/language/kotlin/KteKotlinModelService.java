package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtFile;
import org.jusecase.jte.intellij.language.psi.JtePsiTemplateName;
import org.jusecase.jte.intellij.language.template.KteTemplateSignatureService;

import java.util.ArrayList;
import java.util.List;

/** Owns the in-memory Kotlin view of a template. Kotlin analysis symbols are never cached here. */
public final class KteKotlinModelService {
    private static final Key<CachedValue<Model>> MODEL_KEY = Key.create("kte.kotlin.model");
    private final Project project;

    public KteKotlinModelService(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public static KteKotlinModelService getInstance(@NotNull Project project) {
        return project.getService(KteKotlinModelService.class);
    }

    @NotNull
    public Model getModel(@NotNull PsiFile templateFile) {
        PsiFile kteFile = KteTemplateSignatureService.resolveKteFile(templateFile);
        if (kteFile == null) {
            throw new IllegalArgumentException("Expected a KTE template file");
        }

        return CachedValuesManager.getManager(project).getCachedValue(
                kteFile,
                MODEL_KEY,
                () -> {
                    Model model = buildModel(kteFile);
                    List<Object> dependencies = new ArrayList<>();
                    dependencies.add(kteFile);
                    dependencies.add(ProjectRootModificationTracker.getInstance(project));
                    dependencies.add(VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
                    for (JtePsiTemplateName name : PsiTreeUtil.findChildrenOfType(kteFile, JtePsiTemplateName.class)) {
                        PsiFile child = name.resolveFile();
                        if (child != null && child != kteFile) {
                            dependencies.add(child);
                        }
                    }
                    return CachedValueProvider.Result.create(model, dependencies.toArray());
                },
                false
        );
    }

    @NotNull
    private Model buildModel(@NotNull PsiFile templateFile) {
        KteKotlinProjection projection = new KteKotlinProjectionBuilder().build(templateFile);
        KtFile ktFile = KteKotlinScratchFileFactory.createKtFile(
                templateFile, projection.getFileName(), projection.getText());
        return new Model(projection, ktFile);
    }

    public record Model(@NotNull KteKotlinProjection projection, @NotNull KtFile ktFile) {
    }
}
