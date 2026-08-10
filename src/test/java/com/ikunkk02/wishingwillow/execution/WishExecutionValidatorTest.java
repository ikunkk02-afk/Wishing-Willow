package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WishExecutionValidatorTest {
    @Test void rejectsMissingPlanBeforeWorldAccess(){assertEquals("INVALID_PLAN",assertThrows(IllegalArgumentException.class,()->WishExecutionValidator.validate(null,null,WishActionRegistry.defaults())).getMessage());}
    @Test void enforcesHardDestructiveLimits(){assertTrue(WishExecutionSafety.validExplosionPower(8));assertFalse(WishExecutionSafety.validExplosionPower(1000));assertTrue(WishExecutionSafety.validBlockLimit(16,2048));assertFalse(WishExecutionSafety.validBlockLimit(16,1_000_000));}
}
