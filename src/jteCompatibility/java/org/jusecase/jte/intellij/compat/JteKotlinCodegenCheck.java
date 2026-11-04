package org.jusecase.jte.intellij.compat;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Isolated compatibility check: JTE's compiler must accept the template forms modelled by the plugin. */
public final class JteKotlinCodegenCheck {
    private JteKotlinCodegenCheck() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("kte-jte-codegen-");
        try {
            Path templates = Files.createDirectory(root.resolve("templates"));
            Path generated = Files.createDirectory(root.resolve("generated"));
            Files.createDirectory(templates.resolve("components"));
            Files.writeString(templates.resolve("components/card.kte"), """
                    @param title: String
                    @param content: gg.jte.Content
                    <article>${title}${content}</article>
                    """);
            Files.writeString(templates.resolve("page.kte"), """
                    @param title: String
                    @param values: List<String>
                    @if(title.isNotBlank())
                        @for(value in values)
                            $unsafe{value}
                        @else
                            ${values.size}
                        @endfor
                    @endif
                    @template.components.card(title = title, content = @`
                        ${title}
                    `)
                    """);

            TemplateEngine engine = TemplateEngine.create(
                    new DirectoryCodeResolver(templates), generated, ContentType.Html);
            List<String> generatedNames = engine.generateAll();
            if (generatedNames.size() != 2) {
                throw new AssertionError("Expected two JTE Kotlin outputs, got " + generatedNames);
            }

            String htmlKotlin = generatedKotlin(generated);

            Path plainGenerated = Files.createDirectory(root.resolve("plain-generated"));
            TemplateEngine plainEngine = TemplateEngine.create(
                    new DirectoryCodeResolver(templates), plainGenerated, ContentType.Plain);
            if (plainEngine.generateAll().size() != 2) {
                throw new AssertionError("Expected two plain JTE Kotlin outputs");
            }
            String plainKotlin = generatedKotlin(plainGenerated);

            for (String shape : List.of("if (title.isNotBlank())", "for (value in values)",
                    "writeUnsafeContent(value)", "writeUserContent(title)")) {
                requireContains(htmlKotlin, shape);
                requireContains(plainKotlin, shape);
            }
            requireContains(htmlKotlin, "render(jteOutput:gg.jte.html.HtmlTemplateOutput");
            requireContains(htmlKotlin, "object : gg.jte.html.HtmlContent");
            requireContains(htmlKotlin, "writeTo(jteOutput:gg.jte.html.HtmlTemplateOutput)");
            requireContains(plainKotlin, "render(jteOutput:gg.jte.TemplateOutput");
            requireContains(plainKotlin, "object : gg.jte.Content");
            requireContains(plainKotlin, "writeTo(jteOutput:gg.jte.TemplateOutput)");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static String generatedKotlin(Path directory) throws Exception {
        StringBuilder kotlin = new StringBuilder();
        try (var files = Files.walk(directory)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".kt")).toList()) {
                kotlin.append(Files.readString(path)).append('\n');
            }
        }
        return kotlin.toString();
    }

    private static void requireContains(String generated, String expected) {
        if (!generated.contains(expected)) {
            throw new AssertionError("Missing JTE Kotlin codegen shape '" + expected + "' in:\n" + generated);
        }
    }
}
