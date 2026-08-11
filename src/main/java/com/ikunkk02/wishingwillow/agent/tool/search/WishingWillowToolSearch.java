package com.ikunkk02.wishingwillow.agent.tool.search;

import com.ikunkk02.wishingwillow.agent.core.WishAgentSession;

public interface WishingWillowToolSearch {
    ToolSearchResult search(WishAgentSession session, ToolSearchQuery query);
}
