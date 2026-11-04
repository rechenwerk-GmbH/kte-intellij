package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

public final class KteKotlinProjection {
    private final String fileName;
    private final String text;
    private final List<KteKotlinSourceMapping> mappings;
    private final List<GeneratedCall> generatedCalls;

    public KteKotlinProjection(@NotNull String fileName, @NotNull String text, @NotNull List<KteKotlinSourceMapping> mappings) {
        this(fileName, text, mappings, List.of());
    }

    public KteKotlinProjection(@NotNull String fileName,
                               @NotNull String text,
                               @NotNull List<KteKotlinSourceMapping> mappings,
                               @NotNull List<GeneratedCall> generatedCalls) {
        this.fileName = fileName;
        this.text = text;
        List<KteKotlinSourceMapping> sorted = new ArrayList<>(mappings);
        sorted.sort(Comparator.comparingInt(mapping -> mapping.getKotlinRange().getStartOffset()));
        for (int index = 0; index < sorted.size(); index++) {
            TextRange current = sorted.get(index).getKotlinRange();
            if (current.getEndOffset() > text.length() ||
                    index > 0 && sorted.get(index - 1).getKotlinRange().getEndOffset() > current.getStartOffset()) {
                throw new IllegalArgumentException("Overlapping or out-of-bounds Kotlin source mapping");
            }
        }
        this.mappings = List.copyOf(sorted);
        for (GeneratedCall call : generatedCalls) {
            if (call.kotlinRange().isEmpty() || call.kotlinRange().getEndOffset() > text.length() ||
                    mapKotlinRangeToTemplate(call.kotlinRange()) != null) {
                throw new IllegalArgumentException("Generated call identity must remain outside copied source spans");
            }
        }
        this.generatedCalls = List.copyOf(generatedCalls);
    }

    @NotNull
    public String getFileName() {
        return fileName;
    }

    @NotNull
    public String getText() {
        return text;
    }

    @NotNull
    public List<KteKotlinSourceMapping> getMappings() {
        return mappings;
    }

    @NotNull
    public List<GeneratedCall> getGeneratedCalls() {
        return generatedCalls;
    }

    @Nullable
    public Integer mapKotlinOffsetToTemplate(int kotlinOffset) {
        for (KteKotlinSourceMapping mapping : mappings) {
            Integer templateOffset = mapping.mapKotlinOffsetToTemplate(kotlinOffset);
            if (templateOffset != null) {
                return templateOffset;
            }
        }

        return null;
    }

    @Nullable
    public Integer mapTemplateOffsetToKotlin(int templateOffset) {
        for (KteKotlinSourceMapping mapping : mappings) {
            Integer kotlinOffset = mapping.mapTemplateOffsetToKotlin(templateOffset);
            if (kotlinOffset != null) {
                return kotlinOffset;
            }
        }

        return null;
    }

    @Nullable
    public TextRange mapKotlinRangeToTemplate(@NotNull TextRange range) {
        for (KteKotlinSourceMapping mapping : mappings) {
            TextRange templateRange = mapping.mapKotlinRangeToTemplate(range);
            if (templateRange != null) {
                return templateRange;
            }
        }

        return null;
    }

    @Nullable
    public TextRange mapKotlinSourceEditRangeToTemplate(@NotNull TextRange range) {
        for (KteKotlinSourceMapping mapping : mappings) {
            if (mapping.getEditPolicy() != KteKotlinSourceMapping.EditPolicy.SOURCE_EDITABLE) {
                continue;
            }

            TextRange templateRange = mapping.mapKotlinRangeToTemplate(range);
            if (templateRange != null) {
                return templateRange;
            }
        }

        return null;
    }

    public boolean isKotlinRangeFullySourceEditable(@NotNull TextRange range) {
        return mapKotlinSourceEditRangeToTemplate(range) != null;
    }

    public boolean touchesGeneratedKotlin(@NotNull TextRange range) {
        return mapKotlinRangeToTemplate(range) == null && !isKotlinImportRange(range);
    }

    @Nullable
    public TextRange mapKotlinErrorRangeToTemplate(@NotNull TextRange range) {
        if (range.isEmpty()) {
            Integer templateOffset = mapKotlinOffsetToTemplate(range.getStartOffset());
            return templateOffset == null ? null : TextRange.from(templateOffset, 1);
        }
        return mapKotlinRangeToTemplate(range);
    }

    @Nullable
    public TextRange mapTemplateRangeToKotlin(@NotNull TextRange range) {
        for (KteKotlinSourceMapping mapping : mappings) {
            TextRange kotlinRange = mapping.mapTemplateRangeToKotlin(range);
            if (kotlinRange != null) {
                return kotlinRange;
            }
        }

        return null;
    }

    private boolean isKotlinImportRange(@NotNull TextRange range) {
        for (KteKotlinSourceMapping mapping : mappings) {
            if (mapping.getEditPolicy() != KteKotlinSourceMapping.EditPolicy.IMPORT_EDITABLE) {
                continue;
            }

            if (mapping.mapKotlinRangeToTemplate(range) != null) {
                return true;
            }
        }

        return false;
    }

    public record GeneratedCall(@NotNull TextRange kotlinRange, @NotNull TextRange templateNameRange) {
    }
}
