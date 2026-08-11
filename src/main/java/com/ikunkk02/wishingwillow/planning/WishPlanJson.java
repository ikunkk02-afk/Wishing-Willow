package com.ikunkk02.wishingwillow.planning;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class WishPlanJson {
    private static final Gson GSON = new Gson();
    private WishPlanJson() { }

    public static String toAiJson(WishPlanDraft draft) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", draft.schemaVersion());
        root.addProperty("summary", draft.summary());
        root.addProperty("delivery", draft.delivery().name());
        root.addProperty("severity", draft.severity());
        root.addProperty("estimated_duration", draft.estimatedDuration().name());
        JsonArray steps = new JsonArray();
        for (WishPlanStep step : draft.steps()) {
            JsonObject value = new JsonObject();
            value.addProperty("step_index", step.stepIndex());
            value.addProperty("timing", step.timing().name());
            value.addProperty("delay_seconds", step.delaySeconds());
            value.addProperty("trigger", step.trigger().name());
            value.addProperty("action", step.action().name());
            value.addProperty("capability", step.capability().name());
            value.addProperty("candidate_id", step.candidateId());
            value.addProperty("target", step.target().name());
            value.add("parameters", step.parameters().deepCopy());
            value.addProperty("selection_reason", step.selectionReason());
            if (!step.batchId().isBlank()) value.addProperty("batch_id", step.batchId());
            steps.add(value);
        }
        root.add("steps", steps);
        return GSON.toJson(root);
    }
}
