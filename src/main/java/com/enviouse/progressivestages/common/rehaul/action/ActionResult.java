package com.enviouse.progressivestages.common.rehaul.action;

public record ActionResult(boolean success, String code, String explanation, Object compensationToken) {

    public ActionResult {
        code = code == null ? "" : code;
        explanation = explanation == null ? "" : explanation;
    }

    public static ActionResult success(String explanation, Object token) {
        return new ActionResult(true, "ok", explanation, token);
    }

    public static ActionResult failure(String code, String explanation) {
        return new ActionResult(false, code, explanation, null);
    }
}
