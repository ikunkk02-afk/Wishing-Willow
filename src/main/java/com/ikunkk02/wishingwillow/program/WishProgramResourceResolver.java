package com.ikunkk02.wishingwillow.program;

import com.ikunkk02.wishingwillow.research.RegistryEntryType;

import javax.annotation.Nullable;

/**
 * Live-resource lookup used by the server-side program validator. The production implementation
 * wraps Forge registries; tests provide map-backed fakes so the whole validation chain can run
 * headless.
 */
public interface WishProgramResourceResolver {
    /**
     * Resolves a possibly namespace-less id against the live registry of the given type.
     * Returns the canonical id (with namespace) or {@code null} when it does not exist.
     */
    @Nullable String resolve(RegistryEntryType type, String id);

    /** Resolves a dimension id against the server's loaded level keys. */
    @Nullable String resolveDimension(String id);

    /** Whether a built-in predefined event is registered. */
    boolean containsPredefinedEvent(String event);
}
