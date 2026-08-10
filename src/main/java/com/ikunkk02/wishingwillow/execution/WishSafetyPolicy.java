package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.planning.CapabilityMatcher;
import com.ikunkk02.wishingwillow.planning.CandidateReference;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;

public final class WishSafetyPolicy {
    public static final int THIRD_PARTY_ENTITY_MIN_SEVERITY = 61;
    public static final int HIGH_RISK_MIN_SEVERITY = 81;
    public static final int HIGH_RISK_SCORE = 85;

    private WishSafetyPolicy() { }

    public static int canonicalRisk(CandidateReference reference) {
        int risk = CapabilityMatcher.risk(reference.providedCapability());
        if (isThirdPartyEntity(reference)) risk = Math.max(70, risk);
        return risk;
    }

    public static WishPolicyDecision validate(WishPlanStep step, int severity,
                                              ExecutionSettingsSnapshot settings) {
        if(!settings.enabled())return WishPolicyDecision.reject(WishExecutionAcceptError.EXECUTION_DISABLED,
                "Wish execution is disabled by server settings");
        CandidateReference candidate = step.candidateReference();
        int risk = canonicalRisk(candidate);
        if ((candidate.providedCapability() == WishCapability.POWERFUL_ENEMY || risk >= HIGH_RISK_SCORE)
                && severity < HIGH_RISK_MIN_SEVERITY) {
            return WishPolicyDecision.reject(WishExecutionAcceptError.RISK_TOO_HIGH,
                    "risk=" + risk + " requires severity=" + HIGH_RISK_MIN_SEVERITY);
        }
        boolean destructive = step.action() == WishActionType.CHANGE_BLOCK
                || step.action() == WishActionType.REPLACE_BLOCK_AREA
                || step.action() == WishActionType.EXPLOSION
                && step.parameters().has("destroy_blocks")
                && step.parameters().get("destroy_blocks").getAsBoolean();
        if (destructive && severity > settings.maximumDestructiveSeverity()) {
            return WishPolicyDecision.reject(WishExecutionAcceptError.DESTRUCTIVE_SEVERITY_DISABLED,
                    "severity=" + severity + " maximum=" + settings.maximumDestructiveSeverity());
        }
        if (step.action() == WishActionType.CHANGE_BLOCK || step.action() == WishActionType.REPLACE_BLOCK_AREA) {
            if (!settings.blockModification()) {
                return WishPolicyDecision.reject(WishExecutionAcceptError.BLOCK_MODIFICATION_DISABLED,
                        "Block modification is disabled by server settings");
            }
            if (settings.debugSafeMode()) {
                return WishPolicyDecision.reject(WishExecutionAcceptError.DEBUG_SAFE_MODE,
                        "Block modification is disabled by debug safe mode");
            }
        }
        if (step.action() == WishActionType.EXPLOSION) {
            if (!settings.explosions()) {
                return WishPolicyDecision.reject(WishExecutionAcceptError.EXPLOSIONS_DISABLED,
                        "Explosions are disabled by server settings");
            }
            boolean destroys = step.parameters().get("destroy_blocks").getAsBoolean();
            double power = step.parameters().get("power").getAsDouble();
            if (destroys && !settings.destructiveExplosions()) {
                return WishPolicyDecision.reject(WishExecutionAcceptError.DESTRUCTIVE_EXPLOSIONS_DISABLED,
                        "Destructive explosions are disabled by server settings");
            }
            if (settings.debugSafeMode() && (destroys || power > 2)) {
                return WishPolicyDecision.reject(WishExecutionAcceptError.DEBUG_SAFE_MODE,
                        "Debug safe mode permits only non-destructive explosion power <= 2");
            }
        }
        if (step.action() == WishActionType.TELEPORT
                && "CANDIDATE_DIMENSION".equals(step.parameters().get("mode").getAsString())
                && !settings.crossDimensionTeleport()) {
            return WishPolicyDecision.reject(WishExecutionAcceptError.CROSS_DIMENSION_TELEPORT_DISABLED,
                    "Cross-dimension teleport is disabled by server settings");
        }
        if (step.action() == WishActionType.SPAWN_ENTITY && isThirdPartyEntity(candidate)) {
            if (!settings.thirdPartyEntities()) {
                return WishPolicyDecision.reject(WishExecutionAcceptError.THIRD_PARTY_ENTITY_DISABLED,
                        "Third-party entities are disabled by server settings");
            }
            if (severity < THIRD_PARTY_ENTITY_MIN_SEVERITY) {
                return WishPolicyDecision.reject(WishExecutionAcceptError.THIRD_PARTY_ENTITY_SEVERITY,
                        "Third-party entity requires severity=" + THIRD_PARTY_ENTITY_MIN_SEVERITY);
            }
        }
        return WishPolicyDecision.allow();
    }

    public static boolean candidateAllowed(CandidateReference reference, int severity,
                                           ExecutionSettingsSnapshot settings) {
        if(!settings.enabled())return false;
        int risk = canonicalRisk(reference);
        if ((reference.providedCapability() == WishCapability.POWERFUL_ENEMY || risk >= HIGH_RISK_SCORE)
                && severity < HIGH_RISK_MIN_SEVERITY) return false;
        if (isThirdPartyEntity(reference)
                && (!settings.thirdPartyEntities() || severity < THIRD_PARTY_ENTITY_MIN_SEVERITY)) return false;
        if (reference.providedCapability() == WishCapability.BLOCK_CHANGE
                && (!settings.blockModification() || settings.debugSafeMode())) return false;
        if (reference.providedCapability() == WishCapability.EXPLOSION && !settings.explosions()) return false;
        if (reference.registryResource() != null
                && reference.registryResource().type() == com.ikunkk02.wishingwillow.research.RegistryEntryType.DIMENSION
                && !settings.crossDimensionTeleport()) return false;
        return true;
    }

    private static boolean isThirdPartyEntity(CandidateReference reference) {
        return reference.registryResource() != null
                && reference.registryResource().type() == com.ikunkk02.wishingwillow.research.RegistryEntryType.ENTITY
                && !reference.registryResource().id().startsWith("minecraft:");
    }
}
