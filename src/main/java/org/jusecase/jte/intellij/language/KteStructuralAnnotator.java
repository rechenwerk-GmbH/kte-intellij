package org.jusecase.jte.intellij.language;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.*;

public class KteStructuralAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        switch (element) {
            case JtePsiTemplateName name -> annotateTemplateName(name, holder);
            case JtePsiIf ifElement -> missingEnd(ifElement, JtePsiEndIf.class, "Missing @endif", holder);
            case JtePsiFor forElement -> missingEnd(forElement, JtePsiEndFor.class, "Missing @endfor", holder);
            case JtePsiContent content -> missingEnd(content, JtePsiEndContent.class, "Missing `", holder);
            case JtePsiElseIf elseIf -> parentCheck(elseIf, JtePsiIf.class, "Missing @if", holder);
            case JtePsiElse elseElement -> annotateElse(elseElement, holder);
            case JtePsiEndIf endIf -> parentCheck(endIf, JtePsiIf.class, "Missing @if", holder);
            case JtePsiEndContent endContent -> parentCheck(endContent, JtePsiContent.class, "Missing @`", holder);
            case JtePsiEndFor endFor -> parentCheck(endFor, JtePsiFor.class, "Missing @for", holder);
            default -> {
            }
        }
    }

    private void annotateTemplateName(@NotNull JtePsiTemplateName name, @NotNull AnnotationHolder holder) {
        if (name.getReference() != null) {
            return;
        }
        if (name.findRootDirectory() == null) {
            error(name, holder, "Please add a '" + JtePsiTemplateName.TEMPLATE_ROOT_MARKER +
                    "' file to the root source directory of your KTE template sources, so that IntelliJ knows how to reference templates.");
        } else {
            error(name, holder, name.isDirectory() ? "Unresolved directory " + name.getName() : "Unresolved template");
        }
    }

    private void annotateElse(@NotNull JtePsiElse element, @NotNull AnnotationHolder holder) {
        if (!(element.getParent() instanceof JtePsiIf || element.getParent() instanceof JtePsiFor)) {
            error(element, holder, "Missing @if or @for");
        }
        if (PsiTreeUtil.getPrevSiblingOfType(element, JtePsiElse.class) != null) {
            error(element, holder, "More than one @else");
        }
    }

    private void missingEnd(@NotNull PsiElement element,
                            @NotNull Class<? extends PsiElement> endType,
                            @NotNull String message,
                            @NotNull AnnotationHolder holder) {
        if (!endType.isInstance(element.getLastChild())) {
            error(element, holder, message);
        }
    }

    private void parentCheck(@NotNull PsiElement element,
                             @NotNull Class<? extends PsiElement> parentType,
                             @NotNull String message,
                             @NotNull AnnotationHolder holder) {
        if (!parentType.isInstance(element.getParent())) {
            error(element, holder, message);
        }
    }

    private void error(@NotNull PsiElement element,
                       @NotNull AnnotationHolder holder,
                       @NotNull String message) {
        holder.newAnnotation(HighlightSeverity.ERROR, message)
                .range(element.getTextRange())
                .create();
    }
}
