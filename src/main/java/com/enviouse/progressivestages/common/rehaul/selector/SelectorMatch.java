package com.enviouse.progressivestages.common.rehaul.selector;

public record SelectorMatch(boolean matched, int specificity, String explanation) {

    public static SelectorMatch no(String explanation) {
        return new SelectorMatch(false, 0, explanation == null ? "" : explanation);
    }

    public static SelectorMatch yes(int specificity, String explanation) {
        return new SelectorMatch(true, specificity, explanation == null ? "" : explanation);
    }
}
