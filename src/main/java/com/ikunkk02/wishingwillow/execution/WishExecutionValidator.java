package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.planning.ServerPlanningEnvironment;
import com.ikunkk02.wishingwillow.planning.WishPlan;
import com.ikunkk02.wishingwillow.planning.WishPlanBudget;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

public final class WishExecutionValidator {
    private WishExecutionValidator() { }

    public static void validate(MinecraftServer server, WishPlan plan, WishActionRegistry registry) {
        WishExecutionValidationResult result = validateDetailed(server, plan, registry);
        if (!result.valid()) throw new IllegalArgumentException(result.error().name());
    }

    public static WishExecutionValidationResult validateDetailed(MinecraftServer server, WishPlan plan,
                                                                 WishActionRegistry registry) {
        if (plan == null || plan.schemaVersion() != 1 || plan.steps().isEmpty()
                || plan.steps().size() > WishPlanBudget.maxSteps(plan.severity())) {
            return WishExecutionValidationResult.rejected(WishExecutionAcceptError.INVALID_PLAN,
                    -1, null, "Invalid plan schema or step count");
        }
        int destructive = 0;
        for (int index = 0; index < plan.steps().size(); index++) {
            WishExecutionValidationResult step = validateStepDetailed(server, plan, index, registry);
            if (!step.valid()) return step;
            destructive += WishPlanBudget.destructiveCost(plan.steps().get(index));
        }
        if (destructive > WishPlanBudget.maxDestructiveCost(plan.severity())) {
            return WishExecutionValidationResult.rejected(WishExecutionAcceptError.BUDGET_EXCEEDED,
                    -1, null, "Destructive cost exceeds plan budget");
        }
        return WishExecutionValidationResult.success();
    }

    static void validateStep(MinecraftServer server, WishPlan plan, int index, WishActionRegistry registry) {
        WishExecutionValidationResult result = validateStepDetailed(server, plan, index, registry);
        if (!result.valid()) throw new IllegalArgumentException(result.error().name());
    }

    static WishExecutionValidationResult validateStepDetailed(MinecraftServer server, WishPlan plan, int index,
                                                               WishActionRegistry registry) {
        if (index < 0 || index >= plan.steps().size()) {
            return WishExecutionValidationResult.rejected(WishExecutionAcceptError.INVALID_PLAN,
                    index, null, "Step index is outside plan");
        }
        WishPlanStep step = plan.steps().get(index);
        if (step.stepIndex() != index) return reject(step, WishExecutionAcceptError.INVALID_PARAMETER,
                "Stored step index is not contiguous");
        var reference = step.candidateReference();
        if (reference == null || !reference.candidateId().equals(step.candidateId())
                || reference.requestedCapability() != step.capability()) {
            return reject(step, WishExecutionAcceptError.INVALID_CANDIDATE,
                    "Step and candidate reference do not match");
        }
        WishPolicyDecision action = WishActionPolicy.validate(reference, step.action(), step.parameters(),
                step.target(), step.timing(), step.delaySeconds(), step.trigger(), plan.severity());
        if (!action.allowed()) return reject(step, action.error(), action.detail());

        if (reference.registryResource() != null) {
            var resource = reference.registryResource();
            ResourceLocation id = ResourceLocation.tryParse(resource.id());
            if (id == null) return reject(step, WishExecutionAcceptError.INVALID_RESOURCE,
                    "Registry id is malformed");
            if (!new ServerPlanningEnvironment(server).contains(resource.type(), resource.id())) {
                return reject(step, WishExecutionAcceptError.STALE_RESOURCE,
                        "Registry resource is no longer present");
            }
            if (!id.getNamespace().equals("minecraft") && !id.getNamespace().equals(WishingWillow.MOD_ID)
                    && !ModList.get().isLoaded(id.getNamespace())) {
                return reject(step, WishExecutionAcceptError.INVALID_RESOURCE,
                        "Registry namespace is not loaded");
            }
        }
        WishPolicyDecision safety = WishSafetyPolicy.validate(step, plan.severity(),
                ExecutionSettingsSnapshot.planning());
        if (!safety.allowed()) return reject(step, safety.error(), safety.detail());
        if (!registry.contains(step.action())) return reject(step, WishExecutionAcceptError.UNSUPPORTED_ACTION,
                "No server executor is registered for action");
        return WishExecutionValidationResult.success();
    }

    private static WishExecutionValidationResult reject(WishPlanStep step, WishExecutionAcceptError error,
                                                        String detail) {
        return WishExecutionValidationResult.rejected(error, step.stepIndex(), step.action(), detail);
    }
}
