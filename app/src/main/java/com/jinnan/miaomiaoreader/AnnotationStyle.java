package com.jinnan.miaomiaoreader;

/** Persist stable names, so a palette change never changes a reader's saved choice. */
public final class AnnotationStyle {
    private AnnotationStyle() {}
    public static final String DEFAULT_COLOR = "honey";
    public static final int RED = 0xffc34444;
    public static final String[] COLORS = {"honey", "sage", "sky", "rose", "lavender", "apricot"};
    public static final String[] COLOR_LABELS = {"蜂蜜黄", "鼠尾草绿", "雾蓝", "蔷薇粉", "薰衣草紫", "杏橙"};
    public static final int[] COLOR_VALUES = {0xfff3df9d, 0xffc8ddc7, 0xffc5dce9, 0xffedcbd0, 0xffd9cfee, 0xfff1cfad};
    public static String color(String value) {
        for (String c : COLORS) if (c.equals(value)) return c;
        return DEFAULT_COLOR;
    }
    public static int colorValue(String value) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equals(value)) return COLOR_VALUES[i];
        return COLOR_VALUES[0];
    }
    public static String style(String value) {
        return "underline".equals(value) || "underline-double".equals(value) || "underline-wavy".equals(value)
                ? value : "highlight";
    }
    public static String label(String value) {
        switch (style(value)) {
            case "underline": return "红色单线";
            case "underline-double": return "红色双线";
            case "underline-wavy": return "红色波浪线";
            default: return "高亮";
        }
    }
    public static String colorLabel(String value) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equals(value)) return COLOR_LABELS[i];
        return COLOR_LABELS[0];
    }
}
