package com.ikunkk02.wishingwillow.research.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebResearchToolTest {
    @Test
    void publishesClosedSchemasForBothControlledTools() {
        assertEquals("search_mod_web", WebResearchTool.SEARCH_MOD_WEB.toolName());
        assertEquals("fetch_research_page", WebResearchTool.FETCH_RESEARCH_PAGE.toolName());
        assertFalse(WebResearchTool.SEARCH_MOD_WEB.parameters().get("additionalProperties").getAsBoolean());
        assertEquals(3, WebResearchTool.SEARCH_MOD_WEB.parameters().getAsJsonObject("properties")
                .getAsJsonObject("preferred_domain").getAsJsonArray("enum").size());
        assertTrue(WebResearchTool.FETCH_RESEARCH_PAGE.parameters().getAsJsonArray("required")
                .toString().contains("url"));
    }
}
