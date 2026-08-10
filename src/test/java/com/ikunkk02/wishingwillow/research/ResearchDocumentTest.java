package com.ikunkk02.wishingwillow.research;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchDocumentTest {
    @Test
    void alwaysMarksThirdPartyTextUntrustedAndCapsIt() {
        ResearchDocument document = new ResearchDocument(ResearchSource.MODRINTH_PROJECT, "project",
                "x".repeat(ResearchDocument.MAX_CONTENT_CHARS + 100), "https://modrinth.com/mod/example", "trusted");
        assertEquals(ResearchDocument.MAX_CONTENT_CHARS, document.content().length());
        assertEquals(ResearchDocument.UNTRUSTED, document.trust());
    }
}
