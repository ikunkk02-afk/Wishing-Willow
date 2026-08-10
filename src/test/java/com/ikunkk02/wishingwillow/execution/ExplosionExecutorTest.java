package com.ikunkk02.wishingwillow.execution;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ExplosionExecutorTest {@Test void hardLimitCannotBeBypassed(){assertTrue(WishExecutionSafety.validExplosionPower(3));assertTrue(WishExecutionSafety.validExplosionPower(8));assertFalse(WishExecutionSafety.validExplosionPower(8.01));assertFalse(WishExecutionSafety.validExplosionPower(1000));}}
