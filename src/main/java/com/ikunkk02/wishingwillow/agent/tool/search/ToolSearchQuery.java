package com.ikunkk02.wishingwillow.agent.tool.search;

public record ToolSearchQuery(String query, int limit) {
    public ToolSearchQuery {
        query = query == null ? "" : query.strip();
        limit = limit <= 0 ? 8 : Math.min(12, limit);
    }
}
