package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class KteKotlinSourceMapping {
    public enum Kind {
        IMPORT,
        PARAMETER,
        PARAMETER_DEFAULT_VALUE,
        OUTPUT_EXPRESSION,
        UNSAFE_OUTPUT_EXPRESSION,
        STATEMENT,
        IF_CONDITION,
        ELSE_IF_CONDITION,
        FOR_CONDITION,
        TEMPLATE_ARGUMENT_NAME,
        TEMPLATE_ARGUMENT_VALUE
    }

    private final Kind kind;
    private final TextRange templateRange;
    private final TextRange kotlinRange;

    public KteKotlinSourceMapping(@NotNull Kind kind, @NotNull TextRange templateRange, @NotNull TextRange kotlinRange) {
        if (templateRange.isEmpty() || templateRange.getLength() != kotlinRange.getLength()) {
            throw new IllegalArgumentException("A source mapping must copy a nonempty span exactly");
        }
        this.kind = kind;
        this.templateRange = templateRange;
        this.kotlinRange = kotlinRange;
    }

    @NotNull
    public Kind getKind() {
        return kind;
    }

    @NotNull
    public TextRange getTemplateRange() {
        return templateRange;
    }

    @NotNull
    public TextRange getKotlinRange() {
        return kotlinRange;
    }

    @Nullable
    public Integer mapKotlinOffsetToTemplate(int kotlinOffset) {
        if (!contains(kotlinRange, kotlinOffset)) {
            return null;
        }

        return templateRange.getStartOffset() + kotlinOffset - kotlinRange.getStartOffset();
    }

    @Nullable
    public Integer mapTemplateOffsetToKotlin(int templateOffset) {
        if (!contains(templateRange, templateOffset)) {
            return null;
        }

        return kotlinRange.getStartOffset() + templateOffset - templateRange.getStartOffset();
    }

    @Nullable
    public TextRange mapKotlinRangeToTemplate(@NotNull TextRange range) {
        if (!containsRangeForEdit(kotlinRange, range)) {
            return null;
        }

        int startOffset = templateRange.getStartOffset() + range.getStartOffset() - kotlinRange.getStartOffset();
        return new TextRange(startOffset, startOffset + range.getLength());
    }

    @Nullable
    public TextRange mapTemplateRangeToKotlin(@NotNull TextRange range) {
        if (range.getStartOffset() < templateRange.getStartOffset() || range.getEndOffset() > templateRange.getEndOffset()) {
            return null;
        }

        int startOffset = kotlinRange.getStartOffset() + range.getStartOffset() - templateRange.getStartOffset();
        return new TextRange(startOffset, startOffset + range.getLength());
    }

    private boolean contains(TextRange range, int offset) {
        return offset >= range.getStartOffset() && offset < range.getEndOffset();
    }

    private static boolean containsRangeForEdit(@NotNull TextRange container, @NotNull TextRange range) {
        return range.getStartOffset() >= container.getStartOffset() &&
                range.getEndOffset() <= container.getEndOffset();
    }

}
