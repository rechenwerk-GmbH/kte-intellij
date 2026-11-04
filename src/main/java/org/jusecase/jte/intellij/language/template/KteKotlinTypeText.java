package org.jusecase.jte.intellij.language.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KteKotlinTypeText {
    private static final Pattern TYPE_REFERENCE = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");
    private static final Set<String> KNOWN_TYPE_NAMES = Set.of(
            "Any", "Array", "Boolean", "Byte", "Char", "Collection", "Double", "Float", "Int", "Iterable",
            "List", "Long", "Map", "MutableList", "MutableMap", "MutableSet", "Nothing", "Sequence", "Set",
            "Short", "String", "Unit"
    );
    private static final Set<String> TYPE_KEYWORDS = Set.of("in", "out", "where");

    private KteKotlinTypeText() {
    }

    @NotNull
    public static String rewriteReferences(@NotNull String typeText, @NotNull UnaryOperator<String> rewrite) {
        Matcher matcher = TYPE_REFERENCE.matcher(typeText);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(rewrite.apply(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static boolean isKnownTypeName(@NotNull String name) {
        return KNOWN_TYPE_NAMES.contains(shortName(name));
    }

    public static boolean isTypeKeyword(@NotNull String name) {
        return TYPE_KEYWORDS.contains(name);
    }

    @NotNull
    public static String rawType(@NotNull String typeText) {
        String result = typeText.trim();
        int genericStart = result.indexOf('<');
        if (genericStart != -1) {
            result = result.substring(0, genericStart);
        }

        return result.replace("?", "").trim();
    }

    @NotNull
    public static String shortName(@NotNull String typeName) {
        int lastDot = typeName.lastIndexOf('.');
        return lastDot == -1 ? typeName : typeName.substring(lastDot + 1);
    }

    @Nullable
    public static String firstGenericArgument(@NotNull String typeText) {
        int start = typeText.indexOf('<');
        if (start == -1) {
            return null;
        }

        int depth = 0;
        for (int index = start + 1; index < typeText.length(); index++) {
            char c = typeText.charAt(index);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                if (depth == 0) {
                    return typeText.substring(start + 1, index).trim();
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                return typeText.substring(start + 1, index).trim();
            }
        }

        return null;
    }

    @NotNull
    public static List<String> genericArguments(@NotNull String typeText) {
        int start = typeText.indexOf('<');
        if (start == -1) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        int depth = 0;
        int argumentStart = start + 1;
        for (int index = start + 1; index < typeText.length(); index++) {
            char current = typeText.charAt(index);
            if (current == '<') {
                depth++;
            } else if (current == '>') {
                if (depth == 0) {
                    addGenericArgument(result, typeText, argumentStart, index);
                    return result;
                }
                depth--;
            } else if (current == ',' && depth == 0) {
                addGenericArgument(result, typeText, argumentStart, index);
                argumentStart = index + 1;
            }
        }

        return result;
    }

    private static void addGenericArgument(@NotNull List<String> result,
                                           @NotNull String typeText,
                                           int start,
                                           int end) {
        String argument = typeText.substring(start, end).trim();
        if (!argument.isEmpty()) {
            result.add(argument);
        }
    }
}
