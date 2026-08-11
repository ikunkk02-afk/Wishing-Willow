package com.ikunkk02.wishingwillow.wish;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiExecutionMode;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishInterpretationValidator;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningRequestPacket;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishContextCollector;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.planning.WishPlanState;
import com.ikunkk02.wishingwillow.planning.WishPlanStore;
import com.ikunkk02.wishingwillow.planning.FallbackWishPlanner;
import com.ikunkk02.wishingwillow.planning.WishPlanResult;
import com.ikunkk02.wishingwillow.planning.ServerPlanningEnvironment;
import com.ikunkk02.wishingwillow.execution.WishExecutionManager;
import com.ikunkk02.wishingwillow.execution.WishExecutionAcceptResult;
import com.ikunkk02.wishingwillow.execution.WishExecutionAcceptError;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.item.WishingWillowItem;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.OpenWishScreenPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStatePacket;
import com.ikunkk02.wishingwillow.network.packet.WishOmenPacket;
import com.ikunkk02.wishingwillow.agent.core.WishAgentDebugStore;
import com.ikunkk02.wishingwillow.omen.WishOmenGenerator;
import com.ikunkk02.wishingwillow.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import software.bernie.geckolib.animatable.GeoItem;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WishManager {
    private static final int SUBMIT_COOLDOWN_TICKS = 20;
    private static final int MIN_SNAP_TICKS = 20;
    private static final int PRE_SNAP_TIMEOUT_TICKS = 200;
    private static final int POST_SNAP_TIMEOUT_TICKS = 100;
    private static final int RECENT_REQUEST_LIMIT = 32;

    private static final Map<UUID, WishSession> ACTIVE_SESSIONS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<UUID>> RECENT_REQUESTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SUBMISSIONS = new HashMap<>();
    private static final Map<UUID, PlanningAttempt> PLANNING_ATTEMPTS = new HashMap<>();

    private WishManager() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(WishManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(WishManager::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(WishManager::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(WishManager::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(WishManager::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(WishManager::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(WishManager::onServerStarted);
    }

    public static boolean tryOpenScreen(ServerPlayer player, InteractionHand hand) {
        if (ACTIVE_SESSIONS.containsKey(player.getUUID())) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.WISHING_WILLOW.get())) {
            return false;
        }
        ModNetworking.sendToPlayer(player, new OpenWishScreenPacket(hand));
        return true;
    }

    public static WishResult submit(
            ServerPlayer player,
            UUID requestId,
            InteractionHand hand,
            String rawWish,
            AiExecutionMode executionMode,
            AiProviderType providerType,
            String model
    ) {
        long gameTime = player.serverLevel().getGameTime();
        UUID playerId = player.getUUID();

        if (isDuplicateRequest(playerId, requestId)) {
            return WishResult.rejected(WishRejectionReason.DUPLICATE);
        }
        rememberRequest(playerId, requestId);

        Long lastSubmission = LAST_SUBMISSIONS.get(playerId);
        if (lastSubmission != null && gameTime - lastSubmission < SUBMIT_COOLDOWN_TICKS) {
            return WishResult.rejected(WishRejectionReason.COOLDOWN);
        }
        LAST_SUBMISSIONS.put(playerId, gameTime);

        if (ACTIVE_SESSIONS.containsKey(playerId)) {
            return reject(player, requestId, WishRejectionReason.BUSY);
        }

        if (executionMode != AiExecutionMode.PLAYER_PROVIDED
                || providerType == null
                || !isValidModel(model)) {
            return reject(player, requestId, WishRejectionReason.AI_NOT_CONFIGURED);
        }

        WishTextValidator.Validation validation = WishTextValidator.validate(rawWish);
        if (!validation.valid()) {
            return reject(player, requestId, validation.reason());
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.WISHING_WILLOW.get())) {
            return reject(player, requestId, WishRejectionReason.NOT_HOLDING);
        }

        ServerLevel level = player.serverLevel();
        long itemInstanceId = GeoItem.getOrAssignId(stack, level);
        WishSession session = new WishSession(
                UUID.randomUUID(),
                playerId,
                validation.normalizedText(),
                level.dimension().location(),
                gameTime,
                System.currentTimeMillis(),
                hand,
                itemInstanceId,
                executionMode,
                providerType,
                model.strip()
        );

        ACTIVE_SESSIONS.put(playerId, session);
        persist(player.server, session);

        session.transitionTo(WishState.ANIMATING, gameTime);
        persist(player.server, session);
        ModNetworking.sendToPlayer(
                player,
                new WishStartedPacket(
                        session.sessionId(), hand, itemInstanceId, stack.copy(),
                        session.rawWish(), session.providerType(), session.model()
                )
        );
        ((WishingWillowItem) stack.getItem()).triggerWishAnimation(player, itemInstanceId);
        return WishResult.accepted(session);
    }

    public static void handleAnimationEvent(ServerPlayer player, UUID sessionId, WishAnimationEvent event) {
        WishSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null || !session.sessionId().equals(sessionId)) {
            return;
        }

        if (event == WishAnimationEvent.SNAP) {
            handleSnap(player, session);
        } else if (event == WishAnimationEvent.FINISH && session.state() == WishState.SNAPPED) {
            finish(player.server, session, player);
        }
    }

    public static void handleInterpretationResult(
            ServerPlayer player,
            UUID sessionId,
            InterpretationState state,
            AiErrorCategory errorCategory,
            @Nullable WishInterpretation interpretation
    ) {
        if (state != InterpretationState.SUCCESS
                && state != InterpretationState.AI_REQUEST_FAILED
                && state != InterpretationState.INVALID_RESPONSE) {
            return;
        }
        WishSavedData data = WishSavedData.get(player.server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null
                || !record.playerId().equals(player.getUUID())
                || record.interpretationState() != InterpretationState.REQUESTING) {
            return;
        }

        WishInterpretation acceptedInterpretation = null;
        AiErrorCategory acceptedError = errorCategory == null ? AiErrorCategory.UNKNOWN : errorCategory;
        if (state == InterpretationState.SUCCESS) {
            if (interpretation == null) {
                return;
            }
            try {
                WishInterpretationValidator.validate(interpretation);
            } catch (IllegalArgumentException exception) {
                state = InterpretationState.INVALID_RESPONSE;
                acceptedError = AiErrorCategory.MALFORMED_RESPONSE;
            }
            if (state == InterpretationState.SUCCESS) {
                acceptedInterpretation = interpretation;
                acceptedError = AiErrorCategory.NONE;
            }
        } else if (acceptedError == AiErrorCategory.NONE) {
            acceptedError = state == InterpretationState.INVALID_RESPONSE
                    ? AiErrorCategory.MALFORMED_RESPONSE
                    : AiErrorCategory.UNKNOWN;
        }

        long now = System.currentTimeMillis();
        WishSession active = ACTIVE_SESSIONS.get(player.getUUID());
        if (active != null && active.sessionId().equals(sessionId)) {
            active.completeInterpretation(state, acceptedError, acceptedInterpretation, now);
            persist(player.server, active);
        } else {
            data.updateInterpretation(sessionId, state, acceptedError, acceptedInterpretation, now);
        }
        if (state == InterpretationState.SUCCESS && acceptedInterpretation != null) {
            WishPipelineAudit.success(sessionId,"INTERPRETATION",
                    "severity="+acceptedInterpretation.severity()+" capabilities="+acceptedInterpretation.requiredCapabilities().size());
            beginPlanning(player, sessionId, acceptedInterpretation);
        } else {
            WishPipelineAudit.failure(sessionId,"INTERPRETATION",acceptedError.name(),state.name());
            WishPlanStore.fail(player.server, sessionId, WishPlanError.AI_REQUEST_FAILED);
        }
    }

    public static void handlePlanningProgress(ServerPlayer player, UUID sessionId, UUID attemptId,
                                              WishPlanState state) {
        PlanningAttempt attempt = PLANNING_ATTEMPTS.get(sessionId);
        if (attempt == null || !attempt.attemptId.equals(attemptId)
                || !attempt.playerId.equals(player.getUUID())
                || (state != WishPlanState.PLANNING && state != WishPlanState.VALIDATING)) return;
        WishPlanStore.updateState(player.server, sessionId, state);
    }

    public static void handlePlanningCancellation(ServerPlayer player, UUID sessionId, UUID attemptId) {
        PlanningAttempt attempt = PLANNING_ATTEMPTS.get(sessionId);
        if (attempt == null || !attempt.attemptId.equals(attemptId)
                || !attempt.playerId.equals(player.getUUID())) return;
        PLANNING_ATTEMPTS.remove(sessionId);
        WishPlanStore.fail(player.server, sessionId, WishPlanError.AI_REQUEST_FAILED);
        WishPipelineAudit.failure(sessionId, "PLANNING", "CANCELLED_SUPERSEDED",
                "newer wish planning request replaced this attempt");
        WishingWillow.LOGGER.info("Wish planning cancelled session={} attempt={} reason=SUPERSEDED",
                sessionId, attemptId);
    }

    public static void handlePlanSubmission(ServerPlayer player, UUID sessionId, UUID attemptId,
                                            WishPlanError error, int attemptsUsed,
                                            @Nullable CapabilityCatalog catalog,
                                            @Nullable String draftJson) {
        PlanningAttempt attempt = PLANNING_ATTEMPTS.get(sessionId);
        if (attempt == null || !attempt.attemptId.equals(attemptId)
                || !attempt.playerId.equals(player.getUUID())) return;
        PLANNING_ATTEMPTS.remove(sessionId);
        WishRecord record = WishSavedData.get(player.server).getBySession(sessionId);
        if (record == null || record.interpretation() == null
                || !record.playerId().equals(player.getUUID())
                || record.planState() == WishPlanState.READY
                || record.executionId() != null) return;
        if(catalog!=null)WishPipelineAudit.success(sessionId,"MATCHING","candidates="+catalog.candidates().size());
        if (error != WishPlanError.NONE || catalog == null || draftJson == null) {
            if (error == WishPlanError.NO_CANDIDATES || error == WishPlanError.UNSATISFIED_CAPABILITIES) {
                WishPlanStore.partial(player.server, sessionId, WishPlanError.UNSATISFIED_CAPABILITIES);
                WishPipelineAudit.failure(sessionId, "PLANNING", WishPlanError.UNSATISFIED_CAPABILITIES.name(), "no executable primary capability");
                sendPlanningFailure(player, sessionId);
                return;
            }
            WishPlanError acceptedError=error == WishPlanError.NONE ? WishPlanError.UNKNOWN : error;
            WishPlanStore.fail(player.server, sessionId, acceptedError);
            WishPipelineAudit.failure(sessionId, "PLANNING", acceptedError.name(), "planner returned no plan");
            sendPlanningFailure(player, sessionId);
            return;
        }
        try {
            WishPlanStore.accept(player.server, sessionId, attemptId, record.interpretation(), draftJson, catalog);
            WishRecord accepted = WishSavedData.get(player.server).getBySession(sessionId);
            if (accepted != null && (accepted.planState() == WishPlanState.READY
                    || accepted.planState() == WishPlanState.PARTIAL) && accepted.plan() != null) {
                if("Controlled contract-preserving fallback".equals(accepted.plan().summary()))
                    WishPipelineAudit.success(sessionId,"FALLBACK",
                            "plan="+accepted.plan().planId()+" steps="+accepted.plan().steps().size());
                WishPipelineAudit.success(sessionId,"PLANNING","state="+accepted.planState()+" steps="+accepted.plan().steps().size());
                WishPipelineAudit.success(sessionId,"SERVER_PLAN_VALIDATION",
                        "plan="+accepted.plan().planId()+" state="+accepted.planState()+" steps="+accepted.plan().steps().size());
                WishExecutionAcceptResult result=WishExecutionManager.accept(player, accepted.plan());
                if(!result.accepted())result=tryFallbackAfterExecutionRejection(player,accepted,catalog,result,attemptsUsed);
                if(!result.accepted())player.sendSystemMessage(Component.translatable("message.wishing_willow.technical_failure"));
                else sendOmenForAcceptedPlan(player,sessionId);
            }
        } catch (IllegalArgumentException exception) {
            WishPlanError acceptedError=planError(exception);
            WishPlanStore.fail(player.server, sessionId, acceptedError);
            WishPipelineAudit.failure(sessionId,"SERVER_PLAN_VALIDATION",acceptedError.name(),exception.getMessage());
            sendPlanningFailure(player, sessionId);
        }
    }

    @Nullable
    public static WishRecord getLatestWish(MinecraftServer server, UUID playerId) {
        return WishSavedData.get(server).getLatest(playerId);
    }

    private static void handleSnap(ServerPlayer player, WishSession session) {
        if (session.state() == WishState.SNAPPED || session.state() == WishState.FINISHED) {
            return;
        }
        if (session.state() != WishState.ANIMATING) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();
        if (gameTime - session.submittedGameTime() < MIN_SNAP_TICKS) {
            return;
        }
        if (!isHeldSessionItem(player, session)) {
            cancel(player.server, session, player, WishRejectionReason.INTERRUPTED);
            return;
        }

        ItemStack stack = player.getItemInHand(session.hand());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        session.transitionTo(WishState.SNAPPED, gameTime);
        session.markInterpretationRequesting(System.currentTimeMillis());
        persist(player.server, session);

        ServerLevel level = player.serverLevel();
        level.playSound(
                player,
                player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.WOOD_BREAK,
                SoundSource.PLAYERS,
                0.7F,
                0.9F
        );
        level.playSound(
                player,
                player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.WOOD_HIT,
                SoundSource.PLAYERS,
                0.25F,
                0.72F
        );
        ModNetworking.sendToPlayer(
                player,
                new WishStatePacket(session.sessionId(), WishState.SNAPPED, WishRejectionReason.NONE)
        );
    }

    private static boolean isHeldSessionItem(ServerPlayer player, WishSession session) {
        ItemStack stack = player.getItemInHand(session.hand());
        return stack.is(ModItems.WISHING_WILLOW.get())
                && GeoItem.getId(stack) == session.itemInstanceId();
    }

    private static WishResult reject(ServerPlayer player, UUID requestId, WishRejectionReason reason) {
        ModNetworking.sendToPlayer(
                player,
                new WishStatePacket(requestId, WishState.CANCELLED, reason)
        );
        return WishResult.rejected(reason);
    }

    private static boolean isDuplicateRequest(UUID playerId, UUID requestId) {
        ArrayDeque<UUID> requests = RECENT_REQUESTS.get(playerId);
        return requests != null && requests.contains(requestId);
    }

    private static void rememberRequest(UUID playerId, UUID requestId) {
        ArrayDeque<UUID> requests = RECENT_REQUESTS.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        requests.addLast(requestId);
        while (requests.size() > RECENT_REQUEST_LIMIT) {
            requests.removeFirst();
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (WishSession session : List.copyOf(ACTIVE_SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            if (player == null) {
                if (session.state() == WishState.SNAPPED) {
                    finish(server, session, null);
                } else {
                    cancel(server, session, null, WishRejectionReason.INTERRUPTED);
                }
                continue;
            }

            long gameTime = player.serverLevel().getGameTime();
            if (session.state() == WishState.ANIMATING) {
                if (!isHeldSessionItem(player, session)) {
                    cancel(server, session, player, WishRejectionReason.INTERRUPTED);
                } else if (gameTime - session.stateChangedGameTime() > PRE_SNAP_TIMEOUT_TICKS) {
                    cancel(server, session, player, WishRejectionReason.TIMEOUT);
                }
            } else if (session.state() == WishState.SNAPPED
                    && gameTime - session.stateChangedGameTime() > POST_SNAP_TIMEOUT_TICKS) {
                finish(server, session, player);
            }
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            completeOrCancelForLifecycle(player);
            WishSavedData.get(player.server).failPendingForPlayer(player.getUUID());
            RECENT_REQUESTS.remove(player.getUUID());
            LAST_SUBMISSIONS.remove(player.getUUID());
            failPlanningForPlayer(player.server, player.getUUID());
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        WishRecord resumable = WishSavedData.get(player.server).getLatestResumablePlanning(player.getUUID());
        if (resumable == null || resumable.interpretation() == null) return;
        WishingWillow.LOGGER.info("Wish planning resumed after reconnect session={}", resumable.sessionId());
        beginPlanning(player, resumable.sessionId(), resumable.interpretation());
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            completeOrCancelForLifecycle(player);
        }
    }

    private static void completeOrCancelForLifecycle(ServerPlayer player) {
        WishSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.state() == WishState.SNAPPED) {
            finish(player.server, session, player);
        } else {
            cancel(player.server, session, player, WishRejectionReason.INTERRUPTED);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (WishSession session : List.copyOf(ACTIVE_SESSIONS.values())) {
            if (session.state() == WishState.SNAPPED) {
                finish(server, session, null);
            } else {
                cancel(server, session, null, WishRejectionReason.INTERRUPTED);
            }
        }
        WishSavedData.get(server).failAllPending();
        for (UUID sessionId : List.copyOf(PLANNING_ATTEMPTS.keySet())) {
            WishPlanStore.fail(server, sessionId, WishPlanError.DISCONNECTED);
        }
        PLANNING_ATTEMPTS.clear();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        WishPlanStore.revalidateAll(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_SESSIONS.clear();
        RECENT_REQUESTS.clear();
        LAST_SUBMISSIONS.clear();
        PLANNING_ATTEMPTS.clear();
    }

    private static void cancel(
            MinecraftServer server,
            WishSession session,
            @Nullable ServerPlayer player,
            WishRejectionReason reason
    ) {
        if (session.state() == WishState.CANCELLED || session.state() == WishState.FINISHED) {
            return;
        }
        session.transitionTo(WishState.CANCELLED, server.overworld().getGameTime());
        persist(server, session);
        WishRecord cancelled=WishSavedData.get(server).getBySession(session.sessionId());
        if(cancelled!=null&&cancelled.executionId()==null)
            WishSavedData.get(server).update(cancelled.withExecution(null,
                    com.ikunkk02.wishingwillow.execution.WishExecutionState.CANCELLED,
                    WishExecutionAcceptError.NONE,"wish lifecycle cancelled: "+reason.name()));
        ACTIVE_SESSIONS.remove(session.playerId(), session);
        if (player != null) {
            ModNetworking.sendToPlayer(
                    player,
                    new WishStatePacket(session.sessionId(), WishState.CANCELLED, reason)
            );
        }
    }

    private static void finish(MinecraftServer server, WishSession session, @Nullable ServerPlayer player) {
        if (session.state() == WishState.FINISHED) {
            return;
        }
        session.transitionTo(WishState.FINISHED, server.overworld().getGameTime());
        persist(server, session);
        ACTIVE_SESSIONS.remove(session.playerId(), session);
        if (player != null) {
            ModNetworking.sendToPlayer(
                    player,
                    new WishStatePacket(session.sessionId(), WishState.FINISHED, WishRejectionReason.NONE)
            );
        }
    }

    private static void persist(MinecraftServer server, WishSession session) {
        WishSavedData.get(server).update(session);
    }

    private static boolean isValidModel(String model) {
        if (model == null || model.isBlank() || model.length() > AiConfig.MAX_MODEL_LENGTH) {
            return false;
        }
        for (int index = 0; index < model.length(); index++) {
            if (Character.isISOControl(model.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static void beginPlanning(ServerPlayer player, UUID sessionId, WishInterpretation interpretation) {
        WishRecord record = WishSavedData.get(player.server).getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY) return;
        UUID attemptId = UUID.randomUUID();
        PLANNING_ATTEMPTS.put(sessionId, new PlanningAttempt(attemptId, player.getUUID()));
        WishPlanStore.updateState(player.server, sessionId, WishPlanState.MATCHING);
        ModNetworking.sendToPlayer(player, new WishPlanningRequestPacket(sessionId, attemptId, record.rawWish(),
                record.providerType(), record.model(), interpretation, WishContextCollector.collect(player),
                ExecutionSettingsSnapshot.planning()));
    }

    private static void failPlanningForPlayer(MinecraftServer server, UUID playerId) {
        List<UUID> sessions = PLANNING_ATTEMPTS.entrySet().stream()
                .filter(entry -> entry.getValue().playerId.equals(playerId)).map(Map.Entry::getKey).toList();
        sessions.forEach(session -> {
            PLANNING_ATTEMPTS.remove(session);
            WishPlanStore.fail(server, session, WishPlanError.DISCONNECTED);
        });
    }

    private static WishPlanError planError(IllegalArgumentException exception) {
        try { return WishPlanError.valueOf(exception.getMessage()); }
        catch (RuntimeException ignored) { return WishPlanError.UNKNOWN; }
    }

    private static WishExecutionAcceptResult tryFallbackAfterExecutionRejection(ServerPlayer player,
                                                                                 WishRecord record,
                                                                                 CapabilityCatalog catalog,
                                                                                 WishExecutionAcceptResult rejected,
                                                                                 int attemptsUsed) {
        if(attemptsUsed>=com.ikunkk02.wishingwillow.planning.WishPlanRepairCoordinator.MAX_ATTEMPTS
                ||!fallbackEligible(rejected.error())||record.interpretation()==null)return rejected;
        int fallbackAttempt=attemptsUsed+1;
        WishingWillow.LOGGER.info("Wish fulfillment replan reason={} attempt={}",rejected.error(),fallbackAttempt);
        WishPlanResult fallback=new FallbackWishPlanner().plan(record.rawWish(),record.interpretation(),
                WishContextCollector.collect(player),catalog,new ServerPlanningEnvironment(player.server),
                ExecutionSettingsSnapshot.planning());
        if(fallback.draft()==null){
            WishPipelineAudit.failure(record.sessionId(),"FALLBACK",fallback.error().name(),"no contract-preserving fallback");
            return rejected;
        }
        try{
            WishPlanStore.replaceAfterExecutionRejection(player.server,record.sessionId(),record.interpretation(),
                    fallback.draft(),catalog);
            WishRecord replacement=WishSavedData.get(player.server).getBySession(record.sessionId());
            if(replacement==null||replacement.plan()==null)return rejected;
            WishPipelineAudit.success(record.sessionId(),"FALLBACK",
                    "plan="+replacement.plan().planId()+" steps="+replacement.plan().steps().size());
            String source=replacement.plan().steps().get(0).candidateReference().sourceKind()
                    ==com.ikunkk02.wishingwillow.planning.CandidateSourceKind.VANILLA_REGISTRY
                    ?"VANILLA":"WISHING_WILLOW_BUILTIN";
            WishingWillow.LOGGER.info("Wish fulfillment fallback source={}",source);
            return WishExecutionManager.accept(player,replacement.plan());
        }catch(IllegalArgumentException error){
            WishPipelineAudit.failure(record.sessionId(),"FALLBACK",planError(error).name(),error.getMessage());
            return rejected;
        }
    }

    private static void sendOmenForAcceptedPlan(ServerPlayer player, UUID sessionId) {
        WishRecord finalRecord = WishSavedData.get(player.server).getBySession(sessionId);
        if (finalRecord == null || finalRecord.interpretation() == null || finalRecord.plan() == null
                || finalRecord.executionId() == null) {
            return;
        }
        ModNetworking.sendToPlayer(player, new WishOmenPacket(WishOmenGenerator.generate(
                sessionId, finalRecord.interpretation(), finalRecord.plan()
        )));
    }

    private static void sendPlanningFailure(ServerPlayer player, UUID sessionId) {
        player.sendSystemMessage(Component.translatable("message.wishing_willow.planning_failed"));
        if (!FMLEnvironment.production) {
            var debug = WishAgentDebugStore.latest(player.getUUID());
            if (debug != null && debug.sessionId().equals(sessionId)) {
                player.sendSystemMessage(Component.literal("Planning diagnostics: mode=" + debug.mode()
                        + " state=" + debug.state() + " fallback=" + debug.fallbackReason()
                        + " finalization=" + debug.finalizationState()));
            }
        }
    }

    private static boolean fallbackEligible(WishExecutionAcceptError error){
        return switch(error){
            case VALIDATION_FAILED,INVALID_CANDIDATE,INVALID_RESOURCE,STALE_RESOURCE,
                    INVALID_ACTION_CAPABILITY,INVALID_PARAMETER,INVALID_EVENT,
                    UNTRUSTED_REGISTRY_CANDIDATE,UNSUPPORTED_ACTION,BUDGET_EXCEEDED,RISK_TOO_HIGH,
                    THIRD_PARTY_ENTITY_DISABLED,THIRD_PARTY_ENTITY_SEVERITY,
                    BLOCK_MODIFICATION_DISABLED,EXPLOSIONS_DISABLED,DESTRUCTIVE_EXPLOSIONS_DISABLED,
                    CROSS_DIMENSION_TELEPORT_DISABLED,DESTRUCTIVE_SEVERITY_DISABLED,DEBUG_SAFE_MODE -> true;
            default -> false;
        };
    }

    private record PlanningAttempt(UUID attemptId, UUID playerId) { }
}
