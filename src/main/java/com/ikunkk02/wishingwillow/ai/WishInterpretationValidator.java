package com.ikunkk02.wishingwillow.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.ikunkk02.wishingwillow.contract.WishConstraintKind;
import com.ikunkk02.wishingwillow.contract.WishConstraintOperator;
import com.ikunkk02.wishingwillow.contract.WishContract;
import com.ikunkk02.wishingwillow.contract.WishContractType;
import com.ikunkk02.wishingwillow.contract.WishHardConstraint;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict boundary for every new AI interpretation submitted to the server. */
public final class WishInterpretationValidator {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MAX_INTENT_LENGTH = 64;
    public static final int MAX_LITERAL_GOAL_LENGTH = 512;
    public static final int MAX_TEXT_LENGTH = 1024;
    public static final int MAX_CAPABILITIES = 12;
    public static final int MAX_CONSTRAINTS = 16;

    private static final Pattern INTENT_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Pattern SEMANTIC_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,127}");
    private static final Set<String> FIELDS = Set.of("schema_version", "intent", "literal_goal", "contract",
            "fulfillment", "reasoning_summary", "tone", "severity", "delivery", "required_capabilities");
    private static final Set<String> CONTRACT_FIELDS = Set.of("type", "required_outcome", "hard_constraints");
    private static final Set<String> CONSTRAINT_FIELDS = Set.of("kind", "operator", "semantic", "quantity", "amount", "required");
    private static final Set<String> FULFILLMENT_FIELDS = Set.of("mode", "method", "styles", "absurdity");
    private static final Gson GSON = new Gson();

    private WishInterpretationValidator() {}

    public static WishInterpretation parseAndValidate(String rawContent) {
        JsonElement parsed = parseStrict(stripSingleCodeFence(rawContent));
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(FIELDS)) throw invalid("ROOT_FIELDS");
        JsonObject object = parsed.getAsJsonObject();
        int schema = exactInteger(object, "schema_version");
        if (schema != CURRENT_SCHEMA_VERSION) throw invalid("SCHEMA_VERSION");
        String intent = string(object, "intent", MAX_INTENT_LENGTH);
        if (!INTENT_PATTERN.matcher(intent).matches()) throw invalid("INTENT_FORMAT");
        String literal = string(object, "literal_goal", MAX_LITERAL_GOAL_LENGTH);

        JsonObject contractJson = exactObject(object, "contract", CONTRACT_FIELDS);
        WishContractType contractType = enumValue(contractJson, "type", WishContractType.class);
        String requiredOutcome = string(contractJson, "required_outcome", MAX_TEXT_LENGTH);
        JsonArray constraintsJson = exactArray(contractJson, "hard_constraints", 1, MAX_CONSTRAINTS);
        List<WishHardConstraint> constraints = new ArrayList<>();
        for (JsonElement element : constraintsJson) {
            if (!element.isJsonObject() || !element.getAsJsonObject().keySet().equals(CONSTRAINT_FIELDS)) throw invalid("CONSTRAINT_FIELDS");
            JsonObject value = element.getAsJsonObject();
            WishConstraintKind kind = enumValue(value, "kind", WishConstraintKind.class);
            WishConstraintOperator operator = enumValue(value, "operator", WishConstraintOperator.class);
            String semantic = stringAllowEmpty(value, "semantic", 128);
            int quantity = exactInteger(value, "quantity");
            double amount = finiteNumber(value, "amount");
            boolean required = bool(value, "required");
            validateConstraint(kind, operator, semantic, quantity, amount, required);
            constraints.add(new WishHardConstraint(kind, operator, semantic, quantity, amount, required));
        }
        WishContract contract = new WishContract(contractType, requiredOutcome, constraints);

        JsonObject fulfillmentJson = exactObject(object, "fulfillment", FULFILLMENT_FIELDS);
        WishFulfillmentMode mode = enumValue(fulfillmentJson, "mode", WishFulfillmentMode.class);
        String method = string(fulfillmentJson, "method", MAX_TEXT_LENGTH);
        int absurdity = exactInteger(fulfillmentJson, "absurdity");
        if (absurdity < 0 || absurdity > 100) throw invalid("ABSURDITY_RANGE");
        JsonArray stylesJson = exactArray(fulfillmentJson, "styles", 1, 3);
        List<FulfillmentStyle> styles = enumList(stylesJson, FulfillmentStyle.class);
        WishFulfillment fulfillment = new WishFulfillment(mode, method, styles, absurdity);

        String reasoning = string(object, "reasoning_summary", MAX_TEXT_LENGTH);
        WishTone tone = enumValue(object, "tone", WishTone.class);
        int severity = exactInteger(object, "severity");
        if (severity < 0 || severity > 100) throw invalid("SEVERITY_RANGE");
        WishDelivery delivery = enumValue(object, "delivery", WishDelivery.class);
        List<WishCapability> capabilities = enumList(exactArray(object, "required_capabilities", 1, MAX_CAPABILITIES), WishCapability.class);
        WishRefusalGuard.requireAllowed(literal, requiredOutcome, method, reasoning);
        return new WishInterpretation(schema, intent, literal, contract, fulfillment, reasoning, tone, severity, delivery, capabilities);
    }

    public static WishInterpretation parseProviderResponse(String rawContent) {
        try { return parseAndValidate(rawContent); }
        catch (IllegalArgumentException original) {
            final JsonElement parsed;
            try { parsed = parseStrict(stripSingleCodeFence(rawContent)); }
            catch (IllegalArgumentException ignored) { throw original; }
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(FIELDS)) throw original;
            JsonObject object = parsed.getAsJsonObject();
            JsonElement value = object.get("intent");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw original;
            String normalized = normalizeIntent(value.getAsString().strip());
            if (!INTENT_PATTERN.matcher(normalized).matches()) throw original;
            object.addProperty("intent", normalized);
            return parseAndValidate(GSON.toJson(object));
        }
    }

    /** Legacy records are deliberately accepted only by the save loader, never by the network parser above. */
    public static void validateStored(WishInterpretation interpretation) {
        if (interpretation.schemaVersion() == 1) return;
        validate(interpretation);
    }

    public static void validate(WishInterpretation interpretation) { parseAndValidate(toJson(interpretation)); }

    public static String toJson(WishInterpretation value) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", value.schemaVersion());
        root.addProperty("intent", value.intent());
        root.addProperty("literal_goal", value.literalGoal());
        JsonObject contract = new JsonObject();
        contract.addProperty("type", value.contract().type().name());
        contract.addProperty("required_outcome", value.contract().requiredOutcome());
        JsonArray constraints = new JsonArray();
        for (WishHardConstraint constraint : value.contract().hardConstraints()) {
            JsonObject json = new JsonObject();
            json.addProperty("kind", constraint.kind().name());
            json.addProperty("operator", constraint.operator().name());
            json.addProperty("semantic", constraint.semantic());
            json.addProperty("quantity", constraint.quantity());
            json.addProperty("amount", constraint.amount());
            json.addProperty("required", constraint.required());
            constraints.add(json);
        }
        contract.add("hard_constraints", constraints);
        root.add("contract", contract);
        JsonObject fulfillment = new JsonObject();
        fulfillment.addProperty("mode", value.fulfillment().mode().name());
        fulfillment.addProperty("method", value.fulfillment().method());
        JsonArray styles = new JsonArray();
        value.fulfillment().styles().forEach(style -> styles.add(style.name()));
        fulfillment.add("styles", styles);
        fulfillment.addProperty("absurdity", value.fulfillment().absurdity());
        root.add("fulfillment", fulfillment);
        root.addProperty("reasoning_summary", value.reasoningSummary());
        root.addProperty("tone", value.tone().name());
        root.addProperty("severity", value.severity());
        root.addProperty("delivery", value.delivery().name());
        JsonArray capabilities = new JsonArray();
        value.requiredCapabilities().forEach(capability -> capabilities.add(capability.name()));
        root.add("required_capabilities", capabilities);
        return GSON.toJson(root);
    }

    public static JsonObject jsonSchema() {
        JsonObject root = GSON.fromJson("""
                {"type":"object","additionalProperties":false,
                 "required":["schema_version","intent","literal_goal","contract","fulfillment","reasoning_summary","tone","severity","delivery","required_capabilities"],
                 "properties":{
                   "schema_version":{"type":"integer","const":2},
                   "intent":{"type":"string","minLength":1,"maxLength":64,"pattern":"^[a-z][a-z0-9_-]{0,63}$"},
                   "literal_goal":{"type":"string","minLength":1,"maxLength":512},
                   "contract":{"type":"object","additionalProperties":false,"required":["type","required_outcome","hard_constraints"],"properties":{
                     "type":{"type":"string"},"required_outcome":{"type":"string","minLength":1,"maxLength":1024},
                     "hard_constraints":{"type":"array","minItems":1,"maxItems":16,"items":{"type":"object","additionalProperties":false,
                       "required":["kind","operator","semantic","quantity","amount","required"],"properties":{
                         "kind":{"type":"string"},"operator":{"type":"string"},"semantic":{"type":"string","maxLength":128},
                         "quantity":{"type":"integer","minimum":0},"amount":{"type":"number"},"required":{"type":"boolean"}}}}}},
                   "fulfillment":{"type":"object","additionalProperties":false,"required":["mode","method","styles","absurdity"],"properties":{
                     "mode":{"type":"string"},"method":{"type":"string","minLength":1,"maxLength":1024},
                     "styles":{"type":"array","minItems":1,"maxItems":3,"uniqueItems":true,"items":{"type":"string"}},
                     "absurdity":{"type":"integer","minimum":0,"maximum":100}}},
                   "reasoning_summary":{"type":"string","minLength":1,"maxLength":1024},
                   "tone":{"type":"string"},"severity":{"type":"integer","minimum":0,"maximum":100},
                   "delivery":{"type":"string"},
                   "required_capabilities":{"type":"array","minItems":1,"maxItems":12,"uniqueItems":true,"items":{"type":"string"}}
                 }}
                """, JsonObject.class);
        JsonObject properties = root.getAsJsonObject("properties");
        enumSchema(properties.getAsJsonObject("contract").getAsJsonObject("properties").getAsJsonObject("type"), WishContractType.values());
        JsonObject constraintProps = properties.getAsJsonObject("contract").getAsJsonObject("properties").getAsJsonObject("hard_constraints")
                .getAsJsonObject("items").getAsJsonObject("properties");
        enumSchema(constraintProps.getAsJsonObject("kind"), WishConstraintKind.values());
        enumSchema(constraintProps.getAsJsonObject("operator"), WishConstraintOperator.values());
        JsonObject fulfillment = properties.getAsJsonObject("fulfillment").getAsJsonObject("properties");
        enumSchema(fulfillment.getAsJsonObject("mode"), WishFulfillmentMode.values());
        enumSchema(fulfillment.getAsJsonObject("styles").getAsJsonObject("items"), FulfillmentStyle.values());
        enumSchema(properties.getAsJsonObject("tone"), WishTone.values());
        enumSchema(properties.getAsJsonObject("delivery"), WishDelivery.values());
        enumSchema(properties.getAsJsonObject("required_capabilities").getAsJsonObject("items"), WishCapability.values());
        return root;
    }

    private static void validateConstraint(WishConstraintKind kind, WishConstraintOperator operator, String semantic,
                                           int quantity, double amount, boolean required) {
        if (quantity < 0 || !Double.isFinite(amount)) throw invalid("CONSTRAINT_NUMBER");
        boolean semanticKind = switch (kind) {
            case RESOURCE_KIND, RESOURCE_SEMANTIC, DELIVERY_SEMANTIC, STATE_METRIC,
                    TARGET_SEMANTIC, TARGET_SCOPE, PERSISTENCE -> true;
            default -> false;
        };
        if (semanticKind && !SEMANTIC_PATTERN.matcher(semantic).matches()) throw invalid("CONSTRAINT_SEMANTIC");
        if (kind == WishConstraintKind.CUSTOM_SEMANTIC && semantic.isBlank()) throw invalid("CUSTOM_SEMANTIC_EMPTY");
        if (kind == WishConstraintKind.MINIMUM_QUANTITY && (operator != WishConstraintOperator.AT_LEAST || quantity < 1)) throw invalid("MINIMUM_QUANTITY");
        if (!required) throw invalid("OPTIONAL_HARD_CONSTRAINT");
    }

    private static <E extends Enum<E>> List<E> enumList(JsonArray array, Class<E> type) {
        List<E> result = new ArrayList<>(); Set<E> seen = new HashSet<>();
        for (JsonElement value : array) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("ENUM_TYPE");
            final E parsed; try { parsed = Enum.valueOf(type, value.getAsString()); } catch (IllegalArgumentException e) { throw invalid("ENUM_VALUE_" + type.getSimpleName()); }
            if (!seen.add(parsed)) throw invalid("DUPLICATE_ENUM_" + type.getSimpleName()); result.add(parsed);
        }
        return result;
    }

    private static JsonObject exactObject(JsonObject parent, String name, Set<String> fields) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject() || !value.getAsJsonObject().keySet().equals(fields)) throw invalid("OBJECT_FIELDS_" + name);
        return value.getAsJsonObject();
    }
    private static JsonArray exactArray(JsonObject parent, String name, int min, int max) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() < min || value.getAsJsonArray().size() > max) throw invalid("ARRAY_SIZE_" + name);
        return value.getAsJsonArray();
    }
    private static int exactInteger(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw invalid("INTEGER_" + name);
        try { return Integer.parseInt(value.getAsString()); } catch (NumberFormatException e) { throw invalid("INTEGER_RANGE_" + name); }
    }
    private static double finiteNumber(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("NUMBER_" + name);
        try { double result = value.getAsDouble(); if (!Double.isFinite(result)) throw invalid("FINITE_NUMBER_" + name); return result; }
        catch (NumberFormatException e) { throw invalid("NUMBER_" + name); }
    }
    private static boolean bool(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid("BOOLEAN_" + name);
        return value.getAsBoolean();
    }
    private static String string(JsonObject object, String name, int max) {
        String value = stringAllowEmpty(object, name, max); if (value.isEmpty()) throw invalid("EMPTY_STRING_" + name); return value;
    }
    private static String stringAllowEmpty(JsonObject object, String name, int max) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("STRING_" + name);
        String result = value.getAsString().strip(); if (result.length() > max) throw invalid("STRING_LENGTH_" + name); return result;
    }
    private static <E extends Enum<E>> E enumValue(JsonObject object, String name, Class<E> type) {
        try { return Enum.valueOf(type, string(object, name, 64)); } catch (IllegalArgumentException e) { throw invalid("ENUM_" + name); }
    }
    private static void enumSchema(JsonObject schema, Enum<?>[] values) {
        JsonArray enums = new JsonArray(); for (Enum<?> value : values) enums.add(value.name()); schema.add("enum", enums);
    }
    private static JsonElement parseStrict(String json) {
        try { JsonReader reader = new JsonReader(new StringReader(json)); reader.setLenient(false); JsonElement result = Streams.parse(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("TRAILING_JSON"); return result; }
        catch (Exception e) { if (e instanceof IllegalArgumentException iae) throw iae; throw invalid("JSON_SYNTAX"); }
    }
    private static String stripSingleCodeFence(String raw) {
        if (raw == null) throw invalid("NULL_RESPONSE"); String value = raw.strip(); if (!value.startsWith("```")) return value;
        int newline = value.indexOf('\n'); if (newline < 0 || !value.endsWith("```")) throw invalid("CODE_FENCE");
        String language = value.substring(3, newline).strip(); if (!language.isEmpty() && !language.equalsIgnoreCase("json")) throw invalid("CODE_FENCE_LANGUAGE");
        String body = value.substring(newline + 1, value.length() - 3).strip(); if (body.contains("```")) throw invalid("NESTED_CODE_FENCE"); return body;
    }
    private static String normalizeIntent(String intent) {
        String value = intent.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_").replaceAll("^[^a-z]+", "").replaceAll("[_-]+$", "");
        if (value.length() > MAX_INTENT_LENGTH) value = value.substring(0, MAX_INTENT_LENGTH).replaceAll("[_-]+$", ""); return value;
    }
    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException(AiErrorCategory.MALFORMED_RESPONSE.name() + ":" + detail);
    }
}
