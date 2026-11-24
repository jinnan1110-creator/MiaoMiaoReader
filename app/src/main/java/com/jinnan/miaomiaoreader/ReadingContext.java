package com.jinnan.miaomiaoreader;

import org.json.*;
import java.util.*;

public final class ReadingContext {
    private ReadingContext() {}
    public static String sentence(JSONObject page, int start, int end) throws JSONException {
        if (page == null) return "";
        JSONArray blocks = page.getJSONArray("blocks");
        Map<Integer, String> all = new TreeMap<>();
        Map<Integer, String> selectedBlock = null;
        for (int b = 0; b < blocks.length(); b++) {
            Map<Integer, String> local = new TreeMap<>();
            JSONArray tokens = blocks.getJSONObject(b).getJSONArray("tokens");
            for (int t = 0; t < tokens.length(); t++) {
                JSONObject token = tokens.getJSONObject(t);
                local.put(token.getInt("i"), token.getString("t"));
            }
            all.putAll(local);
            if (local.containsKey(start) && local.containsKey(end)) selectedBlock = local;
        }
        // OCR and TXT paragraph boundaries remain boundaries when the selection fits a block.
        Map<Integer, String> source = selectedBlock == null ? all : selectedBlock;
        List<Integer> ids = new ArrayList<>(source.keySet());
        return VocabularyText.sentence(new ArrayList<>(source.values()), ids.indexOf(start), ids.indexOf(end));
    }
}
