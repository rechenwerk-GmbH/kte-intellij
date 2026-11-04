package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.template.KteKotlinImportResolver;
import org.jusecase.jte.intellij.language.template.KteKotlinTypeText;
import org.jusecase.jte.intellij.language.template.KteTemplateSignatureService;

import java.util.List;

final class KteKotlinTypeRenderer {
    private final Project project;
    private final GlobalSearchScope scope;
    private final Imports parentImports;

    KteKotlinTypeRenderer(@NotNull PsiFile parentTemplateFile) {
        this.project = parentTemplateFile.getProject();
        this.scope = KteKotlinAnalysisContextService.getInstance(project).resolveSearchScope(parentTemplateFile);
        this.parentImports = Imports.from(parentTemplateFile);
    }

    @NotNull
    String renderChildSignatureType(@NotNull KteTemplateSignatureService.Parameter parameter) {
        PsiFile childTemplateFile = parameter.sourceElement().getContainingFile();
        Imports childImports = childTemplateFile == null ? Imports.EMPTY : Imports.from(childTemplateFile);
        return renderTypeText(parameter.typeText(), childImports);
    }

    @NotNull
    private String renderTypeText(@NotNull String typeText, @NotNull Imports childImports) {
        return KteKotlinTypeText.rewriteReferences(typeText,
                reference -> renderTypeReference(reference, childImports));
    }

    @NotNull
    private String renderTypeReference(@NotNull String typeName, @NotNull Imports childImports) {
        if (KteKotlinTypeText.isTypeKeyword(typeName)) {
            return typeName;
        }

        int memberSeparator = typeName.indexOf('.');
        if (memberSeparator > 0) {
            String root = typeName.substring(0, memberSeparator);
            String importedRoot = childImports.qualifiedName(root, this::classExists);
            return importedRoot == null ? typeName : importedRoot + typeName.substring(memberSeparator);
        }

        String childQualifiedName = childImports.qualifiedName(typeName, this::classExists);
        if (childQualifiedName != null) {
            return childQualifiedName;
        }

        if (KteKotlinTypeText.isKnownTypeName(typeName) || parentImports.resolves(typeName, this::classExists)) {
            return typeName;
        }

        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(typeName, scope);
        String qualifiedName = psiClass == null ? null : psiClass.getQualifiedName();
        return qualifiedName == null ? typeName : qualifiedName;
    }

    private boolean classExists(@NotNull String qualifiedName) {
        return JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope) != null;
    }

    private interface ClassLookup {
        boolean exists(@NotNull String qualifiedName);
    }

    private record Imports(@NotNull List<KteKotlinImportResolver.ImportInfo> imports) {
        private static final Imports EMPTY = new Imports(List.of());

        @NotNull
        private static Imports from(@NotNull PsiFile file) {
            List<KteKotlinImportResolver.ImportInfo> result = new KteKotlinImportResolver(file).imports();
            return result.isEmpty() ? EMPTY : new Imports(result);
        }

        private boolean resolves(@NotNull String visibleName, @NotNull ClassLookup classLookup) {
            return qualifiedName(visibleName, classLookup) != null;
        }

        @Nullable
        private String qualifiedName(@NotNull String visibleName, @NotNull ClassLookup classLookup) {
            for (KteKotlinImportResolver.ImportInfo importInfo : imports) {
                if (!importInfo.star() && importInfo.visibleName().equals(visibleName)) {
                    return importInfo.qualifiedName();
                }
            }

            for (KteKotlinImportResolver.ImportInfo importInfo : imports) {
                if (importInfo.star()) {
                    String qualifiedName = importInfo.packageName() + "." + visibleName;
                    if (classLookup.exists(qualifiedName)) {
                        return qualifiedName;
                    }
                }
            }

            return null;
        }
    }
}
