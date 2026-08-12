package com.ikunkk02.wishingwillow.advancement;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishTone;
import com.ikunkk02.wishingwillow.execution.WishExecutionRecord;
import com.ikunkk02.wishingwillow.execution.WishStepExecution;
import com.ikunkk02.wishingwillow.execution.WishStepExecutionState;
import com.ikunkk02.wishingwillow.program.ProgramAction;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class WishOutcomeClassifier {
    private static final Set<String> PERSISTENT_ACTIONS = Set.of(
            "entity_attraction_aura", "entity_suppression", "follow_player", "avoid_player",
            "modify_attribute", "start_predefined_event");
    private static final Set<String> NEGATIVE_ACTIONS = Set.of(
            "create_explosion", "entity_suppression", "remove_entity", "spawn_lightning",
            "modify_health", "apply_effect", "set_entity_target");
    private static final Set<String> EXTREME_ACTIONS = Set.of(
            "entity_suppression", "create_explosion", "replace_blocks", "spawn_falling_block");

    private WishOutcomeClassifier() { }

    public static WishOutcomeSummary classify(WishExecutionRecord execution, WishProgram program,
                                              WishInterpretation interpretation) {
        List<ProgramAction> leaves = new ArrayList<>();
        int index = 0;
        for (WishProgramAction action : allActions(program)) {
            leaves.add(new ProgramAction(action.action(), action.parameters(), false,
                    index, 0, null, null, null, index));
            index++;
        }
        return classify(execution, program, interpretation, leaves);
    }

    public static WishOutcomeSummary classify(WishExecutionRecord execution, WishProgram program,
                                              WishInterpretation interpretation,
                                              List<ProgramAction> leaves) {
        int successful = 0;
        boolean persistent = false;
        boolean negative = false;
        int limit = Math.min(execution.steps().size(), leaves.size());
        for (int index = 0; index < limit; index++) {
            WishStepExecution step = execution.step(index);
            if (step == null || step.state() != WishStepExecutionState.SUCCEEDED) continue;
            successful++;
            ProgramAction action = leaves.get(index);
            JsonObject parameters = action.parameters();
            if (PERSISTENT_ACTIONS.contains(action.actionId()) && persistent(parameters)) persistent = true;
            if (NEGATIVE_ACTIONS.contains(action.actionId()) && negative(action.actionId(), parameters)) negative = true;
        }
        boolean absurd = "absurd_wish_realization".equals(program.skill()) && successful > 0;
        WishSeverity severity = severity(interpretation, leaves, successful);
        return new WishOutcomeSummary(successful, absurd, persistent, negative, severity,
                interpretation == null ? WishTone.NEUTRAL : interpretation.tone());
    }

    static WishProgram testingProgram(String skill, List<ProgramAction> actions) {
        List<WishProgramAction> core = actions.stream()
                .map(action -> new WishProgramAction(action.actionId(), action.parameters())).toList();
        if (core.isEmpty()) core = List.of(new WishProgramAction("play_sound", new JsonObject()));
        return new WishProgram(1, "test", core, List.of(), skill, "");
    }

    private static List<WishProgramAction> allActions(WishProgram program) {
        List<WishProgramAction> result = new ArrayList<>(program.coreActions());
        result.addAll(program.presentationActions());
        return result;
    }

    private static boolean persistent(JsonObject parameters) {
        return bool(parameters, "permanent") || bool(parameters, "prevent_future")
                || number(parameters, "duration_seconds") >= 300
                || number(parameters, "interval_ticks") > 0;
    }

    private static boolean negative(String action, JsonObject parameters) {
        if ("modify_health".equals(action)) return number(parameters, "delta") < 0;
        if ("apply_effect".equals(action)) return bool(parameters, "harmful")
                || text(parameters, "effect").contains("poison")
                || text(parameters, "effect").contains("wither");
        if ("create_explosion".equals(action)) return bool(parameters, "destroy_blocks")
                || number(parameters, "power") >= 4;
        return true;
    }

    private static WishSeverity severity(WishInterpretation interpretation,
                                         List<ProgramAction> actions, int successful) {
        if (successful < 1) return WishSeverity.NORMAL;
        int value = interpretation == null ? 0 : interpretation.severity();
        WishTone tone = interpretation == null ? WishTone.NEUTRAL : interpretation.tone();
        boolean extreme = actions.stream().anyMatch(action -> EXTREME_ACTIONS.contains(action.actionId()));
        if (value >= 90 && (extreme || tone == WishTone.HORROR || tone == WishTone.DARK)) {
            return WishSeverity.CATASTROPHIC;
        }
        if (value >= 70 || tone == WishTone.HORROR || tone == WishTone.DARK || extreme) {
            return WishSeverity.DANGEROUS;
        }
        if (value >= 40 || tone == WishTone.ABSURD || tone == WishTone.IRONIC) return WishSeverity.STRANGE;
        return WishSeverity.NORMAL;
    }

    private static boolean bool(JsonObject object, String key) {
        try { return object.has(key) && object.get(key).getAsBoolean(); }
        catch (RuntimeException ignored) { return false; }
    }

    private static double number(JsonObject object, String key) {
        try { return object.has(key) ? object.get(key).getAsDouble() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }

    private static String text(JsonObject object, String key) {
        try { return object.has(key) ? object.get(key).getAsString().toLowerCase(Locale.ROOT) : ""; }
        catch (RuntimeException ignored) { return ""; }
    }
}
