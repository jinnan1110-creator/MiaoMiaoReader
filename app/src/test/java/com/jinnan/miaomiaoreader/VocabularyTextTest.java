package com.jinnan.miaomiaoreader;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VocabularyTextTest {
    @Test public void originalSentenceUsesTheSelectedOccurrence() {
        List<String> t=Arrays.asList("The","policy","changed.","This","policy","protects","forests.","Why?");
        assertEquals("This policy protects forests.",VocabularyText.sentence(t,4,4));
        assertEquals("The policy changed.",VocabularyText.sentence(t,1,1));
    }
    @Test public void phraseAndAbbreviationsKeepTheirContext() {
        List<String> t=Arrays.asList("Dr.","Green","works","in","spite","of","the","rain.","It","stops.");
        assertEquals("Dr. Green works in spite of the rain.",VocabularyText.sentence(t,3,5));
        assertEquals("",VocabularyText.sentence(t,-1,5));
    }
    @Test public void textWithoutAPeriodKeepsTheFullParagraph() {
        assertEquals("A forest of trees",VocabularyText.sentence(Arrays.asList("A","forest","of","trees"),1,1));
    }
    @Test public void exportRetainsChineseCommasQuotesAndLineBreaks() {
        assertEquals("\"policy\",\"政策，方针\",\"He said, \"\"go\"\".\nNext\"\r\n",
                VocabularyText.csvRow("policy","政策，方针","He said, \"go\".\nNext"));
        assertEquals("\"'=1+1\"",VocabularyText.csvCell("=1+1"));
        assertEquals("\"'  @SUM(A1)\"",VocabularyText.csvCell("  @SUM(A1)"));
    }
    @Test public void onlyOneEnglishWordCanBeSpoken() {
        assertTrue(VocabularyText.isSingleWord("Forests,"));
        assertTrue(VocabularyText.isSingleWord("don't"));
        assertTrue(VocabularyText.isSingleWord("well-known"));
        assertFalse(VocabularyText.isSingleWord("in spite of"));
        assertFalse(VocabularyText.isSingleWord("This is a sentence."));
        assertFalse(VocabularyText.isSingleWord("中文"));
    }
}
