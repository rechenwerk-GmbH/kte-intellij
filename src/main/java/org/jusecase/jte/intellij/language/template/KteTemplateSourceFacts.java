package org.jusecase.jte.intellij.language.template;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.psi.JtePsiParam;
import org.jusecase.jte.intellij.language.psi.KtePsiJavaContent;

import java.util.ArrayList;
import java.util.List;

/** Source facts shared by whole-template analysis and caret-scoped completion. */
public record KteTemplateSourceFacts(@NotNull PsiFile file,
                                     @NotNull KtePsiJavaContent host,
                                     @NotNull String sourceText,
                                     @NotNull List<KteKotlinImportResolver.ImportDirective> imports,
                                     @NotNull KteTemplateSignatureSource.Signature signature,
                                     @NotNull List<JtePsiParam> parameters) {
    public KteTemplateSourceFacts {
        imports = List.copyOf(imports);
        parameters = List.copyOf(parameters);
    }

    @Nullable
    public static KteTemplateSourceFacts from(@NotNull PsiFile file) {
        KtePsiJavaContent host = PsiTreeUtil.findChildOfType(file, KtePsiJavaContent.class);
        if (host == null) {
            return null;
        }

        List<JtePsiParam> parameters = new ArrayList<>();
        for (PsiElement child : host.getChildren()) {
            if (child instanceof JtePsiParam param) {
                parameters.add(param);
            }
        }
        return new KteTemplateSourceFacts(
                file,
                host,
                file.getText(),
                new KteKotlinImportResolver(file).importDirectives(),
                KteTemplateSignatureSource.resolve(file),
                parameters
        );
    }
}
