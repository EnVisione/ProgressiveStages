package com.enviouse.progressivestages.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masks configured words in the rendered chat component while retaining its character styles. */
public final class ChatMasker {
    private record Part(int start, int end, Style style) {}
    private record MatcherPlan(List<String> terms, Pattern pattern) {}
    private static volatile MatcherPlan cachedPlan;

    private ChatMasker() {}

    public static Component mask(Component message, List<String> terms) {
        if (terms == null || terms.isEmpty()) return message;
        MatcherPlan plan = cachedPlan;
        if (plan == null || !plan.terms().equals(terms)) {
            plan = compile(terms);
            cachedPlan = plan;
        }
        if (plan.pattern() == null) return message;
        StringBuilder raw = new StringBuilder();
        List<Part> parts = new ArrayList<>();
        message.visit((style, text) -> {
            int start = raw.length();
            raw.append(text);
            parts.add(new Part(start, raw.length(), style));
            return Optional.<Void>empty();
        }, Style.EMPTY);
        Matcher matcher = plan.pattern().matcher(raw);
        if (!matcher.find()) return message;
        MutableComponent result = Component.empty();
        int cursor = 0;
        do {
            appendStyled(result, raw, parts, cursor, matcher.start(), false);
            appendStyled(result, raw, parts, matcher.start(), matcher.end(), true);
            cursor = matcher.end();
        } while (matcher.find());
        appendStyled(result, raw, parts, cursor, raw.length(), false);
        return result;
    }

    private static MatcherPlan compile(List<String> terms) {
        List<String> ordered = terms.stream().filter(term -> term != null && !term.isEmpty())
            .distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        List<String> snapshot = java.util.Collections.unmodifiableList(new ArrayList<>(terms));
        if (ordered.isEmpty()) return new MatcherPlan(snapshot, null);
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}_])(?:"
            + ordered.stream().map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"))
            + ")(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return new MatcherPlan(snapshot, pattern);
    }

    private static void appendStyled(MutableComponent result, StringBuilder raw, List<Part> parts,
                                     int from, int to, boolean obfuscated) {
        for (Part part : parts) {
            int start = Math.max(from, part.start());
            int end = Math.min(to, part.end());
            if (end > start) result.append(Component.literal(raw.substring(start, end))
                .withStyle(obfuscated ? part.style().withObfuscated(true) : part.style()));
        }
    }
}
