package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;

/** Small, bounded recovery helper for common LLM wrappers; it is deliberately not a JavaScript parser. */
public final class LlmJsonRecovery {
    public static final int MAX_AI_JSON = 128 * 1024;

    private LlmJsonRecovery() { }

    public static ParsedObject parseObject(String raw, Set<String> identifyingFields, String errorCode) {
        if (raw == null || raw.length() > MAX_AI_JSON) {
            throw new IllegalArgumentException(errorCode);
        }
        String value = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        for (int start = value.indexOf('{'); start >= 0; start = value.indexOf('{', start + 1)) {
            int end = matchingObjectEnd(value, start);
            if (end < 0) continue;
            String candidate = value.substring(start, end + 1);
            String recovered = removeTrailingCommas(candidate);
            try {
                JsonElement parsed = JsonParser.parseString(recovered);
                if (!parsed.isJsonObject()) continue;
                JsonObject object = parsed.getAsJsonObject();
                if (!identifyingFields.isEmpty() && !object.keySet().containsAll(identifyingFields)) continue;
                String stripped = value.strip();
                boolean changed = start != value.indexOf(stripped)
                        || end + 1 != value.indexOf(stripped) + stripped.length()
                        || !candidate.equals(recovered)
                        || raw.startsWith("\uFEFF");
                return new ParsedObject(object, changed);
            } catch (RuntimeException ignored) {
                // Try the next bounded object candidate. Natural-language prefixes often contain braces.
            }
        }
        throw new IllegalArgumentException(errorCode);
    }

    private static int matchingObjectEnd(String value, int start) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') quoted = true;
            else if (current == '{' || current == '[') depth++;
            else if (current == '}' || current == ']') {
                depth--;
                if (depth == 0) return current == '}' ? index : -1;
                if (depth < 0) return -1;
            }
        }
        return -1;
    }

    private static String removeTrailingCommas(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quoted) {
                result.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') {
                quoted = true;
                result.append(current);
                continue;
            }
            if (current == ',') {
                int next = index + 1;
                while (next < value.length() && Character.isWhitespace(value.charAt(next))) next++;
                if (next < value.length() && (value.charAt(next) == '}' || value.charAt(next) == ']')) {
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    public record ParsedObject(JsonObject object, boolean recovered) {
        public ParsedObject {
            object = object.deepCopy();
        }

        @Override
        public JsonObject object() {
            return object.deepCopy();
        }
    }
}
