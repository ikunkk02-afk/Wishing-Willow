package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.research.KnowledgeEntry;
import com.ikunkk02.wishingwillow.research.ModKnowledge;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.ikunkk02.wishingwillow.research.web.IdentityConfidenceLevel;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class ModKnowledgeDetailScreen extends Screen {
    private final Screen parent;
    private final String modId;
    private int scroll;
    private int maxScroll;

    public ModKnowledgeDetailScreen(Screen parent, String modId) {
        super(Component.translatable("screen.wishing_willow.knowledge.detail"));
        this.parent = parent;
        this.modId = modId;
    }

    @Override
    protected void init() {
        KnowledgeEntry entry = ModResearchManager.getInstance().knowledgeBase().findMod(modId);
        boolean unresolved = entry == null || entry.webResearch().identity().level() != IdentityConfidenceLevel.CONFIRMED;
        int buttonCount = unresolved ? 3 : 2;
        int totalWidth = buttonCount * 110 + (buttonCount - 1) * 6;
        int x = width / 2 - totalWidth / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.knowledge.research_mod"),
                        button -> ModResearchManager.getInstance().researchMod(modId))
                .bounds(x, height - 28, 110, 20).build());
        if (unresolved) {
            addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.knowledge.manual_url"),
                            button -> minecraft.setScreen(new ManualResearchUrlScreen(this, modId)))
                    .bounds(x + 116, height - 28, 110, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(x + (buttonCount - 1) * 116, height - 28, 110, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        KnowledgeEntry entry = ModResearchManager.getInstance().knowledgeBase().findMod(modId);
        if (entry == null) {
            graphics.drawCenteredString(font, Component.literal("Unknown mod: " + modId), width / 2, 16,
                    0xFFFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int panelWidth = Math.min(620, width - 24);
        int left = (width - panelWidth) / 2;
        int top = 42;
        int bottom = height - 36;
        graphics.fill(left - 6, 6, left + panelWidth + 6, 36, 0xB8101014);
        graphics.fill(left - 6, top, left + panelWidth + 6, bottom, 0xB8101014);
        graphics.drawCenteredString(font, Component.literal(entry.installed().displayName()), width / 2, 10,
                0xFFFFFFFF);
        Component status = ResearchUiText.category(entry.category()).append(Component.literal("  •  ")
                        .withStyle(ChatFormatting.DARK_GRAY)).append(ResearchUiText.level(entry.knowledgeLevel()))
                .append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(ResearchUiText.state(entry.state()));
        graphics.drawCenteredString(font, status, width / 2, 24, 0xFFFFFFFF);

        List<Component> logicalLines = details(entry);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : logicalLines) {
            wrapped.addAll(font.split(line, panelWidth - 20));
        }
        int contentHeight = wrapped.size() * 12 + 8;
        maxScroll = Math.max(0, contentHeight - (bottom - top - 12));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        graphics.enableScissor(left, top + 2, left + panelWidth, bottom - 2);
        int y = top + 8 - scroll;
        for (FormattedCharSequence line : wrapped) {
            graphics.drawString(font, line, left + 10, y, 0xFFE0DCD3);
            y += 12;
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            int trackTop = top + 5;
            int trackHeight = bottom - top - 10;
            int thumbHeight = Math.max(16, trackHeight * trackHeight / Math.max(trackHeight, contentHeight));
            int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(left + panelWidth - 4, trackTop, left + panelWidth - 2, trackTop + trackHeight,
                    0xFF34343A);
            graphics.fill(left + panelWidth - 4, thumbY, left + panelWidth - 2, thumbY + thumbHeight,
                    0xFF8FA8B5);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private List<Component> details(KnowledgeEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(label("mod_id", Component.literal(entry.installed().modId())));
        lines.add(label("version", Component.literal(entry.installed().version())));
        lines.add(label("sources", Component.literal(entry.sources().stream().map(Enum::name).sorted()
                .collect(Collectors.joining(", ")))));
        lines.add(label("identity", Component.literal(entry.webResearch().identity().level().name())));
        lines.add(label("identity_confidence", Component.literal(
                Math.round(entry.webResearch().identity().confidence() * 100) + "%")));
        if (!entry.webResearch().identity().selectedUrl().isBlank()) {
            lines.add(label("selected_url", Component.literal(entry.webResearch().identity().selectedUrl())));
        }
        String sourceStatus = entry.webResearch().sourceTraces().stream()
                .map(trace -> (trace.outcome() == com.ikunkk02.wishingwillow.research.web.SourceTraceOutcome.SUCCEEDED
                        ? "✓ " : "✗ ") + trace.source().name() + "=" + trace.outcome().name())
                .collect(Collectors.joining(", "));
        if (!sourceStatus.isBlank()) lines.add(label("source_status", Component.literal(sourceStatus)));
        if (!entry.errorCode().isBlank()) {
            lines.add(label("error", Component.literal(entry.errorCode()).withStyle(ChatFormatting.RED)));
        }
        ModKnowledge knowledge = entry.knowledge();
        if (knowledge != null) {
            lines.add(label("horror_score", Component.literal(Integer.toString(knowledge.horrorScore()))));
            lines.add(label("wish_relevance", Component.literal(Integer.toString(knowledge.wishRelevance()))));
            lines.add(label("confidence", Component.literal(Math.round(knowledge.researchConfidence() * 100) + "%")));
            lines.add(label("capabilities", Component.literal(knowledge.availableCapabilities().stream()
                    .map(Enum::name).sorted().collect(Collectors.joining(", ")))));
            lines.add(Component.empty());
            lines.add(Component.translatable("screen.wishing_willow.knowledge.field.summary")
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal(knowledge.summary()));
            long verified = knowledge.features().stream().mapToLong(feature ->
                    feature.verifiedRegistryResources().size()).sum();
            lines.add(label("verified_resources", Component.literal(Long.toString(verified))));
        }
        Map<RegistryEntryType, Integer> counts = ModResearchManager.getInstance().registryCounts(modId);
        String registry = counts.entrySet().stream().filter(value -> value.getValue() > 0)
                .map(value -> value.getKey() + "=" + value.getValue()).collect(Collectors.joining(", "));
        lines.add(label("registry", Component.literal(registry.isBlank() ? "-" : registry)));
        if (!FMLEnvironment.production) {
            lines.add(Component.empty());
            lines.add(Component.translatable("screen.wishing_willow.knowledge.field.identity_debug")
                    .withStyle(ChatFormatting.AQUA));
            entry.webResearch().identity().candidates().stream().limit(10).forEach(candidate -> {
                lines.add(Component.literal(candidate.result().title() + " ["
                        + Math.round(candidate.confidence() * 100) + "%] " + candidate.result().url()));
                candidate.factors().forEach(factor -> lines.add(Component.literal(String.format(java.util.Locale.ROOT,
                        "  %s %+.0f%% %s", factor.name(), factor.contribution() * 100, factor.detail()))
                        .withStyle(factor.contribution() >= 0 ? ChatFormatting.GRAY : ChatFormatting.RED)));
                if (candidate.rejected()) lines.add(Component.literal("  REJECTED: " + candidate.rejectionReason())
                        .withStyle(ChatFormatting.RED));
            });
        }
        return lines;
    }

    private static Component label(String key, Component value) {
        return Component.translatable("screen.wishing_willow.knowledge.field." + key)
                .withStyle(ChatFormatting.GRAY).append(Component.literal(": ")).append(value);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta) * 24));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
