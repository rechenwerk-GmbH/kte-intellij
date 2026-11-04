package org.jusecase.jte.intellij.language;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.*;

import java.util.ArrayList;
import java.util.List;

/** Shared structural checks for editor annotations and the batch audit. */
final class KteStructuralDiagnosticCollector {
    @NotNull
    List<Diagnostic> collect(@NotNull PsiFile file) {
        List<Diagnostic> result = new ArrayList<>();
        file.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                result.addAll(collect(element));
                super.visitElement(element);
            }
        });
        return result;
    }

    @NotNull
    List<Diagnostic> collect(@NotNull PsiElement element) {
        return switch (element) {
            case JtePsiTemplateName name -> templateName(name);
            case JtePsiIf ifElement -> missingEnd(ifElement, JtePsiEndIf.class, "MISSING_ENDIF", "Missing @endif");
            case JtePsiFor forElement -> missingEnd(forElement, JtePsiEndFor.class, "MISSING_ENDFOR", "Missing @endfor");
            case JtePsiContent content -> missingEnd(content, JtePsiEndContent.class, "MISSING_CONTENT_END", "Missing `");
            case JtePsiElseIf elseIf -> parentCheck(elseIf, JtePsiIf.class, "ORPHAN_ELSEIF", "Missing @if");
            case JtePsiElse elseElement -> elseDiagnostics(elseElement);
            case JtePsiEndIf endIf -> parentCheck(endIf, JtePsiIf.class, "ORPHAN_ENDIF", "Missing @if");
            case JtePsiEndContent endContent -> parentCheck(endContent, JtePsiContent.class, "ORPHAN_CONTENT_END", "Missing @`");
            case JtePsiEndFor endFor -> parentCheck(endFor, JtePsiFor.class, "ORPHAN_ENDFOR", "Missing @for");
            default -> List.of();
        };
    }

    private List<Diagnostic> templateName(JtePsiTemplateName name) {
        if (name.getReference() != null) {
            return List.of();
        }
        if (name.findRootDirectory() == null) {
            return error(name, "MISSING_TEMPLATE_ROOT", "Please add a '" +
                    JtePsiTemplateName.TEMPLATE_ROOT_MARKER +
                    "' file to the root source directory of your KTE template sources, so that IntelliJ knows how to reference templates.");
        }
        return name.isDirectory()
                ? error(name, "UNRESOLVED_DIRECTORY", "Unresolved directory " + name.getName())
                : error(name, "UNRESOLVED_TEMPLATE", "Unresolved template");
    }

    private List<Diagnostic> elseDiagnostics(JtePsiElse element) {
        List<Diagnostic> result = new ArrayList<>();
        if (!(element.getParent() instanceof JtePsiIf) && !(element.getParent() instanceof JtePsiFor)) {
            result.add(new Diagnostic("ORPHAN_ELSE", "Missing @if or @for", element.getTextRange()));
        }
        if (PsiTreeUtil.getPrevSiblingOfType(element, JtePsiElse.class) != null) {
            result.add(new Diagnostic("DUPLICATE_ELSE", "More than one @else", element.getTextRange()));
        }
        return result;
    }

    private List<Diagnostic> missingEnd(PsiElement element, Class<? extends PsiElement> endType,
                                        String code, String message) {
        return endType.isInstance(element.getLastChild()) ? List.of() : error(element, code, message);
    }

    private List<Diagnostic> parentCheck(PsiElement element, Class<? extends PsiElement> parentType,
                                         String code, String message) {
        return parentType.isInstance(element.getParent()) ? List.of() : error(element, code, message);
    }

    private List<Diagnostic> error(PsiElement element, String code, String message) {
        return List.of(new Diagnostic(code, message, element.getTextRange()));
    }

    record Diagnostic(@NotNull String code, @NotNull String message, @NotNull TextRange range) {
    }
}
