package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.planning.CandidateReference;
import com.ikunkk02.wishingwillow.planning.WishPlan;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

public record WishExecutionContext(ServerLevel level, @Nullable ServerPlayer player, WishPlan plan,
                                   WishPlanStep step, CandidateReference candidate,
                                   WishExecutionRecord execution) { }
