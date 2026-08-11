package com.ikunkk02.wishingwillow.agent.tool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

public record ToolResult(
        ToolStatus status,
        String code,
        String message,
        int affected,
        List<String> accepted,
        List<ToolRejection> rejected,
        String nextHint,
        JsonObject data,
        String nextCursor
) {
    public static final int MAX_SERIALIZED_BYTES = 64 * 1024;
    private static final Gson GSON = new Gson();

    public ToolResult {
        status = status == null ? ToolStatus.FAILED : status;
        code = code == null ? "FAILED" : code;
        message = message == null ? "" : message;
        affected = Math.max(0, affected);
        accepted = List.copyOf(accepted == null ? List.of() : accepted);
        rejected = List.copyOf(rejected == null ? List.of() : rejected);
        nextHint = nextHint == null ? "" : nextHint;
        data = data == null ? new JsonObject() : data.deepCopy();
        nextCursor = nextCursor == null ? "" : nextCursor;
    }

    public String toJson() {
        String json = GSON.toJson(this);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SERIALIZED_BYTES) {
            return GSON.toJson(failed("RESULT_TOO_LARGE",
                    "Tool result exceeded the 64 KiB safety limit.",
                    "Use limit, cursor, query, or a narrower filter."));
        }
        return json;
    }

    public static ToolResult success(String code, String message, int affected,
                                     List<String> accepted, JsonObject data, String nextCursor) {
        return new ToolResult(ToolStatus.SUCCESS, code, message, affected, accepted, List.of(),
                "", data, nextCursor);
    }

    public static ToolResult failed(String code, String message, String nextHint) {
        return new ToolResult(ToolStatus.FAILED, code, message, 0, List.of(), List.of(),
                nextHint, new JsonObject(), "");
    }

    public static ToolResult invalid(String code, String message, String nextHint) {
        return new ToolResult(ToolStatus.INVALID_ARGUMENT, code, message, 0, List.of(), List.of(),
                nextHint, new JsonObject(), "");
    }

    public static ToolResult notFound(String code, String message, String nextHint) {
        return new ToolResult(ToolStatus.NOT_FOUND, code, message, 0, List.of(), List.of(),
                nextHint, new JsonObject(), "");
    }
}
