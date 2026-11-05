package org.jusecase.jte.intellij.language.template;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.psi.JtePsiComma;
import org.jusecase.jte.intellij.language.psi.JtePsiContent;
import org.jusecase.jte.intellij.language.psi.JtePsiEquals;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;
import org.jusecase.jte.intellij.language.psi.JtePsiTemplate;
import org.jusecase.jte.intellij.language.psi.KtePsiParamName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class KteTemplateCallArguments {
    private KteTemplateCallArguments() {
    }

    @NotNull
    public static List<Argument> parse(@NotNull JtePsiTemplate template) {
        List<Argument> result = new ArrayList<>();
        Builder builder = new Builder(result);
        for (PsiElement child : template.getChildren()) {
            if (child instanceof JtePsiComma) {
                builder.flush();
            } else if (child instanceof KtePsiParamName paramName) {
                builder.setName(paramName);
            } else if (child instanceof JtePsiEquals equals) {
                builder.addEquals(equals);
            } else if (child instanceof JtePsiJavaInjection javaInjection) {
                builder.addValue(javaInjection);
            } else if (child instanceof JtePsiContent content) {
                builder.addContent(content);
            }
        }
        builder.flush();

        return result;
    }

    @NotNull
    public static Set<String> usedNamedParameters(@NotNull JtePsiTemplate template) {
        return parse(template)
                .stream()
                .map(Argument::name)
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.toSet());
    }

    private static final class Builder {
        private final List<Argument> result;
        private KtePsiParamName nameElement;
        private String valueText;
        private TextRange valueRange;
        private JtePsiContent content;
        private TextRange argumentRange;

        private Builder(@NotNull List<Argument> result) {
            this.result = result;
        }

        private void setName(@NotNull KtePsiParamName paramName) {
            if (nameElement != null || valueText != null || content != null) {
                flush();
            }
            nameElement = paramName;
            includeRange(paramName.getTextRange());
        }

        private void addEquals(@NotNull JtePsiEquals equals) {
            if (nameElement != null) {
                includeRange(equals.getTextRange());
            }
        }

        private void addValue(@NotNull JtePsiJavaInjection javaInjection) {
            String text = javaInjection.getText();
            int segmentStart = 0;
            boolean insideString = false;
            boolean insideRawString = false;
            boolean insideChar = false;
            int parenthesisDepth = 0;
            int bracketDepth = 0;
            int braceDepth = 0;
            int typeArgumentDepth = 0;
            for (int index = 0; index < text.length(); index++) {
                char current = text.charAt(index);
                if (insideRawString) {
                    if (text.startsWith("\"\"\"", index)) {
                        insideRawString = false;
                        index += 2;
                    }
                    continue;
                }
                if (insideString || insideChar) {
                    if (current == (insideString ? '"' : '\'') && !isEscaped(text, index)) {
                        insideString = false;
                        insideChar = false;
                    }
                    continue;
                }
                if (text.startsWith("\"\"\"", index)) {
                    insideRawString = true;
                    index += 2;
                    continue;
                }
                if (current == '"') {
                    insideString = true;
                } else if (current == '\'') {
                    insideChar = true;
                } else if (current == '(') {
                    parenthesisDepth++;
                } else if (current == ')' && parenthesisDepth > 0) {
                    parenthesisDepth--;
                } else if (current == '[') {
                    bracketDepth++;
                } else if (current == ']' && bracketDepth > 0) {
                    bracketDepth--;
                } else if (current == '{') {
                    braceDepth++;
                } else if (current == '}' && braceDepth > 0) {
                    braceDepth--;
                } else if (current == '<' &&
                        (typeArgumentDepth > 0 || startsTypeArguments(text, index))) {
                    typeArgumentDepth++;
                } else if (current == '>' && typeArgumentDepth > 0) {
                    typeArgumentDepth--;
                } else if (current == ',' && parenthesisDepth == 0 && bracketDepth == 0 &&
                        braceDepth == 0 && typeArgumentDepth == 0) {
                    addValueSegment(javaInjection, segmentStart, index);
                    flush();
                    segmentStart = index + 1;
                }
            }

            addValueSegment(javaInjection, segmentStart, text.length());
        }

        private boolean startsTypeArguments(@NotNull String text, int start) {
            // A '<' can also be a comparison; require an identifier before it and a plausible
            // continuation after its matching '>' before treating inner commas as type syntax.
            if (start == 0 || !Character.isJavaIdentifierPart(text.charAt(start - 1))) {
                return false;
            }

            int depth = 1;
            for (int index = start + 1; index < text.length(); index++) {
                char current = text.charAt(index);
                if (current == '<') {
                    depth++;
                } else if (current == '>') {
                    depth--;
                    if (depth == 0) {
                        int next = index + 1;
                        while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
                            next++;
                        }
                        return next == text.length() || "(.?[{:,)".indexOf(text.charAt(next)) >= 0;
                    }
                }
            }
            return false;
        }

        private boolean isEscaped(@NotNull String text, int offset) {
            int backslashes = 0;
            for (int index = offset - 1; index >= 0 && text.charAt(index) == '\\'; index--) {
                backslashes++;
            }
            return backslashes % 2 != 0;
        }

        private void addContent(@NotNull JtePsiContent contentElement) {
            if (valueText != null || content != null) {
                flush();
            }
            content = contentElement;
            includeRange(contentElement.getTextRange());
        }

        private void addValueSegment(@NotNull JtePsiJavaInjection javaInjection, int startOffset, int endOffset) {
            String text = javaInjection.getText();
            int trimmedStart = skipWhitespace(text, startOffset, endOffset);
            int trimmedEnd = trimEnd(text, trimmedStart, endOffset);
            if (trimmedStart >= trimmedEnd) {
                return;
            }

            if (valueText != null || content != null) {
                flush();
            }

            valueText = text.substring(trimmedStart, trimmedEnd);
            valueRange = new TextRange(
                    javaInjection.getTextRange().getStartOffset() + trimmedStart,
                    javaInjection.getTextRange().getStartOffset() + trimmedEnd
            );
            includeRange(valueRange);
        }

        private void flush() {
            if (nameElement == null && valueText == null && content == null) {
                reset();
                return;
            }

            result.add(new Argument(
                    nameElement == null ? null : nameElement.getName(),
                    nameElement,
                    valueText,
                    valueRange,
                    content,
                    argumentRange == null ? TextRange.EMPTY_RANGE : argumentRange
            ));
            reset();
        }

        private void reset() {
            nameElement = null;
            valueText = null;
            valueRange = null;
            content = null;
            argumentRange = null;
        }

        private void includeRange(@NotNull TextRange range) {
            argumentRange = argumentRange == null
                    ? range
                    : TextRange.create(
                    Math.min(argumentRange.getStartOffset(), range.getStartOffset()),
                    Math.max(argumentRange.getEndOffset(), range.getEndOffset())
            );
        }

        private int skipWhitespace(@NotNull String text, int startOffset, int endOffset) {
            int result = startOffset;
            while (result < endOffset && Character.isWhitespace(text.charAt(result))) {
                result++;
            }
            return result;
        }

        private int trimEnd(@NotNull String text, int startOffset, int endOffset) {
            int result = endOffset;
            while (result > startOffset && Character.isWhitespace(text.charAt(result - 1))) {
                result--;
            }
            return result;
        }
    }

    public record Argument(@Nullable String name,
                           @Nullable PsiElement nameElement,
                           @Nullable String valueText,
                           @Nullable TextRange valueRange,
                           @Nullable JtePsiContent content,
                           @NotNull TextRange argumentRange) {
        public boolean positional() {
            return name == null;
        }

        public boolean hasAssignment() {
            return valueText != null || content != null;
        }

        @NotNull
        public TextRange range() {
            if (nameElement != null) {
                return nameElement.getTextRange();
            }
            if (valueRange != null) {
                return valueRange;
            }
            if (content != null) {
                return content.getTextRange();
            }
            return TextRange.EMPTY_RANGE;
        }
    }
}
