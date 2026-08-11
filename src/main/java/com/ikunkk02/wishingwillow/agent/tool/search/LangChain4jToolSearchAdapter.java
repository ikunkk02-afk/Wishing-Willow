package com.ikunkk02.wishingwillow.agent.tool.search;

import com.ikunkk02.wishingwillow.agent.core.WishAgentSession;
import com.ikunkk02.wishingwillow.agent.tool.RegisteredWishTool;
import com.ikunkk02.wishingwillow.agent.tool.WishToolDescriptor;
import com.ikunkk02.wishingwillow.agent.tool.WishToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stable project-facing adapter around LangChain4j tool specifications. The
 * matching policy is kept here because LangChain4j's Tool Search API is experimental.
 */
public final class LangChain4jToolSearchAdapter implements WishingWillowToolSearch {
    private final WishToolRegistry registry;

    public LangChain4jToolSearchAdapter(WishToolRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry);
    }

    @Override
    public ToolSearchResult search(WishAgentSession session, ToolSearchQuery query) {
        Set<String> terms = terms(query.query());
        List<Scored> scored = new ArrayList<>();
        for (RegisteredWishTool tool : registry.searchable(session)) {
            WishToolDescriptor descriptor = tool.descriptor();
            int score = score(descriptor, terms);
            if (score > 0 || terms.isEmpty()) scored.add(new Scored(descriptor, score));
        }
        List<WishToolDescriptor> found = scored.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(value -> value.descriptor().name()))
                .limit(query.limit()).map(Scored::descriptor).toList();
        found.forEach(value -> session.discover(value.name()));
        return new ToolSearchResult(query.query(), found);
    }

    public List<ToolSpecification> langChainSpecifications(WishAgentSession session) {
        return registry.visible(session).stream().map(tool -> ToolSpecification.builder()
                .name(tool.descriptor().name())
                .description(tool.descriptor().description())
                // The project-owned Gson schema remains authoritative for the
                // existing provider adapter. This view is only for LC4J search.
                .parameters(JsonObjectSchema.builder().build())
                .build()).toList();
    }

    private static int score(WishToolDescriptor descriptor, Set<String> terms) {
        String name = descriptor.name().toLowerCase(Locale.ROOT);
        String haystack = (descriptor.description() + " " + descriptor.capabilities() + " "
                + descriptor.contractTypes() + " " + descriptor.featureTypes()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (name.contains(term)) score += 4;
            if (haystack.contains(term)) score += 2;
        }
        return score;
    }

    private static Set<String> terms(String query) {
        Set<String> values = new HashSet<>();
        for (String value : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_:]+")) {
            if (!value.isBlank()) values.add(value);
        }
        return values;
    }

    private record Scored(WishToolDescriptor descriptor, int score) { }
}
