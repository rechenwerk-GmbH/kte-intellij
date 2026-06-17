package org.jusecase.jte.intellij.language.k2;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.KteLanguage;
import org.jusecase.jte.intellij.language.psi.KtePsiFile;

import java.util.List;

final class KteTemplateDiagnosticCollector {
    @NotNull
    List<KteTemplateDiagnostic> collect(@NotNull PsiFile templateFile) {
        if (!isKteFile(templateFile)) {
            return List.of();
        }

        return new KteTemplateContractDiagnosticChecker().collect(templateFile);
    }

    private boolean isKteFile(@NotNull PsiFile file) {
        return file instanceof KtePsiFile ||
                file.getViewProvider().getPsi(KteLanguage.INSTANCE) instanceof KtePsiFile;
    }
}
