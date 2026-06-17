package org.jusecase.jte.intellij.language;

import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import org.jusecase.jte.intellij.language.parsing.TokenTypes;

public class KteTemplateDataElementType extends TemplateDataElementType {
    public KteTemplateDataElementType(Language language, TokenTypes tokenTypes) {
        super("KTE_TEMPLATE_DATA_HTML", language, tokenTypes.HTML_CONTENT(), tokenTypes.OUTER_ELEMENT_TYPE());
    }
}
