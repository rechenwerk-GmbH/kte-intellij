package org.jusecase.jte.intellij.language;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jusecase.jte.intellij.language.psi.*;

import java.util.ArrayList;
import java.util.List;

/** Structural checks for editor annotations. */
final class KteStructuralDiagnosticCollector {
    @NotNull
    List<Diagnostic> collect(@NotNull PsiElement element) {
        return switch (element) {
            case JtePsiTemplateName name -> templateName(name);
            case JtePsiIf ifElement -> missingEnd(ifElement, JtePsiEndIf.class, "Missing @endif");
            case JtePsiFor forElement -> missingEnd(forElement, JtePsiEndFor.class, "Missing @endfor");
            case JtePsiContent content -> missingEnd(content, JtePsiEndContent.class, "Missing `");
            case JtePsiElseIf elseIf -> parentCheck(elseIf, JtePsiIf.class, "Missing @if");
            case JtePsiElse elseElement -> elseDiagnostics(elseElement);
            case JtePsiEndIf endIf -> parentCheck(endIf, JtePsiIf.class, "Missing @if");
            case JtePsiEndContent endContent -> parentCheck(endContent, JtePsiContent.class, "Missing @`");
            case JtePsiEndFor endFor -> parentCheck(endFor, JtePsiFor.class, "Missing @for");
            default -> List.of();
        };
    }

    private List<Diagnostic> templateName(JtePsiTemplateName name) {
        if (name.getReference() != null) {
            return List.of();
        }
        if (name.findRootDirectory() == null) {
            return error(name, "Please add a '" +
                    JtePsiTemplateName.TEMPLATE_ROOT_MARKER +
                    "' file to the root source directory of your KTE template sources, so that IntelliJ knows how to reference templates.");
        }
        return name.isDirectory()
                ? error(name, "Unresolved directory " + name.getName())
                : error(name, "Unresolved template");
    }

    private List<Diagnostic> elseDiagnostics(JtePsiElse element) {
        List<Diagnostic> result = new ArrayList<>();
        if (!(element.getParent() instanceof JtePsiIf) && !(element.getParent() instanceof JtePsiFor)) {
            result.add(new Diagnostic("Missing @if or @for", element.getTextRange()));
        }
        if (PsiTreeUtil.getPrevSiblingOfType(element, JtePsiElse.class) != null) {
            result.add(new Diagnostic("More than one @else", element.getTextRange()));
        }
        return result;
    }

    private List<Diagnostic> missingEnd(PsiElement element, Class<? extends PsiElement> endType,
                                        String message) {
        return endType.isInstance(element.getLastChild()) ? List.of() : error(element, message);
    }

    private List<Diagnostic> parentCheck(PsiElement element, Class<? extends PsiElement> parentType,
                                         String message) {
        return parentType.isInstance(element.getParent()) ? List.of() : error(element, message);
    }

    private List<Diagnostic> error(PsiElement element, String message) {
        return List.of(new Diagnostic(message, element.getTextRange()));
    }

    record Diagnostic(@NotNull String message, @NotNull TextRange range) {
    }
}
