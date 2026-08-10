package com.ikunkk02.wishingwillow.client;

import com.ikunkk02.wishingwillow.client.gui.ModKnowledgeBaseScreen;
import com.ikunkk02.wishingwillow.client.gui.AiSettingsScreen;
import com.ikunkk02.wishingwillow.client.gui.WishingWillowSettingsScreen;
import com.ikunkk02.wishingwillow.research.KnowledgeBaseSnapshot;
import com.ikunkk02.wishingwillow.research.KnowledgeEntry;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import com.ikunkk02.wishingwillow.research.ResearchState;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.concurrent.CompletableFuture;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new WishingWillowSettingsScreen(parent)
                )
        );
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::registerClientCommands);
        ModResearchManager.getInstance().start();
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("wishingwillow")
                        .then(Commands.literal("ai").executes(context -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            minecraft.execute(() -> minecraft.setScreen(new AiSettingsScreen(minecraft.screen)));
                            return 1;
                        }))
                        .then(Commands.literal("research")
                                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                                .then(Commands.literal("rescan").executes(context -> {
                                    ModResearchManager.getInstance().rescan();
                                    context.getSource().sendSuccess(() -> Component.literal("Wishing Willow research rescan queued."), false);
                                    return 1;
                                }))
                                .then(Commands.literal("retry").executes(context -> {
                                    ModResearchManager.getInstance().retryFailed();
                                    context.getSource().sendSuccess(() -> Component.literal("Failed and partial research queued."), false);
                                    return 1;
                                }))
                                .then(Commands.literal("mod")
                                        .then(Commands.argument("modid", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    ModResearchManager.getInstance().knowledgeBase().snapshot().entries()
                                                            .forEach(entry -> builder.suggest(entry.installed().modId()));
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> researchMod(context.getSource(),
                                                        StringArgumentType.getString(context, "modid")))))
                                .then(Commands.literal("web")
                                        .then(Commands.argument("modid", StringArgumentType.word())
                                                .suggests(ClientSetup::suggestMods)
                                                .executes(context -> researchWeb(context.getSource(),
                                                        StringArgumentType.getString(context, "modid")))))
                                .then(Commands.literal("identity")
                                        .then(Commands.argument("modid", StringArgumentType.word())
                                                .suggests(ClientSetup::suggestMods)
                                                .executes(context -> identity(context.getSource(),
                                                        StringArgumentType.getString(context, "modid"))))))
                        .then(Commands.literal("knowledge")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            ModResearchManager.getInstance().knowledgeBase().snapshot().entries()
                                                    .forEach(entry -> builder.suggest(entry.installed().modId()));
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> knowledge(context.getSource(),
                                                StringArgumentType.getString(context, "modid")))))
                        .then(Commands.literal("settings").executes(context -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            minecraft.execute(() -> minecraft.setScreen(new WishingWillowSettingsScreen(minecraft.screen)));
                            return 1;
                        }))
        );
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        KnowledgeBaseSnapshot snapshot = ModResearchManager.getInstance().knowledgeBase().snapshot();
        long active = snapshot.entries().stream().filter(entry -> switch (entry.state()) {
            case SCANNING, IDENTIFYING, FETCHING, ANALYZING, VERIFYING -> true;
            default -> false;
        }).count();
        long failed = snapshot.count(ResearchState.FAILED) + snapshot.count(ResearchState.PARTIAL);
        source.sendSuccess(() -> Component.literal("Research " + snapshot.state()
                + ": scanned=" + snapshot.entries().size()
                + " ready=" + snapshot.count(ResearchState.READY)
                + " active=" + active
                + " ignored=" + snapshot.count(ResearchState.IGNORED)
                + " failed=" + failed), false);
        return 1;
    }

    private static int researchMod(net.minecraft.commands.CommandSourceStack source, String modId) {
        boolean queued = ModResearchManager.getInstance().researchMod(modId);
        source.sendSuccess(() -> Component.literal(queued
                ? "Research queued for " + modId : "Unknown installed mod: " + modId), false);
        return queued ? 1 : 0;
    }

    private static int knowledge(net.minecraft.commands.CommandSourceStack source, String modId) {
        KnowledgeEntry entry = ModResearchManager.getInstance().knowledgeBase().findMod(modId);
        if (entry == null) {
            source.sendFailure(Component.literal("Unknown installed mod: " + modId));
            return 0;
        }
        String summary = entry.knowledge() == null ? "No semantic summary yet" : entry.knowledge().summary();
        String capabilities = entry.knowledge() == null ? ""
                : entry.knowledge().availableCapabilities().stream().map(Enum::name).sorted().limit(8)
                .collect(java.util.stream.Collectors.joining(","));
        source.sendSuccess(() -> Component.literal(entry.installed().displayName() + " "
                + entry.installed().version() + " category=" + entry.category()
                + " state=" + entry.state() + " level=" + entry.knowledgeLevel()
                + " capabilities=[" + capabilities + "] summary=" + summary), false);
        return 1;
    }

    private static int researchWeb(net.minecraft.commands.CommandSourceStack source, String modId) {
        boolean queued = ModResearchManager.getInstance().researchWeb(modId);
        if (queued) source.sendSuccess(() -> Component.literal("Web discovery queued for " + modId), false);
        else source.sendFailure(Component.literal("Unknown installed mod: " + modId));
        return queued ? 1 : 0;
    }

    private static int identity(net.minecraft.commands.CommandSourceStack source, String modId) {
        KnowledgeEntry entry = ModResearchManager.getInstance().knowledgeBase().findMod(modId);
        if (entry == null) { source.sendFailure(Component.literal("Unknown installed mod: " + modId)); return 0; }
        var identity = entry.webResearch().identity();
        source.sendSuccess(() -> Component.literal("Identity " + identity.level() + " confidence="
                + Math.round(identity.confidence() * 100) + "% selected="
                + (identity.selectedUrl().isBlank() ? "-" : identity.selectedUrl()) + " reason=" + identity.reason()), false);
        identity.candidates().stream().limit(10).forEach(candidate -> {
            String factors = candidate.factors().stream().map(factor -> String.format(java.util.Locale.ROOT,
                    "%s%+.0f", factor.name().replace(' ', '_'), factor.contribution() * 100))
                    .collect(java.util.stream.Collectors.joining(","));
            source.sendSuccess(() -> Component.literal(Math.round(candidate.confidence() * 100) + "% "
                    + candidate.result().title() + " " + candidate.result().url()
                    + " factors=[" + factors + "]"
                    + (candidate.rejected() ? " rejected=" + candidate.rejectionReason() : "")), false);
        });
        return 1;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMods(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        ModResearchManager.getInstance().knowledgeBase().snapshot().entries()
                .forEach(entry -> builder.suggest(entry.installed().modId()));
        return builder.buildFuture();
    }
}
