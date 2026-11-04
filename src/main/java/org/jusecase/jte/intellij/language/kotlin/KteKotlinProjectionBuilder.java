package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jusecase.jte.intellij.language.psi.*;
import org.jusecase.jte.intellij.language.template.KteTemplateSignatureService;
import org.jusecase.jte.intellij.language.template.KteTemplateSignatureSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class KteKotlinProjectionBuilder {
    private static final String COMPLETION_DUMMY_IDENTIFIER = CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED;

    @NotNull
    public KteKotlinProjection build(@NotNull PsiFile templateFile) {
        KtePsiJavaContent host = PsiTreeUtil.findChildOfType(templateFile, KtePsiJavaContent.class);
        if (host == null) {
            return new KteKotlinProjection(syntheticFileName(templateFile), "", List.of());
        }

        Builder builder = new Builder(templateFile, host);
        return builder.build();
    }

    private static String syntheticFileName(@NotNull PsiFile templateFile) {
        String fileName = templateFile.getName();
        if (fileName.endsWith(".kte")) {
            fileName = fileName.substring(0, fileName.length() - ".kte".length());
        }

        return fileName.replaceAll("[^A-Za-z0-9_.$-]", "_") + ".synthetic.kt";
    }

    private static final class Builder {
        private final PsiFile templateFile;
        private final KtePsiJavaContent host;
        private final StringBuilder text = new StringBuilder();
        private final List<KteKotlinSourceMapping> mappings = new ArrayList<>();
        private final List<KteKotlinProjection.GeneratedCall> generatedCalls = new ArrayList<>();
        private final Map<String, TemplateStub> templateStubs = new LinkedHashMap<>();
        private final Set<String> generatedNames = new HashSet<>();
        private final KteKotlinTypeRenderer typeRenderer;
        private final KteTemplateSignatureSource.Signature sourceSignature;
        private final String className;
        private final String fallbackCallName;
        private int forElseCounter;

        private Builder(PsiFile templateFile, KtePsiJavaContent host) {
            this.templateFile = templateFile;
            this.host = host;
            this.typeRenderer = new KteKotlinTypeRenderer(templateFile);
            this.sourceSignature = KteTemplateSignatureSource.resolve(templateFile);
            this.className = uniqueName("DummyTemplate");
            this.fallbackCallName = uniqueName("dummyCall");
        }

        private KteKotlinProjection build() {
            List<JtePsiParam> params = directChildren(JtePsiParam.class);

            appendImports();
            collectTemplateStubs();
            appendTemplateStubs();

            if (!params.isEmpty() || hasSupportedBody(host)) {
                appendGenerated("@Suppress(\"unused\", \"UNUSED_PARAMETER\")\n" +
                        "class " + className + " {\n" +
                        " @Suppress(\"unused\")\n" +
                        " fun render(jteOutput: gg.jte.TemplateOutput");
                appendParameters(params);
                appendGenerated(") {\n");
                appendTemplateBody();
                appendGenerated("\n}\nfun " + fallbackCallName + "(vararg o: Any?) { o.hashCode() }\n}\n");
            }

            String sourceText = templateFile.getText();
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
            return new KteKotlinProjection(syntheticFileName(templateFile), kotlinText, mappings, generatedCalls);
        }

        private void appendImports() {
            for (JtePsiImport importElement : directChildren(JtePsiImport.class)) {
                JtePsiJavaInjection part = PsiTreeUtil.getChildOfType(importElement, JtePsiJavaInjection.class);
                appendMappedPart("import ", "\n", part, KteKotlinSourceMapping.Kind.IMPORT);
            }
        }

        private void collectTemplateStubs() {
            for (JtePsiTemplate template : PsiTreeUtil.findChildrenOfType(host, JtePsiTemplate.class)) {
                resolveTemplateStub(template);
            }
        }

        private void appendTemplateStubs() {
            for (TemplateStub stub : templateStubs.values()) {
                appendGenerated("@Suppress(\"unused\", \"UNUSED_PARAMETER\")\n");
                appendGenerated("fun " + stub.functionName() + "(");
                List<KteTemplateSignatureService.Parameter> parameters = stub.signature().parameters();
                for (int index = 0; index < parameters.size(); index++) {
                    appendGenerated(stubParameter(parameters.get(index)));
                    appendGenerated(index + 1 < parameters.size() ? ", " : "");
                }
                appendGenerated(") {}\n");
            }
        }

        @NotNull
        private String stubParameter(@NotNull KteTemplateSignatureService.Parameter parameter) {
            StringBuilder result = new StringBuilder();
            if (parameter.vararg()) {
                result.append("vararg ");
            }
            result.append(parameter.name()).append(": ").append(typeRenderer.renderChildSignatureType(parameter));
            if (parameter.defaulted() && !parameter.vararg()) {
                result.append(" = TODO()");
            }
            return result.toString();
        }

        private void appendParameters(List<JtePsiParam> params) {
            if (!params.isEmpty()) {
                appendGenerated(", ");
            }
            for (int index = 0; index < params.size(); index++) {
                appendParameter(params.get(index));
                String suffix = index + 1 < params.size() ? ", " : "";
                appendGenerated(suffix);
            }
        }

        private void appendParameter(@NotNull JtePsiParam param) {
            JtePsiJavaInjection part = PsiTreeUtil.getChildOfType(param, JtePsiJavaInjection.class);
            if (part == null) {
                return;
            }

            String partText = part.getText();
            KteTemplateSignatureSource.Parameter sourceParameter = sourceSignature.parameters().stream()
                    .filter(parameter -> parameter.sourceElement() == param)
                    .findFirst().orElse(null);
            TextRange defaultValueRange = sourceParameter == null ? null : sourceParameter.defaultValueRange();
            if (defaultValueRange == null || !part.getTextRange().contains(defaultValueRange)) {
                appendMappedPart(null, null, part, KteKotlinSourceMapping.Kind.PARAMETER);
                return;
            }

            int declarationEnd = sourceParameter.declarationRange().getEndOffset() - part.getTextRange().getStartOffset();
            int valueStart = defaultValueRange.getStartOffset() - part.getTextRange().getStartOffset();
            int valueEnd = defaultValueRange.getEndOffset() - part.getTextRange().getStartOffset();
            appendMappedPartRange(null, null, part, KteKotlinSourceMapping.Kind.PARAMETER, 0, declarationEnd);
            appendGenerated(partText.substring(declarationEnd, valueStart));
            appendMappedPartRange(null, null, part, KteKotlinSourceMapping.Kind.PARAMETER_DEFAULT_VALUE, valueStart, valueEnd);
            appendGenerated(partText.substring(valueEnd));
        }

        private void appendTemplateBody() {
            for (PsiElement child : host.getChildren()) {
                if (!(child instanceof JtePsiImport) && !(child instanceof JtePsiParam)) {
                    processTemplateBody(child);
                }
            }
        }

        private void processTemplateBody(PsiElement child) {
            if (child instanceof JtePsiOutput output) {
                KteKotlinSourceMapping.Kind kind = outputKind(output);
                String writer = kind == KteKotlinSourceMapping.Kind.UNSAFE_OUTPUT_EXPRESSION
                        ? "jteOutput.writeUnsafeContent(" : "jteOutput.writeUserContent(";
                appendContentAwareKotlinPart(writer, ")\n", output, kind);
            } else if (child instanceof JtePsiStatement) {
                appendContentAwareKotlinPart(null, "\n", child, KteKotlinSourceMapping.Kind.STATEMENT);
            } else if (child instanceof JtePsiIf) {
                JtePsiJavaInjection part = PsiTreeUtil.getChildOfType(child, JtePsiJavaInjection.class);
                appendMappedPart("if (", ") {\n", part, KteKotlinSourceMapping.Kind.IF_CONDITION);

                if (part != null) {
                    for (PsiElement sibling = part.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
                        processTemplateBody(sibling);
                    }
                }
            } else if (child instanceof JtePsiElseIf) {
                JtePsiJavaInjection part = PsiTreeUtil.getChildOfType(child, JtePsiJavaInjection.class);
                appendMappedPart("} else if (", ") {\n", part, KteKotlinSourceMapping.Kind.ELSE_IF_CONDITION);
            } else if (child instanceof JtePsiElse) {
                appendGenerated("\n} else {\n");
            } else if (child instanceof JtePsiEndIf) {
                appendGenerated("}\n");
            } else if (child instanceof JtePsiFor forElement) {
                appendFor(forElement);
            } else if (child instanceof JtePsiEndFor) {
                appendGenerated("}\n");
            } else if (child instanceof JtePsiTemplate) {
                appendTemplateCall((JtePsiTemplate) child);
            } else if (child instanceof JtePsiBlock) {
                for (PsiElement element : child.getChildren()) {
                    processTemplateBody(element);
                }
            }
        }

        private void appendTemplateCall(@NotNull JtePsiTemplate template) {
            TemplateStub stub = resolveTemplateStub(template);
            if (stub == null) {
                appendContentAwareKotlinPart(fallbackCallName + "(", ")\n", template,
                        KteKotlinSourceMapping.Kind.TEMPLATE_ARGUMENT_VALUE);
                return;
            }

            appendTemplateCallTarget(template, stub.functionName());
            appendGenerated("(");
            for (PsiElement element : template.getChildren()) {
                if (element instanceof KtePsiParamName paramName) {
                    String name = paramName.getName();
                    if (name != null && !name.isBlank()) {
                        appendMappedGenerated(name, paramName.getTextRange(),
                                KteKotlinSourceMapping.Kind.TEMPLATE_ARGUMENT_NAME);
                        appendGenerated(" = ");
                    }
                } else if (element instanceof JtePsiJavaInjection javaInjection) {
                    appendMappedPart(null, null, javaInjection, KteKotlinSourceMapping.Kind.TEMPLATE_ARGUMENT_VALUE);
                } else if (element instanceof JtePsiContent content) {
                    appendContent(null, null, content);
                } else if (element instanceof JtePsiComma) {
                    appendGenerated(",");
                }
            }
            appendGenerated(")\n");
        }

        private void appendMappedGenerated(@NotNull String generatedText,
                                           @NotNull TextRange templateRange,
                                           @NotNull KteKotlinSourceMapping.Kind kind) {
            int kotlinStartOffset = text.length();
            appendGenerated(generatedText);
            if (generatedText.length() == templateRange.getLength() &&
                    generatedText.equals(templateFile.getText().substring(
                            templateRange.getStartOffset(), templateRange.getEndOffset()))) {
                mappings.add(new KteKotlinSourceMapping(
                        kind,
                        templateRange,
                        TextRange.from(kotlinStartOffset, generatedText.length())
                ));
            }
        }

        private void appendTemplateCallTarget(@NotNull JtePsiTemplate template, @NotNull String functionName) {
            // A generated function name is a semantic stand-in, not a copy of the @template path.
            // Mapping a shared prefix here would make navigation and edits target unrelated text.
            int kotlinStartOffset = text.length();
            appendGenerated(functionName);
            JtePsiTemplateName templateName = JtePsiUtil.getLastChildOfType(template, JtePsiTemplateName.class);
            if (templateName != null && !templateName.getTextRange().isEmpty()) {
                generatedCalls.add(new KteKotlinProjection.GeneratedCall(
                        TextRange.from(kotlinStartOffset, functionName.length()), templateName.getTextRange()));
            }
        }

        @Nullable
        private TemplateStub resolveTemplateStub(@NotNull JtePsiTemplate template) {
            JtePsiTemplateName templateName = JtePsiUtil.getLastChildOfType(template, JtePsiTemplateName.class);
            if (templateName == null) {
                return null;
            }

            PsiFile targetTemplate = templateName.resolveFile();
            if (targetTemplate == null || !KteTemplateSignatureService.isKteTemplate(targetTemplate)) {
                return null;
            }

            KteTemplateSignatureService.TemplateSignature signature =
                    KteTemplateSignatureService.resolveForSyntheticStub(targetTemplate);
            String key = templateKey(targetTemplate);
            TemplateStub existing = templateStubs.get(key);
            if (existing != null) {
                return existing;
            }

            TemplateStub stub = new TemplateStub(templateFunctionName(key), signature);
            templateStubs.put(key, stub);
            return stub;
        }

        @NotNull
        private String templateKey(@NotNull PsiFile targetTemplate) {
            VirtualFile virtualFile = targetTemplate.getVirtualFile();
            if (virtualFile != null) {
                return virtualFile.getPath();
            }
            return targetTemplate.getName();
        }

        @NotNull
        private String templateFunctionName(@NotNull String key) {
            return uniqueName("__jte_template_" + key.replaceAll("[^A-Za-z0-9_]", "_"));
        }

        @NotNull
        private String uniqueName(@NotNull String base) {
            String result = base;
            int suffix = 0;
            while (templateFile.getText().contains(result) || !generatedNames.add(result)) {
                result = base + "_" + ++suffix;
            }
            return result;
        }

        private void appendFor(@NotNull JtePsiFor forElement) {
            JtePsiJavaInjection part = PsiTreeUtil.getChildOfType(forElement, JtePsiJavaInjection.class);
            if (part == null) {
                return;
            }

            ForBody body = forBody(part);
            if (body.elseElements().isEmpty()) {
                appendMappedPart("for (", ") {\n", part, KteKotlinSourceMapping.Kind.FOR_CONDITION);
                processTemplateBody(body.loopElements());
                appendGenerated("}\n");
                return;
            }

            String emptyFlagName = uniqueName("__jteForElse" + forElseCounter++);
            appendGenerated("var " + emptyFlagName + " = true\n");
            appendMappedPart("for (", ") {\n", part, KteKotlinSourceMapping.Kind.FOR_CONDITION);
            appendGenerated(emptyFlagName + " = false\n");
            processTemplateBody(body.loopElements());
            appendGenerated("}\nif (" + emptyFlagName + ") {\n");
            processTemplateBody(body.elseElements());
            appendGenerated("}\n");
        }

        private ForBody forBody(@NotNull JtePsiJavaInjection part) {
            List<PsiElement> loopElements = new ArrayList<>();
            List<PsiElement> elseElements = new ArrayList<>();
            boolean inElse = false;
            for (PsiElement sibling = part.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
                if (sibling instanceof JtePsiEndFor) {
                    break;
                }
                if (sibling instanceof JtePsiElse) {
                    inElse = true;
                    continue;
                }

                if (inElse) {
                    elseElements.add(sibling);
                } else {
                    loopElements.add(sibling);
                }
            }

            return new ForBody(loopElements, elseElements);
        }

        private void processTemplateBody(@NotNull List<PsiElement> elements) {
            for (PsiElement element : elements) {
                processTemplateBody(element);
            }
        }

        private void appendContentAwareKotlinPart(String prefix, String suffix, PsiElement child, KteKotlinSourceMapping.Kind kind) {
            List<PsiElement> children = kotlinArgumentChildren(child);
            if (children.isEmpty()) {
                appendGenerated((prefix == null ? "" : prefix) + (suffix == null ? "" : suffix));
                return;
            }

            boolean prefixWritten = false;
            PsiElement last = children.isEmpty() ? null : children.get(children.size() - 1);
            for (PsiElement element : children) {
                String currentPrefix = null;
                if (!prefixWritten) {
                    currentPrefix = prefix;
                    prefixWritten = true;
                }

                String currentSuffix = null;
                if (element == last) {
                    currentSuffix = suffix;
                } else if (hasCommaAfter(element) && !hasCompletionRecoveryCommaAfter(element)) {
                    currentSuffix = ",";
                }

                if (element instanceof JtePsiJavaInjection javaInjection) {
                    if (kind == KteKotlinSourceMapping.Kind.STATEMENT) {
                        appendTrimmedMappedPart(currentPrefix, currentSuffix, javaInjection, kind);
                    } else {
                        appendMappedPart(currentPrefix, currentSuffix, javaInjection, kind);
                    }
                } else if (element instanceof JtePsiContent content) {
                    appendContent(currentPrefix, currentSuffix, content);
                }
            }
        }

        @NotNull
        private List<PsiElement> kotlinArgumentChildren(@NotNull PsiElement child) {
            List<PsiElement> result = new ArrayList<>();
            for (PsiElement element : child.getChildren()) {
                if (element instanceof JtePsiJavaInjection || element instanceof JtePsiContent) {
                    result.add(element);
                }
            }

            return result;
        }

        private boolean hasCommaAfter(@NotNull PsiElement element) {
            for (PsiElement sibling = element.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
                if (sibling instanceof JtePsiComma) {
                    return true;
                }
                if (sibling instanceof JtePsiJavaInjection ||
                        sibling instanceof JtePsiContent ||
                        sibling instanceof KtePsiParamName) {
                    return false;
                }
            }

            return false;
        }

        private void appendContent(String prefix, String suffix, JtePsiContent element) {
            appendGenerated((prefix == null ? "" : prefix) +
                    "object : gg.jte.Content { override fun writeTo(jteOutput: gg.jte.TemplateOutput) {");

            JtePsiBlock block = PsiTreeUtil.getChildOfType(element, JtePsiBlock.class);
            if (block != null) {
                for (PsiElement child : block.getChildren()) {
                    processTemplateBody(child);
                }
            }

            appendGenerated("}}" + (suffix == null ? "" : suffix));
        }

        private void appendMappedPart(String prefix, String suffix, JtePsiJavaInjection part, KteKotlinSourceMapping.Kind kind) {
            if (part == null) {
                return;
            }

            appendMappedPartRange(prefix, suffix, part, kind, 0, part.getTextLength());
        }

        private void appendTrimmedMappedPart(String prefix, String suffix, JtePsiJavaInjection part, KteKotlinSourceMapping.Kind kind) {
            String partText = part.getText();
            int startOffset = skipWhitespace(partText, 0);
            int endOffset = trimEnd(partText, partText.length());
            appendMappedPartRange(prefix, suffix, part, kind, startOffset, endOffset);
        }

        private void appendMappedPartRange(String prefix,
                                           String suffix,
                                           JtePsiJavaInjection part,
                                           KteKotlinSourceMapping.Kind kind,
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
            TextRange templateRange = new TextRange(
                    part.getTextRange().getStartOffset() + partStartOffset,
                    part.getTextRange().getStartOffset() + partStartOffset + mappedText.length()
            );
            mappings.add(new KteKotlinSourceMapping(kind, templateRange, kotlinRange));
            appendGenerated(suffix);
        }

        @NotNull
        private String normalizedMappedText(@NotNull String partText, int partStartOffset, int partEndOffset) {
            String mappedText = partText.substring(partStartOffset, partEndOffset);
            int dummyOffset = mappedText.indexOf(COMPLETION_DUMMY_IDENTIFIER);
            if (dummyOffset < 0) {
                return mappedText;
            }

            int dummyEndOffset = dummyOffset + COMPLETION_DUMMY_IDENTIFIER.length();
            return mappedText.substring(0, Math.min(dummyEndOffset, mappedText.length()));
        }

        private boolean hasCompletionRecoveryCommaAfter(@NotNull PsiElement element) {
            if (!element.getText().contains(COMPLETION_DUMMY_IDENTIFIER) ||
                    PsiTreeUtil.getParentOfType(element, JtePsiOutput.class, false) == null) {
                return false;
            }

            PsiElement sibling = nextNonWhitespace(element.getNextSibling());
            if (!(sibling instanceof JtePsiComma)) {
                return false;
            }

            return nextNonWhitespace(sibling.getNextSibling()) instanceof JtePsiOutputEnd;
        }

        @Nullable
        private PsiElement nextNonWhitespace(@Nullable PsiElement element) {
            PsiElement current = element;
            while (current instanceof PsiWhiteSpace) {
                current = current.getNextSibling();
            }
            return current;
        }

        private void appendGenerated(String generatedText) {
            if (generatedText != null) {
                text.append(generatedText);
            }
        }

        private boolean hasSupportedBody(PsiElement element) {
            for (PsiElement child : element.getChildren()) {
                if (child instanceof JtePsiOutput ||
                        child instanceof JtePsiStatement ||
                        child instanceof JtePsiIf ||
                        child instanceof JtePsiElseIf ||
                        child instanceof JtePsiElse ||
                        child instanceof JtePsiEndIf ||
                        child instanceof JtePsiFor ||
                        child instanceof JtePsiEndFor ||
                        child instanceof JtePsiTemplate) {
                    return true;
                }

                if (child instanceof JtePsiBlock && hasSupportedBody(child)) {
                    return true;
                }
            }

            return false;
        }

        private KteKotlinSourceMapping.Kind outputKind(@NotNull JtePsiOutput output) {
            JtePsiOutputBegin begin = PsiTreeUtil.getChildOfType(output, JtePsiOutputBegin.class);
            if (begin != null && begin.getText().startsWith("$unsafe")) {
                return KteKotlinSourceMapping.Kind.UNSAFE_OUTPUT_EXPRESSION;
            }

            return KteKotlinSourceMapping.Kind.OUTPUT_EXPRESSION;
        }

        private int skipWhitespace(@NotNull String value, int offset) {
            int result = offset;
            while (result < value.length() && Character.isWhitespace(value.charAt(result))) {
                result++;
            }

            return result;
        }

        private int trimEnd(@NotNull String value, int endOffset) {
            int result = endOffset;
            while (result > 0 && Character.isWhitespace(value.charAt(result - 1))) {
                result--;
            }

            return result;
        }

        private <T extends PsiElement> List<T> directChildren(Class<T> childType) {
            List<T> result = new ArrayList<>();
            for (PsiElement child : host.getChildren()) {
                if (childType.isInstance(child)) {
                    result.add(childType.cast(child));
                }
            }

            return result;
        }

        private record ForBody(@NotNull List<PsiElement> loopElements, @NotNull List<PsiElement> elseElements) {
        }

        private record TemplateStub(@NotNull String functionName,
                                    @NotNull KteTemplateSignatureService.TemplateSignature signature) {
        }
    }
}
