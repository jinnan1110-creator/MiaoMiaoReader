package com.jinnan.miaomiaoreader;

import java.util.*;

/** Text-only helpers shared by annotation capture and UTF-8 CSV export. */
public final class VocabularyText {
    private VocabularyText() {}
    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList(
            "mr.", "mrs.", "ms.", "dr.", "prof.", "st.", "vs.", "e.g.", "i.e.", "etc."));

    private static boolean sentenceEnd(String token) {
        String s = token.replaceAll("[\\\"'”’）)\\]]+$", "");
        if (ABBREVIATIONS.contains(s.toLowerCase(Locale.US)) || s.matches("(?:[A-Za-z]\\.)+")) return false;
        return s.matches(".*[.!?…]$");
    }
    public static String sentence(List<String> tokens, int start, int end) {
        if (tokens.isEmpty() || start < 0 || end < start || end >= tokens.size()) return "";
        int left = start, right = end;
        while (left > 0 && !sentenceEnd(tokens.get(left - 1))) left--;
        while (right + 1 < tokens.size() && !sentenceEnd(tokens.get(right))) right++;
        return String.join(" ", tokens.subList(left, right + 1));
    }
    public static boolean isSingleWord(String value) {
        return TextRules.normalize(value).matches("[a-z]+(?:['-][a-z]+)*");
    }
    /** RFC 4180 quoting plus spreadsheet formula protection for user-entered fields. */
    public static String csvCell(String value) {
        String s = value == null ? "" : value;
        String trimmed = s.replaceFirst("^\\s+", "");
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    public static String csvRow(String... fields) {
        List<String> cells = new ArrayList<>();
        for (String field : fields) cells.add(csvCell(field));
        return String.join(",", cells) + "\r\n";
    }
}
