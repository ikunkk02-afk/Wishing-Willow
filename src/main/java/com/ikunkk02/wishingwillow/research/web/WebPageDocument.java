package com.ikunkk02.wishingwillow.research.web;

import java.util.List;

public record WebPageDocument(String title, String finalUrl, String content, List<Link> links) {
    public WebPageDocument {
        title = title == null ? "" : title.strip();
        finalUrl = finalUrl == null ? "" : finalUrl.strip();
        content = content == null ? "" : content.strip();
        links = List.copyOf(links == null ? List.of() : links);
    }

    public record Link(String text, String url) {
        public Link {
            text = text == null ? "" : text.strip();
            url = url == null ? "" : url.strip();
        }
    }
}
