package com.tuservidor.mcextremo.util;

import net.minecraft.text.Text;

public final class TextUtil {
    private TextUtil() {
    }

    public static String color(String input) {
        if (input == null) return "";
        return input.replace('&', '\u00A7');
    }

    public static Text literal(String input) {
        return Text.literal(color(input));
    }
}
