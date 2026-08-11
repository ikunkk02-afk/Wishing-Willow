package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.program.skill.WishSkillRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministically expands a validated Wish Program into flat executable leaves
 * ({@link ProgramAction}). Flow primitives (sequence / parallel / repeat / delay) are resolved
 * natively here into groups and delays; nothing is lowered into the legacy WishPlan model.
 *
 * <p>This is the only compile step on the NEW path. It never touches
 * {@code DirectActionPlanCompiler}, {@code WishPlanDraft}, {@code WishPlanStep},
 * {@code WishContractValidator} or any other legacy planning component.</p>
 */
public final class WishProgramCompiler {
    private static final int MAX_FLOW_DEPTH = 4;

    public CompiledWishProgram compile(WishProgram program) {
        WishProgramJson.validate(program, com.ikunkk02.wishingwillow.execution.action.WishActionRegistry.defaults());
        WishSkillRegistry.defaults().validateSelection(program);
        if (program.requiresAgent()) throw new IllegalArgumentException("UNKNOWN_CAPABILITY");
        List<ProgramAction> core = assignIndices(expand(program.coreActions(), false, 0, 0), 0);
        List<ProgramAction> presentation = assignIndices(
                expand(program.presentationActions(), true, core.size(), 0), core.size());
        return new CompiledWishProgram(program, List.copyOf(core), List.copyOf(presentation),
                program.usesSkill(), false);
    }

    /**
     * Expands one action list into flat leaves. Group numbers increase monotonically; parallel
     * children share one group; delays accumulate onto the next leaf; repeat expands bounded
     * iterations.
     */
    static List<ProgramAction> expand(List<WishProgramAction> values, boolean presentation,
                                      int group, int depth) {
        if (depth > MAX_FLOW_DEPTH) throw new IllegalArgumentException("FLOW_DEPTH");
        List<ProgramAction> result = new ArrayList<>();
        int currentGroup = group;
        int pendingDelay = 0;
        for (WishProgramAction value : values) {
            switch (value.action()) {
                case "delay" -> pendingDelay += value.parameters().get("ticks").getAsInt();
                case "sequence", "parallel", "repeat" -> {
                    List<WishProgramAction> children = children(value.parameters());
                    int repeats = value.action().equals("repeat")
                            ? value.parameters().get("count").getAsInt() : 1;
                    for (int iteration = 0; iteration < repeats; iteration++) {
                        List<ProgramAction> expanded = expand(children, presentation,
                                currentGroup, depth + 1);
                        if (value.action().equals("parallel")) {
                            int parallelGroup = currentGroup;
                            expanded = expanded.stream().map(child -> new ProgramAction(
                                    child.actionId(), child.parameters(), presentation,
                                    parallelGroup, child.delayTicks(), child.target(),
                                    child.capability(), child.candidate(), 0)).toList();
                        }
                        if (pendingDelay > 0 && !expanded.isEmpty()) {
                            ProgramAction first = expanded.get(0);
                            List<ProgramAction> delayed = new ArrayList<>(expanded);
                            delayed.set(0, new ProgramAction(first.actionId(),
                                    first.parameters(), presentation, first.group(),
                                    first.delayTicks() + pendingDelay, first.target(),
                                    first.capability(), first.candidate(), 0));
                            expanded = delayed;
                            pendingDelay = 0;
                        }
                        result.addAll(expanded);
                        int maxChildGroup = expanded.stream().mapToInt(ProgramAction::group)
                                .max().orElse(currentGroup);
                        currentGroup = Math.max(currentGroup + 1, maxChildGroup + 1);
                    }
                }
                default -> {
                    result.add(new ProgramAction(value.action(), value.parameters(),
                            presentation, currentGroup, pendingDelay, null, null, null, 0));
                    pendingDelay = 0;
                    currentGroup++;
                }
            }
        }
        return result;
    }

    private static List<ProgramAction> assignIndices(List<ProgramAction> leaves, int start) {
        List<ProgramAction> result = new ArrayList<>(leaves.size());
        for (int index = 0; index < leaves.size(); index++) {
            ProgramAction leaf = leaves.get(index);
            result.add(new ProgramAction(leaf.actionId(), leaf.parameters(), leaf.presentation(),
                    leaf.group(), leaf.delayTicks(), leaf.target(), leaf.capability(),
                    leaf.candidate(), start + index));
        }
        return result;
    }

    private static List<WishProgramAction> children(JsonObject parameters) {
        JsonArray array = parameters.getAsJsonArray("actions");
        List<WishProgramAction> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            result.add(new WishProgramAction(object.get("action").getAsString(),
                    object.getAsJsonObject("parameters")));
        }
        return result;
    }
}
