package com.ikunkk02.wishingwillow.research.web;

import java.util.List;

public record WebSearchResult(
        String title,
        String url,
        String snippet,
        String author,
        List<String> gameVersions,
        List<String> loaders,
        List<String> categories,
        List<String> fileNames,
        String source
) {
    public WebSearchResult {
        title = clean(title, 256);
        url = clean(url, 2048);
        snippet = clean(snippet, 2048);
        author = clean(author, 256);
        gameVersions = List.copyOf(gameVersions == null ? List.of() : gameVersions);
        loaders = List.copyOf(loaders == null ? List.of() : loaders);
        categories = List.copyOf(categories == null ? List.of() : categories);
        fileNames = List.copyOf(fileNames == null ? List.of() : fileNames);
        source = clean(source, 64);
    }

    private static String clean(String value, int max) {
        String result = value == null ? "" : value.replace('\u0000', ' ').strip();
        return result.length() <= max ? result : result.substring(0, max);
    }
}
