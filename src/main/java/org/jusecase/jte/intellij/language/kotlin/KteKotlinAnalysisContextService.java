package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.List;

public final class KteKotlinAnalysisContextService {
    private final Project project;

    public KteKotlinAnalysisContextService(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public static KteKotlinAnalysisContextService getInstance(@NotNull Project project) {
        return project.getService(KteKotlinAnalysisContextService.class);
    }

    @NotNull
    public GlobalSearchScope resolveSearchScope(@NotNull PsiFile templateFile) {
        VirtualFile virtualFile = templateFile.getVirtualFile();
        if (virtualFile != null) {
            Module module = ProjectRootManager.getInstance(project)
                    .getFileIndex()
                    .getModuleForFile(virtualFile);
            if (module != null) {
                return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
            }
        }

        return GlobalSearchScope.allScope(project);
    }

    @Nullable
    public VirtualFile findModuleSourceRoot(@NotNull PsiFile templateFile) {
        VirtualFile templateVirtualFile = templateFile.getVirtualFile();
        if (templateVirtualFile == null) {
            return null;
        }

        Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(templateVirtualFile);
        if (module == null) {
            return null;
        }

        List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.SOURCE);
        if (sourceRoots.isEmpty()) {
            return null;
        }

        return sourceRoots.stream()
                .filter(sourceRoot -> sourceRoot.getPath().endsWith("/kotlin"))
                .findFirst()
                .or(() -> sourceRoots.stream()
                        .filter(sourceRoot -> sourceRoot.getPath().endsWith("/java"))
                        .findFirst())
                .or(() -> sourceRoots.stream()
                        .filter(sourceRoot -> !VfsUtilCore.isAncestor(sourceRoot, templateVirtualFile, false))
                        .findFirst())
                .orElse(sourceRoots.getFirst());
    }

    @NotNull
    public PsiElement findAnalysisContext(@NotNull PsiFile templateFile, @Nullable VirtualFile sourceRoot) {
        if (sourceRoot == null) {
            return templateFile;
        }

        PsiDirectory sourceDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
        return sourceDirectory == null ? templateFile : sourceDirectory;
    }
}
