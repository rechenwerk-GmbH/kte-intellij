package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.psi.JtePsiJavaInjection;

import java.util.ArrayList;
import java.util.List;

/** Emits Kotlin text and records only spans copied exactly from the KTE source. */
final class KteKotlinProjectionWriter {
    private final String sourceText;
    private final String completionDummyIdentifier;
    private final StringBuilder text = new StringBuilder();
    private final List<KteKotlinSourceMapping> mappings = new ArrayList<>();

    KteKotlinProjectionWriter(@NotNull String sourceText, @NotNull String completionDummyIdentifier) {
        this.sourceText = sourceText;
        this.completionDummyIdentifier = completionDummyIdentifier;
    }

    void appendGenerated(@Nullable String generatedText) {
        if (generatedText != null) {
            text.append(generatedText);
        }
    }

    void appendCopiedText(@NotNull String copiedText,
                          @NotNull TextRange sourceRange,
                          @NotNull KteKotlinSourceMapping.Kind kind) {
        int kotlinStartOffset = text.length();
        text.append(copiedText);
        if (sourceRange.getEndOffset() <= sourceText.length() &&
                copiedText.length() == sourceRange.getLength() &&
                copiedText.equals(sourceRange.substring(sourceText))) {
            mappings.add(new KteKotlinSourceMapping(
                    kind, sourceRange, TextRange.from(kotlinStartOffset, copiedText.length())));
        }
    }

    void appendMappedPart(@Nullable String prefix,
                          @Nullable String suffix,
                          @Nullable JtePsiJavaInjection part,
                          @NotNull KteKotlinSourceMapping.Kind kind) {
        if (part != null) {
            appendMappedPartRange(prefix, suffix, part, kind, 0, part.getTextLength());
        }
    }

    void appendTrimmedMappedPart(@Nullable String prefix,
                                 @Nullable String suffix,
                                 @NotNull JtePsiJavaInjection part,
                                 @NotNull KteKotlinSourceMapping.Kind kind) {
        String partText = part.getText();
        int startOffset = skipWhitespace(partText, 0);
        int endOffset = trimEnd(partText, partText.length());
        appendMappedPartRange(prefix, suffix, part, kind, startOffset, endOffset);
    }

    void appendMappedPartRange(@Nullable String prefix,
                               @Nullable String suffix,
                               @NotNull JtePsiJavaInjection part,
                               @NotNull KteKotlinSourceMapping.Kind kind,
                               int partStartOffset,
                               int partEndOffset) {
        if (partStartOffset >= partEndOffset) {
            return;
        }

        appendGenerated(prefix);
        int kotlinStartOffset = text.length();
        String mappedText = normalizedMappedText(part.getText(), partStartOffset, partEndOffset);
        if (mappedText.isEmpty()) {
            return;
        }

        text.append(mappedText);
        TextRange kotlinRange = new TextRange(kotlinStartOffset, text.length());
        TextRange templateRange = TextRange.from(
                part.getTextRange().getStartOffset() + partStartOffset, mappedText.length());
        mappings.add(new KteKotlinSourceMapping(kind, templateRange, kotlinRange));
        appendGenerated(suffix);
    }

    @NotNull
    KteKotlinProjection finish(@NotNull String fileName) {
        String kotlinText = text.toString();
        for (KteKotlinSourceMapping mapping : mappings) {
            TextRange sourceRange = mapping.getTemplateRange();
            TextRange kotlinRange = mapping.getKotlinRange();
            if (sourceRange.getEndOffset() > sourceText.length() ||
                    !sourceText.regionMatches(sourceRange.getStartOffset(), kotlinText,
                            kotlinRange.getStartOffset(), sourceRange.getLength())) {
                throw new IllegalStateException("Projection copied text differs from its KTE source range");
            }
        }
        return new KteKotlinProjection(fileName, kotlinText, mappings);
    }

    @NotNull
    private String normalizedMappedText(@NotNull String partText, int partStartOffset, int partEndOffset) {
        String mappedText = partText.substring(partStartOffset, partEndOffset);
        int dummyOffset = mappedText.indexOf(completionDummyIdentifier);
        if (dummyOffset < 0) {
            return mappedText;
        }

        int dummyEndOffset = dummyOffset + completionDummyIdentifier.length();
        return mappedText.substring(0, Math.min(dummyEndOffset, mappedText.length()));
    }

    private static int skipWhitespace(@NotNull String value, int offset) {
        int result = offset;
        while (result < value.length() && Character.isWhitespace(value.charAt(result))) {
            result++;
        }
        return result;
    }

    private static int trimEnd(@NotNull String value, int endOffset) {
        int result = endOffset;
        while (result > 0 && Character.isWhitespace(value.charAt(result - 1))) {
            result--;
        }
        return result;
    }
}
