package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.wish.WishRecord;
import com.ikunkk02.wishingwillow.wish.WishSavedData;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class WishPlanStore {
    private WishPlanStore() { }

    public static boolean updateState(MinecraftServer server, UUID sessionId, WishPlanState state) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY || record.planState() == WishPlanState.STALE) return false;
        data.update(record.withPlanning(state, WishPlanError.NONE, null));
        return true;
    }

    public static boolean fail(MinecraftServer server, UUID sessionId, WishPlanError error) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY) return false;
        data.update(record.withPlanning(WishPlanState.FAILED, error, null));
        return true;
    }

    public static boolean partial(MinecraftServer server, UUID sessionId, WishPlanError error) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY) return false;
        data.update(record.withPlanning(WishPlanState.PARTIAL, error, null));
        return true;
    }

    public static WishPlanState accept(MinecraftServer server, UUID sessionId, WishInterpretation interpretation,
                                       String rawDraft, CapabilityCatalog catalog) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY) throw new IllegalArgumentException("INVALID_SESSION");
        WishPlanValidation validation = WishPlanValidator.parseAndValidate(rawDraft, interpretation, catalog,
                new ServerPlanningEnvironment(server));
        Set<String> mods = new LinkedHashSet<>();
        Set<String> registries = new LinkedHashSet<>();
        validation.draft().steps().forEach(step -> {
            mods.add(step.candidateReference().sourceModId());
            if (step.candidateReference().registryResource() != null) {
                registries.add(step.candidateReference().registryResource().id());
            }
        });
        WishPlan plan = new WishPlan(UUID.randomUUID(), sessionId, 1, validation.draft().summary(),
                validation.draft().delivery(), validation.draft().severity(),
                validation.draft().estimatedDuration(), validation.draft().steps(), mods, registries,
                validation.unfulfilledCapabilities(), server.overworld().getGameTime(), System.currentTimeMillis(),
                catalog.knowledgeState(), catalog.knowledgeDigest(), catalog.registryDigest(), catalog.catalogHash());
        data.update(record.withPlanning(validation.state(), WishPlanError.NONE, plan));
        return validation.state();
    }

    public static void revalidateAll(MinecraftServer server) {
        WishSavedData data = WishSavedData.get(server);
        for (WishRecord record : data.allRecords()) {
            if (record.planState() == WishPlanState.READY && record.plan() != null) {
                try {
                    WishPlanValidator.validateStored(record.plan(), new ServerPlanningEnvironment(server));
                } catch (IllegalArgumentException exception) {
                    data.update(record.withPlanning(WishPlanState.STALE, WishPlanError.STALE_RESOURCE, record.plan()));
                }
            }
        }
    }
}
