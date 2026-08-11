package com.ikunkk02.wishingwillow.agent.tool.search;

import com.ikunkk02.wishingwillow.agent.tool.WishToolDescriptor;

import java.util.List;

public record ToolSearchResult(String query, List<WishToolDescriptor> tools) {
    public ToolSearchResult { tools = List.copyOf(tools == null ? List.of() : tools); }
}
