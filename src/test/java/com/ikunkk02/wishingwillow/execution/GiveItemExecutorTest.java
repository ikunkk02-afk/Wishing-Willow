package com.ikunkk02.wishingwillow.execution;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;import com.ikunkk02.wishingwillow.planning.WishActionType;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class GiveItemExecutorTest {@Test void giveIsRegisteredAndCountIsPacketBounded(){assertNotNull(WishActionRegistry.defaults().get(WishActionType.GIVE_ITEM));assertTrue(WishExecutionSafety.validItemCount(64));assertFalse(WishExecutionSafety.validItemCount(640));}}
