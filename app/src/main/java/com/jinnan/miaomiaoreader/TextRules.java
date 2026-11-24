package com.jinnan.miaomiaoreader;

import java.util.*;

/** Deterministic dictionary lookup candidates; the displayed source is never rewritten. */
public final class TextRules {
    private static final Map<String,String> IRREGULAR = new HashMap<>();
    static {
        String[][] pairs={{"children","child"},{"men","man"},{"women","woman"},{"people","person"},{"mice","mouse"},{"teeth","tooth"},{"feet","foot"},{"went","go"},{"gone","go"},{"was","be"},{"were","be"},{"is","be"},{"are","be"},{"been","be"},{"had","have"},{"did","do"},{"done","do"},{"better","good"},{"best","good"},{"worse","bad"},{"worst","bad"},{"found","find"},{"thought","think"},{"brought","bring"},{"bought","buy"},{"taught","teach"},{"caught","catch"},{"made","make"},{"took","take"},{"taken","take"},{"shown","show"},{"saw","see"},{"seen","see"},{"grew","grow"},{"grown","grow"},{"wrote","write"},{"written","write"},{"held","hold"},{"lost","lose"},{"left","leave"},{"built","build"},{"said","say"}};
        for(String[] p:pairs)IRREGULAR.put(p[0],p[1]);
    }
    public static String normalize(String text) {
        return text.toLowerCase(Locale.US).replace('’','\'').replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "").replaceAll("\\s+", " ").trim();
    }
    public static List<String> candidates(String text) {
        String w=normalize(text); LinkedHashSet<String> out=new LinkedHashSet<>();
        if(w.isEmpty())return new ArrayList<>();
        out.add(w);
        if(w.endsWith("'s"))out.add(w.substring(0,w.length()-2));
        if(IRREGULAR.containsKey(w))out.add(IRREGULAR.get(w));
        if(!w.contains(" ")&&w.length()>3){
            if(w.endsWith("ies"))out.add(w.substring(0,w.length()-3)+"y");
            if(w.endsWith("ied"))out.add(w.substring(0,w.length()-3)+"y");
            if(w.endsWith("ing")&&w.length()>5){String b=w.substring(0,w.length()-3);out.add(b);out.add(b+"e");undouble(out,b);}
            if(w.endsWith("ed")&&w.length()>4){String b=w.substring(0,w.length()-2);out.add(b);out.add(b+"e");undouble(out,b);}
            if(w.endsWith("es")){out.add(w.substring(0,w.length()-2));out.add(w.substring(0,w.length()-1));}
            else if(w.endsWith("s")&&!w.endsWith("ss"))out.add(w.substring(0,w.length()-1));
        }
        return new ArrayList<>(out);
    }
    private static void undouble(Set<String> out,String b){int n=b.length();if(n>2&&b.charAt(n-1)==b.charAt(n-2))out.add(b.substring(0,n-1));}
    /** Only the inline label is compact; full dictionary fields remain available in the dialog. */
    public static String compactGloss(String full){
        String s=full.replaceAll("(?m)^(?:[a-z]+\\.\\s*)+","").split("[；;\\n]",2)[0].trim();
        String[] parts=s.split("[,，]");
        if(parts.length>2)s=parts[0].trim()+"、"+parts[1].trim();
        return s.length()>28?s.substring(0,28)+"…":s;
    }
}
