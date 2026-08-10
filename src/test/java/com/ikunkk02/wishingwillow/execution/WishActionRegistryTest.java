package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WishActionRegistryTest {
    @Test void registersEveryWhitelistedAction(){var registry=WishActionRegistry.defaults();assertEquals(WishActionType.values().length,registry.registered().size());for(WishActionType action:WishActionType.values())assertNotNull(registry.get(action),action.name());}
}
