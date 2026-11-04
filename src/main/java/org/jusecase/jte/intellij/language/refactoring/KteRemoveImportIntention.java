package org.jusecase.jte.intellij.language.refactoring;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class KteRemoveImportIntention implements IntentionAction {
    private final TextRange importRange;
    private final String text;

    @NotNull
    public static KteRemoveImportIntention unused(@NotNull TextRange importRange) {
        return new KteRemoveImportIntention(importRange, "Remove unused .kte import");
    }

    private KteRemoveImportIntention(@NotNull TextRange importRange, @NotNull String text) {
        this.importRange = importRange;
        this.text = text;
    }

    @Override
    public @NotNull String getText() {
        return text;
    }

    @Override
    public @NotNull String getFamilyName() {
        return text;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return file != null &&
                KteNativeTemplateSourceEditUtil.isKteFile(file) &&
                KteNativeTemplateSourceEditUtil.isValidRange(file, importRange) &&
                !file.getText().substring(importRange.getStartOffset(), importRange.getEndOffset()).trim().isEmpty();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        if (file != null) {
            KteNativeTemplateSourceEditUtil.deleteLine(file, importRange);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                         @NotNull Editor editor,
                                                         @NotNull PsiFile file) {
        return IntentionPreviewInfo.EMPTY;
    }
}
