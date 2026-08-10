package com.ikunkk02.wishingwillow.research.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public enum WebResearchTool {
    SEARCH_MOD_WEB("search_mod_web", "Search controlled first-party public mod sources.", """
            {"type":"object","additionalProperties":false,"required":["query","preferred_domain"],
             "properties":{"query":{"type":"string","minLength":1,"maxLength":256},
             "preferred_domain":{"type":"string","enum":["CURSEFORGE","GITHUB","AUTO"]}}}
            """),
    FETCH_RESEARCH_PAGE("fetch_research_page", "Fetch one previously approved HTTPS research page.", """
            {"type":"object","additionalProperties":false,"required":["url"],
             "properties":{"url":{"type":"string","minLength":8,"maxLength":2048}}}
            """);

    private final String toolName;
    private final String description;
    private final JsonObject parameters;

    WebResearchTool(String toolName, String description, String parameters) {
        this.toolName = toolName; this.description = description;
        this.parameters = JsonParser.parseString(parameters).getAsJsonObject();
    }
    public String toolName() { return toolName; }
    public String description() { return description; }
    public JsonObject parameters() { return parameters.deepCopy(); }
}
