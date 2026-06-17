package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.psi.JtePsiElse;
import org.jusecase.jte.intellij.language.psi.JtePsiElseIf;
import org.jusecase.jte.intellij.language.psi.JtePsiFor;
import org.jusecase.jte.intellij.language.psi.JtePsiIf;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.JtePsiStatement;
import org.jusecase.jte.intellij.language.psi.JtePsiTemplate;

final class KteKotlinFragmentVisibility {
    private KteKotlinFragmentVisibility() {
    }

    static boolean isVisibleStatement(@NotNull JtePsiStatement statement,
                                      @NotNull JtePsiJavaInjection targetInjection) {
        PsiElement scope = visibilityScope(statement);
        if (scope == null) {
            return true;
        }

        return PsiTreeUtil.isAncestor(scope, targetInjection, false) &&
                !crossesAlternativeBranch(scope, targetInjection) &&
                !hasBranchBoundaryBetween(scope, statement, targetInjection);
    }

    @Nullable
    private static PsiElement visibilityScope(@NotNull PsiElement element) {
        return PsiTreeUtil.getParentOfType(
                element,
                JtePsiFor.class,
                JtePsiIf.class,
                JtePsiElseIf.class,
                JtePsiElse.class,
                JtePsiTemplate.class
        );
    }

    private static boolean crossesAlternativeBranch(@NotNull PsiElement scope,
                                                    @NotNull PsiElement target) {
        for (PsiElement current = target; current != null && current != scope; current = current.getParent()) {
            if (current instanceof JtePsiElse || current instanceof JtePsiElseIf) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBranchBoundaryBetween(@NotNull PsiElement scope,
                                                    @NotNull PsiElement statement,
                                                    @NotNull PsiElement target) {
        if (!(scope instanceof JtePsiIf || scope instanceof JtePsiFor)) {
            return false;
        }

        int statementEnd = statement.getTextRange().getEndOffset();
        int targetStart = target.getTextRange().getStartOffset();
        for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
            TextRange childRange = child.getTextRange();
            if (childRange == null || childRange.getEndOffset() <= statementEnd) {
                continue;
            }
            if (childRange.getStartOffset() >= targetStart) {
                return false;
            }
            if (child instanceof JtePsiElse || child instanceof JtePsiElseIf) {
                return true;
            }
        }
        return false;
    }
}
