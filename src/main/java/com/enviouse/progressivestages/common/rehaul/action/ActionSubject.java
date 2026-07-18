package com.enviouse.progressivestages.common.rehaul.action;

import com.enviouse.progressivestages.common.api.StageId;

public interface ActionSubject {

    String id();

    boolean hasStage(StageId stage);

    boolean grantStage(StageId stage);

    boolean revokeStage(StageId stage);

    String stageState(StageId stage);

    boolean setStageState(StageId stage, String state);

    double variable(String id);

    void setVariable(String id, double value);

    void sendMessage(String message);

    Object snapshot();

    void restore(Object snapshot);
}
