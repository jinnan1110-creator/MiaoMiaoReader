package com.jinnan.miaomiaoreader;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class TextRulesTest {
    @Test public void wordFormsRetainExactCandidateFirst(){assertEquals("studies",TextRules.candidates("Studies,").get(0));assertTrue(TextRules.candidates("studies").contains("study"));assertTrue(TextRules.candidates("running").contains("run"));assertTrue(TextRules.candidates("children").contains("child"));assertTrue(TextRules.candidates("John’s").contains("john"));}
    @Test public void phraseLookupDoesNotChangeInternalWords(){assertEquals(Arrays.asList("in spite of"),TextRules.candidates(" In spite of! "));}
    @Test public void inlineGlossUsesCompactLabelOnly(){assertEquals("政策、方针",TextRules.compactGloss("n. 政策, 方针, 策略；n. 保险单"));}
    @Test public void columnOrderPreservesSpanningHeading(){List<LayoutOrder.Block<String>> b=Arrays.asList(new LayoutOrder.Block<>(50,0,950,50,"title"),new LayoutOrder.Block<>(530,80,950,180,"R1"),new LayoutOrder.Block<>(50,90,460,180,"L1"),new LayoutOrder.Block<>(50,200,460,300,"L2"),new LayoutOrder.Block<>(530,190,950,300,"R2"));List<String> out=new ArrayList<>();for(LayoutOrder.Block<String> x:LayoutOrder.sort(b,1000))out.add(x.value);assertEquals(Arrays.asList("title","L1","L2","R1","R2"),out);}
}
