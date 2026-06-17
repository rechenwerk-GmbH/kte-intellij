package org.jusecase.jte.intellij.language.template;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jusecase.jte.intellij.language.kotlin.KteKotlinAnalysisContextService;
import org.jusecase.jte.intellij.language.psi.JtePsiImport;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.template.KteKotlinTypeText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source helper for .kte imports, deterministic import fixes, and child-template stub type rendering.
 */
public final class KteKotlinImportResolver {
    private static final Pattern IMPORT_ALIAS_PATTERN =
            Pattern.compile("\\s*(.*?)\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*");

    private final PsiFile templateFile;
    private final Project project;
    private final GlobalSearchScope scope;

    public KteKotlinImportResolver(@NotNull PsiFile templateFile) {
        this.templateFile = templateFile;
        this.project = templateFile.getProject();
        this.scope = KteKotlinAnalysisContextService.getInstance(project).resolveSearchScope(templateFile);
    }

    @Nullable
    public PsiClass resolveClass(@NotNull String typeName) {
        for (String qualifiedName : resolveQualifiedTypeNames(typeName)) {
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope);
            if (psiClass != null) {
                return psiClass;
            }
        }

        return null;
    }

    @NotNull
    public List<ImportCandidate> importCandidates(@NotNull String visibleName, boolean includeCallables) {
        String candidateName = KteKotlinTypeText.shortName(visibleName);
        Map<String, ImportCandidate> result = new LinkedHashMap<>();
        addJavaClassImportCandidates(result, candidateName);

        addKotlinTopLevelImportCandidates(result, candidateName, includeCallables);
        return result.values()
                .stream()
                .sorted(Comparator.comparing(ImportCandidate::qualifiedName))
                .toList();
    }

    @NotNull
    public List<ImportCandidate> importCandidatesByPrefix(@NotNull String visibleNamePrefix, boolean includeCallables) {
        String candidatePrefix = KteKotlinTypeText.shortName(visibleNamePrefix);
        Map<String, ImportCandidate> result = new LinkedHashMap<>();
        addJavaClassImportCandidatesByPrefix(result, candidatePrefix);
        addKotlinTopLevelImportCandidates(result, declarationName -> declarationName.startsWith(candidatePrefix), includeCallables);
        return result.values()
                .stream()
                .sorted(Comparator.comparing(ImportCandidate::qualifiedName))
                .toList();
    }

    @NotNull
    public List<ImportInfo> imports() {
        List<ImportInfo> result = new ArrayList<>();
        for (JtePsiImport importElement : PsiTreeUtil.findChildrenOfType(templateFile, JtePsiImport.class)) {
            JtePsiJavaInjection injection = PsiTreeUtil.getChildOfType(importElement, JtePsiJavaInjection.class);
            if (injection == null) {
                continue;
            }

            String importText = injection.getText().trim();
            if (!importText.isEmpty()) {
                result.add(parseImport(importText));
            }
        }

        return result;
    }

    @Nullable
    public PsiElement resolveImportedReference(@NotNull String importText, @NotNull String identifierText) {
        ImportInfo importInfo = parseImport(importText);
        if (importInfo.star() || !importInfo.visibleName().equals(identifierText)) {
            return null;
        }

        return resolveImport(importInfo);
    }

    @Nullable
    public PsiElement resolveImportedVisibleName(@NotNull String visibleName) {
        for (ImportInfo importInfo : imports()) {
            if (!importInfo.star() && importInfo.visibleName().equals(visibleName)) {
                return resolveImport(importInfo);
            }
        }
        return null;
    }

    @Nullable
    private PsiElement resolveImport(@NotNull ImportInfo importInfo) {
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(importInfo.qualifiedName(), scope);
        if (psiClass != null) {
            return navigationElement(psiClass);
        }

        return navigationElement(resolveKotlinQualifiedDeclaration(importInfo.qualifiedName()));
    }

    @Nullable
    public PsiElement navigationElement(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }

        PsiElement navigationElement = element.getNavigationElement();
        return navigationElement == null ? element : navigationElement;
    }

    @NotNull
    private List<String> resolveQualifiedTypeNames(@NotNull String typeName) {
        List<String> result = new ArrayList<>();
        if (typeName.contains(".")) {
            result.add(typeName);
            return result;
        }

        String builtinType = builtinQualifiedTypeName(typeName);
        if (builtinType != null) {
            result.add(builtinType);
        }

        String shortName = KteKotlinTypeText.shortName(typeName);
        for (ImportInfo importInfo : imports()) {
            if (!importInfo.star() && importInfo.visibleName().equals(shortName)) {
                result.add(importInfo.qualifiedName());
            } else if (importInfo.star()) {
                result.add(importInfo.packageName() + "." + shortName);
            }
        }

        return result;
    }

    @Nullable
    private PsiElement resolveKotlinQualifiedDeclaration(@NotNull String qualifiedName) {
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile virtualFile : FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            PsiFile psiFile = psiManager.findFile(virtualFile);
            if (!(psiFile instanceof KtFile ktFile)) {
                continue;
            }

            String declarationPath = declarationPath(ktFile, qualifiedName);
            if (declarationPath == null) {
                continue;
            }

            PsiElement declaration = resolveDeclarationPath(ktFile.getDeclarations(), declarationPath);
            if (declaration != null) {
                return declaration;
            }
        }
        return null;
    }

    @Nullable
    private String declarationPath(@NotNull KtFile ktFile, @NotNull String qualifiedName) {
        String packageName = packageName(ktFile);
        if (packageName.isEmpty()) {
            return qualifiedName.contains(".") ? null : qualifiedName;
        }

        String prefix = packageName + ".";
        return qualifiedName.startsWith(prefix) ? qualifiedName.substring(prefix.length()) : null;
    }

    @Nullable
    private PsiElement resolveDeclarationPath(@NotNull List<KtDeclaration> declarations,
                                              @NotNull String declarationPath) {
        int dotOffset = declarationPath.indexOf('.');
        String segment = dotOffset == -1 ? declarationPath : declarationPath.substring(0, dotOffset);
        String remainingPath = dotOffset == -1 ? null : declarationPath.substring(dotOffset + 1);
        for (KtDeclaration declaration : declarations) {
            if (!(declaration instanceof KtNamedDeclaration namedDeclaration) ||
                    !segment.equals(namedDeclaration.getName())) {
                continue;
            }

            if (remainingPath == null) {
                return declaration;
            }
            if (declaration instanceof KtClassOrObject classOrObject) {
                return resolveDeclarationPath(classOrObject.getDeclarations(), remainingPath);
            }
        }
        return null;
    }

    private void addJavaClassImportCandidates(@NotNull Map<String, ImportCandidate> result, @NotNull String candidateName) {
        for (PsiClass psiClass : PsiShortNamesCache.getInstance(project).getClassesByName(candidateName, scope)) {
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName != null) {
                result.putIfAbsent(qualifiedName, new ImportCandidate(qualifiedName, navigationElement(psiClass)));
            }
        }
    }

    private void addJavaClassImportCandidatesByPrefix(@NotNull Map<String, ImportCandidate> result,
                                                      @NotNull String candidatePrefix) {
        for (String className : PsiShortNamesCache.getInstance(project).getAllClassNames()) {
            if (className.startsWith(candidatePrefix)) {
                addJavaClassImportCandidates(result, className);
            }
        }
    }

    private void addKotlinTopLevelImportCandidates(@NotNull Map<String, ImportCandidate> result,
                                                   @NotNull String name,
                                                   boolean includeCallables) {
        addKotlinTopLevelImportCandidates(result, declarationName -> declarationName.equals(name), includeCallables);
    }

    private void addKotlinTopLevelImportCandidates(@NotNull Map<String, ImportCandidate> result,
                                                   @NotNull java.util.function.Predicate<String> nameFilter,
                                                   boolean includeCallables) {
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile virtualFile : FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            PsiFile psiFile = psiManager.findFile(virtualFile);
            if (!(psiFile instanceof KtFile ktFile)) {
                continue;
            }

            for (KtDeclaration declaration : ktFile.getDeclarations()) {
                if (!(declaration instanceof KtNamedDeclaration namedDeclaration) ||
                        namedDeclaration.getName() == null ||
                        !nameFilter.test(namedDeclaration.getName())) {
                    continue;
                }

                if (declaration instanceof KtClassOrObject ||
                        includeCallables && (declaration instanceof KtNamedFunction || declaration instanceof KtProperty)) {
                    String qualifiedName = topLevelQualifiedName(ktFile, namedDeclaration);
                    result.putIfAbsent(qualifiedName, new ImportCandidate(qualifiedName, navigationElement(namedDeclaration)));
                }
            }
        }
    }

    @NotNull
    private String topLevelQualifiedName(@NotNull KtFile ktFile, @NotNull KtNamedDeclaration declaration) {
        String name = declaration.getName();
        String packageName = packageName(ktFile);
        return packageName.isEmpty() ? String.valueOf(name) : packageName + "." + name;
    }

    @NotNull
    private String packageName(@NotNull KtFile ktFile) {
        FqName packageFqName = ktFile.getPackageFqName();
        return packageFqName.asString();
    }

    @NotNull
    private ImportInfo parseImport(@NotNull String importText) {
        String qualifiedName = importText.trim();
        String alias = null;
        Matcher aliasMatcher = IMPORT_ALIAS_PATTERN.matcher(qualifiedName);
        if (aliasMatcher.matches()) {
            qualifiedName = aliasMatcher.group(1).trim();
            alias = aliasMatcher.group(2).trim();
        }

        boolean star = qualifiedName.endsWith(".*");
        String packageName = star ? qualifiedName.substring(0, qualifiedName.length() - ".*".length()) : packageName(qualifiedName);
        String visibleName = alias == null ? KteKotlinTypeText.shortName(qualifiedName) : alias;
        return new ImportInfo(importText, qualifiedName, visibleName, packageName, star);
    }

    @NotNull
    private String packageName(@NotNull String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
    }

    @Nullable
    private String builtinQualifiedTypeName(@NotNull String typeName) {
        return switch (typeName) {
            case "Any" -> "kotlin.Any";
            case "Array" -> "kotlin.Array";
            case "Boolean" -> "kotlin.Boolean";
            case "Byte" -> "kotlin.Byte";
            case "Char" -> "kotlin.Char";
            case "Collection" -> "kotlin.collections.Collection";
            case "Double" -> "kotlin.Double";
            case "Float" -> "kotlin.Float";
            case "Int" -> "kotlin.Int";
            case "Iterable" -> "kotlin.collections.Iterable";
            case "List" -> "kotlin.collections.List";
            case "Long" -> "kotlin.Long";
            case "Map" -> "kotlin.collections.Map";
            case "MutableList" -> "kotlin.collections.MutableList";
            case "MutableMap" -> "kotlin.collections.MutableMap";
            case "MutableSet" -> "kotlin.collections.MutableSet";
            case "Set" -> "kotlin.collections.Set";
            case "Short" -> "kotlin.Short";
            case "String" -> "kotlin.String";
            case "Unit" -> "kotlin.Unit";
            default -> null;
        };
    }

    public record ImportCandidate(@NotNull String qualifiedName, @Nullable PsiElement element) {
    }

    public record ImportInfo(@NotNull String text,
                             @NotNull String qualifiedName,
                             @NotNull String visibleName,
                             @NotNull String packageName,
                             boolean star) {
    }
}
