package org.jusecase.jte.intellij.language.kotlin;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import org.jusecase.jte.intellij.language.KtePluginFixtureSupport;

import java.util.Arrays;

/** Repeatable highlighting and completion samples. Timings are observational, not CI thresholds. */
public class KteHighlightingBenchmarkTest extends KtePluginFixtureSupport {
    public void testTwoHundredExpressionsAfterEdits() {
        StringBuilder template = new StringBuilder("@param title: String\n");
        for (int index = 0; index < 200; index++) {
            template.append("<p>${title}</p>\n");
        }
        myFixture.configureByText("benchmark.kte", template + "!{val marker = 0}\n");
        myFixture.doHighlighting();

        long[] millis = new long[3];
        for (int sample = 0; sample < millis.length; sample++) {
            int marker = sample + 1;
            WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                var document = myFixture.getEditor().getDocument();
                document.setText(template + "!{val marker = " + marker + "}\n");
                PsiDocumentManager.getInstance(getProject()).commitDocument(document);
            });
            long start = System.nanoTime();
            myFixture.doHighlighting();
            millis[sample] = (System.nanoTime() - start) / 1_000_000;
        }
        System.out.println("KTE highlighting edit samples (ms): " + Arrays.toString(millis));
    }

    public void testCompletionAfterManyExpressions() {
        int[] expressionCounts = {50, 50, 200, 200};
        long[] millis = new long[expressionCounts.length];
        for (int sample = 0; sample < expressionCounts.length; sample++) {
            StringBuilder template = new StringBuilder("@param title: String\n");
            for (int index = 0; index < expressionCounts[sample]; index++) {
                template.append("<p>${title}</p>\n");
            }
            template.append("${tit<caret>}\n");
            myFixture.configureByText("completion-" + expressionCounts[sample] + "-" + sample + ".kte",
                    template.toString());

            long start = System.nanoTime();
            LookupElement[] candidates = myFixture.completeBasic();
            millis[sample] = (System.nanoTime() - start) / 1_000_000;
            if (candidates == null) {
                assertTrue(myFixture.getFile().getText().endsWith("${title}\n"));
            } else {
                var names = lookupStrings(candidates);
                assertTrue(names.toString(), names.contains("title"));
            }
        }
        System.out.println("KTE completion at " + Arrays.toString(expressionCounts) +
                " expressions (ms): " + Arrays.toString(millis));
    }
}
