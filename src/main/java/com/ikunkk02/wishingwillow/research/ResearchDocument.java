package com.ikunkk02.wishingwillow.research;

public record ResearchDocument(
        ResearchSource source,
        String title,
        String content,
        String publicUrl,
        String trust
) {
    public static final String UNTRUSTED = "UNTRUSTED_RESEARCH_DOCUMENT";
    public static final int MAX_CONTENT_CHARS = 24 * 1024;

    public ResearchDocument {
        source = source == null ? ResearchSource.LOCAL_METADATA : source;
        title = title == null ? "" : title.strip();
        content = content == null ? "" : content.replace('\u0000', ' ').strip();
        if (content.length() > MAX_CONTENT_CHARS) {
            content = content.substring(0, MAX_CONTENT_CHARS);
        }
        publicUrl = publicUrl == null ? "" : publicUrl.strip();
        trust = UNTRUSTED;
    }

    public ResearchDocument(ResearchSource source, String title, String content, String publicUrl) {
        this(source, title, content, publicUrl, UNTRUSTED);
    }
}
