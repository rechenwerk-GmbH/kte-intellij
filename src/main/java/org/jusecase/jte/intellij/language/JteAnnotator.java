package org.jusecase.jte.intellij.language;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.*;

public class JteAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        switch (element) {
            case JtePsiTemplateName jtePsiTemplateName -> doAnnotate(jtePsiTemplateName, holder);
            case JtePsiIf jtePsiIf -> doAnnotate(jtePsiIf, holder);
            case JtePsiFor jtePsiFor -> doAnnotate(jtePsiFor, holder);
            case JtePsiContent jtePsiContent -> doAnnotate(jtePsiContent, holder);
            case JtePsiElseIf jtePsiElseIf -> doAnnotate(jtePsiElseIf, holder);
            case JtePsiElse jtePsiElse -> doAnnotate(jtePsiElse, holder);
            case JtePsiEndIf jtePsiEndIf -> doAnnotate(jtePsiEndIf, holder);
            case JtePsiEndContent jtePsiEndContent -> doAnnotate(jtePsiEndContent, holder);
            case JtePsiEndFor jtePsiEndFor -> doAnnotate(jtePsiEndFor, holder);
            default -> {}
        }
    }

    private void doAnnotate(JtePsiTemplateName element, AnnotationHolder holder) {
        if (element.getReference() == null) {
            if (element.findRootDirectory() == null) {
                addError(holder, "Please add a '" + JtePsiTemplateName.JTE_ROOT + "' file to the root source directory of your jte sources, so that IntelliJ knows how to reference templates.");
            } else {
                if (element.isDirectory()) {
                    addError(holder, "Unresolved directory " + element.getName());
                } else {
                    addError(holder, "Unresolved template");
                }
            }
        }
    }

    private void doAnnotate(JtePsiIf element, AnnotationHolder holder) {
        if (!(element.getLastChild() instanceof JtePsiEndIf)) {
            addError(holder, "Missing @endif");
        }
    }

    private void doAnnotate(JtePsiFor element, AnnotationHolder holder) {
        if (!(element.getLastChild() instanceof JtePsiEndFor)) {
            addError(holder, "Missing @endfor");
        }
    }

    private void doAnnotate(JtePsiContent element, AnnotationHolder holder) {
        if (!(element.getLastChild() instanceof JtePsiEndContent)) {
            addError(holder, "Missing `");
        }
    }

    private void doAnnotate(JtePsiElseIf element, AnnotationHolder holder) {
        checkParentIsIf(element, holder);
    }

    private void doAnnotate(JtePsiElse element, AnnotationHolder holder) {
        checkParentIsIfOrFor(element, holder);
        if (PsiTreeUtil.getPrevSiblingOfType(element, JtePsiElse.class) != null) {
            addError(holder, "More than one @else");
        }
    }

    private void doAnnotate(JtePsiEndIf element, AnnotationHolder holder) {
        checkParentIsIf(element, holder);
    }

    private void doAnnotate(JtePsiEndContent element, AnnotationHolder holder) {
        if (!(element.getParent() instanceof JtePsiContent)) {
            addError(holder, "Missing @`");
        }
    }

    private void doAnnotate(JtePsiEndFor element, AnnotationHolder holder) {
        if (!(element.getParent() instanceof JtePsiFor)) {
            addError(holder, "Missing @for");
        }
    }

    private void checkParentIsIf(JtePsiElement element, AnnotationHolder holder) {
        if (!(element.getParent() instanceof JtePsiIf)) {
            addError(holder, "Missing @if");
        }
    }

    private void checkParentIsIfOrFor(JtePsiElse element, AnnotationHolder holder) {
        if (!(element.getParent() instanceof JtePsiIf) && !(element.getParent() instanceof JtePsiFor)) {
            addError(holder, "Missing @if or @for");
        }
    }

    private void addError(@NotNull AnnotationHolder holder, String message) {
        holder.newAnnotation(HighlightSeverity.ERROR, message).create();
    }
}
