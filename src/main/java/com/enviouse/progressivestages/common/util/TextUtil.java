package com.enviouse.progressivestages.common.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing color codes and formatting text
 */
public final class TextUtil {

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-fk-or])");

    private static final Map<Character, ChatFormatting> COLOR_MAP = new HashMap<>();

    static {
        // Colors
        COLOR_MAP.put('0', ChatFormatting.BLACK);
        COLOR_MAP.put('1', ChatFormatting.DARK_BLUE);
        COLOR_MAP.put('2', ChatFormatting.DARK_GREEN);
        COLOR_MAP.put('3', ChatFormatting.DARK_AQUA);
        COLOR_MAP.put('4', ChatFormatting.DARK_RED);
        COLOR_MAP.put('5', ChatFormatting.DARK_PURPLE);
        COLOR_MAP.put('6', ChatFormatting.GOLD);
        COLOR_MAP.put('7', ChatFormatting.GRAY);
        COLOR_MAP.put('8', ChatFormatting.DARK_GRAY);
        COLOR_MAP.put('9', ChatFormatting.BLUE);
        COLOR_MAP.put('a', ChatFormatting.GREEN);
        COLOR_MAP.put('b', ChatFormatting.AQUA);
        COLOR_MAP.put('c', ChatFormatting.RED);
        COLOR_MAP.put('d', ChatFormatting.LIGHT_PURPLE);
        COLOR_MAP.put('e', ChatFormatting.YELLOW);
        COLOR_MAP.put('f', ChatFormatting.WHITE);
        // Formatting
        COLOR_MAP.put('k', ChatFormatting.OBFUSCATED);
        COLOR_MAP.put('l', ChatFormatting.BOLD);
        COLOR_MAP.put('m', ChatFormatting.STRIKETHROUGH);
        COLOR_MAP.put('n', ChatFormatting.UNDERLINE);
        COLOR_MAP.put('o', ChatFormatting.ITALIC);
        COLOR_MAP.put('r', ChatFormatting.RESET);
    }

    private TextUtil() {
        // Prevent instantiation
    }

    /**
     * Parse a string with color codes (&c, &l, etc.) into a Component
     * @param text Text with color codes
     * @return Formatted Component
     */
    public static Component parseColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        MutableComponent result = Component.empty();
        Style currentStyle = Style.EMPTY;

        Matcher matcher = COLOR_CODE_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            // Add text before this color code
            if (matcher.start() > lastEnd) {
                String segment = text.substring(lastEnd, matcher.start());
                result = result.append(Component.literal(segment).withStyle(currentStyle));
            }

            // Apply the color code
            char code = matcher.group(1).charAt(0);
            ChatFormatting formatting = COLOR_MAP.get(code);
            if (formatting != null) {
                if (formatting == ChatFormatting.RESET) {
                    currentStyle = Style.EMPTY;
                } else if (formatting.isColor()) {
                    currentStyle = Style.EMPTY.withColor(formatting);
                } else {
                    currentStyle = currentStyle.applyFormat(formatting);
                }
            }

            lastEnd = matcher.end();
        }

        // Add remaining text
        if (lastEnd < text.length()) {
            String segment = text.substring(lastEnd);
            result = result.append(Component.literal(segment).withStyle(currentStyle));
        }

        return result;
    }

    /**
     * Create a simple text component
     */
    public static Component literal(String text) {
        return Component.literal(text);
    }

    /**
     * Create a translatable component
     */
    public static Component translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
