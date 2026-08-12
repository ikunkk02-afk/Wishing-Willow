package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishInterpretationResult;
import com.ikunkk02.wishingwillow.ai.WishInterpreter;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishInterpretationPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStatePacket;
import com.ikunkk02.wishingwillow.wish.WishState;
import com.ikunkk02.wishingwillow.client.hints.ClientWishProcessingHints;
import com.ikunkk02.wishingwillow.client.hints.WishProcessingPhase;
import com.ikunkk02.wishingwillow.config.WishingWillowClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientAiWishCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WishInterpreter INTERPRETER = new WishInterpreter(AiService.getInstance());
    private static final Map<UUID, PendingWish> PENDING = new HashMap<>();
    private static final ConcurrentLinkedQueue<CompletedWish> COMPLETED = new ConcurrentLinkedQueue<>();
    private static long connectionGeneration;

    private ClientAiWishCoordinator() {
    }

    public static void register(WishStartedPacket packet) {
        AiConfig config = AiConfigManager.getInstance().get();
        boolean matches = config.isConfigured()
                && config.providerType() == packet.providerType()
                && config.model().equals(packet.model());
        PENDING.put(packet.sessionId(), new PendingWish(
                packet.originalWish(), matches ? config : null, connectionGeneration
        ));
    }

    public static void updateState(WishStatePacket packet) {
        if (packet.state() == WishState.SNAPPED) {
            begin(packet.correlationId());
        } else if (packet.state() == WishState.CANCELLED
                || packet.state() == WishState.FINISHED) {
            PENDING.remove(packet.correlationId());
            ClientWishProcessingHints.stop();
        }
    }

    private static void begin(UUID sessionId) {
        PendingWish pending = PENDING.get(sessionId);
        if (pending == null || pending.started) {
            return;
        }
        pending.started = true;
        ClientWishProcessingHints.setPhase(WishProcessingPhase.INTERPRETING);
        if (pending.config == null) {
            COMPLETED.add(new CompletedWish(
                    sessionId,
                    WishInterpretationResult.requestFailure(AiErrorCategory.NOT_CONFIGURED, 0),
                    pending.generation
            ));
            return;
        }
        LOGGER.info(
                "AI request started provider={} model={} wishSession={} wishLength={}",
                pending.config.providerType(), safeModel(pending.config.model()), sessionId, pending.wish.length()
        );
        INTERPRETER.interpret(pending.config, pending.wish, WishingWillowClientConfig.FULFILLMENT_MODE.get()).whenComplete((result, throwable) -> {
            WishInterpretationResult completed = result;
            if (throwable != null || completed == null) {
                completed = WishInterpretationResult.requestFailure(AiErrorCategory.UNKNOWN, 0);
            }
            COMPLETED.add(new CompletedWish(sessionId, completed, pending.generation));
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        CompletedWish completed;
        while ((completed = COMPLETED.poll()) != null) {
            if (completed.generation != connectionGeneration || !PENDING.containsKey(completed.sessionId)) {
                continue;
            }
            LOGGER.info(
                    "AI response received wishSession={} state={} validation={}",
                    completed.sessionId,
                    completed.result.state(),
                    completed.result.state() == InterpretationState.SUCCESS ? "success" : "failure"
            );
            ModNetworking.sendToServer(new SubmitWishInterpretationPacket(
                    completed.sessionId,
                    completed.result.state(),
                    completed.result.errorCategory(),
                    completed.result.interpretation(),
                    completed.result.program()
            ));
            PENDING.remove(completed.sessionId);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        connectionGeneration++;
        PENDING.clear();
        COMPLETED.clear();
    }

    private static String safeModel(String model) {
        return model.chars()
                .map(character -> Character.isISOControl(character) ? '?' : character)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static final class PendingWish {
        private final String wish;
        private final AiConfig config;
        private final long generation;
        private boolean started;

        private PendingWish(String wish, AiConfig config, long generation) {
            this.wish = wish;
            this.config = config;
            this.generation = generation;
        }
    }

    private record CompletedWish(UUID sessionId, WishInterpretationResult result, long generation) {
    }
}
