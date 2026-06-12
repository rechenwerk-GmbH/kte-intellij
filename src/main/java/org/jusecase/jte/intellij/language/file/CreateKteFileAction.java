package org.jusecase.jte.intellij.language.file;

import com.intellij.ide.actions.CreateFileAction;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.JteIcons;

public class CreateKteFileAction extends CreateFileAction {

    public CreateKteFileAction() {
        super(() -> "KTE Template", () -> "Create KTE Template", () -> JteIcons.ICON);
    }

    @Override
    protected @Nullable String getDefaultExtension() {
        return "kte";
    }
}
