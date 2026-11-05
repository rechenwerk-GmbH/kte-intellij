package org.jusecase.jte.intellij.language;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

record KteTemplateDiagnostic(@NotNull String message,
                             @NotNull TextRange range,
                             @NotNull List<IntentionAction> fixes) {
    KteTemplateDiagnostic {
        fixes = List.copyOf(fixes);
    }
}
