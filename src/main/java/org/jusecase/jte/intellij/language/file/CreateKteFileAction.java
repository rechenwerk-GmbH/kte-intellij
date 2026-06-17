package org.jusecase.jte.intellij.language.file;

import com.intellij.ide.actions.CreateFileAction;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.KteIcons;

public class CreateKteFileAction extends CreateFileAction {

    public CreateKteFileAction() {
        super(() -> "KTE Template", () -> "Create KTE Template", () -> KteIcons.ICON);
    }

    @Override
    protected @Nullable String getDefaultExtension() {
        return "kte";
    }
}
