package org.jusecase.jte.intellij.language.template;


import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KteKotlinTypeTextTest {
    @Test
    public void testRawAndShortTypes() {
        assertEquals("List", KteKotlinTypeText.rawType("List<Profile?>?"));
        assertEquals("Profile", KteKotlinTypeText.shortName("com.example.Profile"));
    }
}
