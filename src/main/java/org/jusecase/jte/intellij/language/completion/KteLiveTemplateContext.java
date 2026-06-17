package org.jusecase.jte.intellij.language.completion;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import org.jetbrains.annotations.NotNull;

public class KteLiveTemplateContext extends TemplateContextType {
    protected KteLiveTemplateContext() {
        super("kte");
    }

    @Override
    public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
        String name = templateActionContext.getFile().getName();
        return name.endsWith(".kte");
    }
}
