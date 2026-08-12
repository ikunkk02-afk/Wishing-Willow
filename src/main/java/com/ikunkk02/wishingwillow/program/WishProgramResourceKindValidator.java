package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.util.ArrayList;
import java.util.List;

/** Client-side diagnostic check; the server remains authoritative for every registry lookup. */
public final class WishProgramResourceKindValidator {
    private static final WishActionRegistry ACTIONS = WishActionRegistry.defaults();

    private WishProgramResourceKindValidator() { }

    public static void validate(WishProgram program, RegistrySnapshot snapshot) {
        for (ResourceUse use : resources(program)) {
            if (snapshot.contains(use.expected(), use.resource())) continue;
            RegistryEntryType actual = actualType(snapshot, use.resource(), use.expected());
            if (actual != null) {
                throw new IllegalArgumentException("RESOURCE_KIND_MISMATCH:action=" + use.action()
                        + " parameter=" + use.parameter() + " resource=" + use.resource()
                        + " expected=" + use.expected() + " actual=" + actual);
            }
        }
    }

    public static List<ResourceUse> resources(WishProgram program) {
        List<ResourceUse> result = new ArrayList<>();
        program.coreActions().forEach(action -> collect(action.action(), action.parameters(), result));
        program.presentationActions().forEach(action -> collect(action.action(), action.parameters(), result));
        return List.copyOf(result);
    }

    private static void collect(String actionId, JsonObject parameters, List<ResourceUse> result) {
        WishActionDefinition definition = ACTIONS.find(actionId);
        if (definition == null) return;
        if (definition.resourceKind() != null && parameters.has(definition.resourceParameter())) {
            JsonElement value = parameters.get(definition.resourceParameter());
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                int count = parameters.has("count") && parameters.get("count").isJsonPrimitive()
                        && parameters.get("count").getAsJsonPrimitive().isNumber()
                        ? parameters.get("count").getAsInt() : 0;
                result.add(new ResourceUse(actionId, definition.resourceParameter(),
                        namespaced(value.getAsString()), definition.resourceKind(), count));
            }
        }
        JsonElement children = parameters.get("actions");
        if (children == null || !children.isJsonArray()) return;
        for (JsonElement child : children.getAsJsonArray()) {
            if (!child.isJsonObject()) continue;
            JsonObject object = child.getAsJsonObject();
            if (object.has("action") && object.get("action").isJsonPrimitive()
                    && object.get("action").getAsJsonPrimitive().isString()
                    && object.has("parameters") && object.get("parameters").isJsonObject()) {
                collect(object.get("action").getAsString(), object.getAsJsonObject("parameters"), result);
            }
        }
    }

    private static RegistryEntryType actualType(RegistrySnapshot snapshot, String resource,
                                                RegistryEntryType expected) {
        for (RegistryEntryType type : RegistryEntryType.values()) {
            if (type != expected && snapshot.contains(type, resource)) return type;
        }
        return null;
    }

    private static String namespaced(String resource) {
        String value = resource == null ? "" : resource.strip();
        return value.contains(":") ? value : "minecraft:" + value;
    }

    public record ResourceUse(String action, String parameter, String resource,
                              RegistryEntryType expected, int count) { }
}