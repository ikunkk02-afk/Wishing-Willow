package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.research.KnowledgeBaseSnapshot;
import com.ikunkk02.wishingwillow.research.KnowledgeEntry;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import com.ikunkk02.wishingwillow.research.ResearchState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class ModKnowledgeBaseScreen extends Screen {
    private static final int LIST_TOP = 62;
    private static final int ROW_HEIGHT = 26;
    private final Screen parent;
    private int page;
    private int ticks;
    private int rowsPerPage = 1;
    private int pages = 1;
    private int listX;
    private int listWidth;
    private int actionY;
    private int navY;
    private String snapshotSignature = "";

    public ModKnowledgeBaseScreen(Screen parent) {
        super(Component.translatable("screen.wishing_willow.knowledge.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        KnowledgeBaseSnapshot snapshot = ModResearchManager.getInstance().knowledgeBase().snapshot();
        listWidth = Math.min(620, Math.max(280, width - 24));
        listX = (width - listWidth) / 2;
        actionY = height - 50;
        navY = height - 26;
        rowsPerPage = Math.max(1, Math.min(8, (actionY - 8 - LIST_TOP) / ROW_HEIGHT));
        pages = Math.max(1, (snapshot.entries().size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        snapshotSignature = signature(snapshot);

        int start = page * rowsPerPage;
        int end = Math.min(snapshot.entries().size(), start + rowsPerPage);
        for (int index = start; index < end; index++) {
            KnowledgeEntry entry = snapshot.entries().get(index);
            int rowY = LIST_TOP + (index - start) * ROW_HEIGHT;
            addRenderableWidget(Button.builder(entryLabel(entry),
                            button -> minecraft.setScreen(new ModKnowledgeDetailScreen(this,
                                    entry.installed().modId())))
                    .bounds(listX, rowY, listWidth, 22).build());
        }

        int gap = 4;
        int actionWidth = (listWidth - gap * 3) / 4;
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.knowledge.rescan"),
                        button -> ModResearchManager.getInstance().rescan())
                .bounds(listX, actionY, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.knowledge.retry"),
                        button -> ModResearchManager.getInstance().retryFailed())
                .bounds(listX + actionWidth + gap, actionY, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(snapshot.paused()
                                ? "screen.wishing_willow.knowledge.resume" : "screen.wishing_willow.knowledge.pause"),
                        button -> {
                            if (snapshot.paused()) ModResearchManager.getInstance().resume();
                            else ModResearchManager.getInstance().pause();
                            rebuildWidgets();
                        })
                .bounds(listX + (actionWidth + gap) * 2, actionY, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.knowledge.clear"),
                        button -> minecraft.setScreen(new ConfirmKnowledgeCacheClearScreen(this)))
                .bounds(listX + (actionWidth + gap) * 3, actionY, actionWidth, 20).build());

        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> changePage(-1))
                .bounds(listX, navY, 34, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), button -> changePage(1))
                .bounds(listX + 38, navY, 34, 20).build());
        next.active = page + 1 < pages;
        int rightButtonWidth = Math.min(112, (listWidth - 84) / 2);
        addRenderableWidget(Button.builder(Component.translatable("screen.wishing_willow.settings.research"),
                        button -> minecraft.setScreen(new ResearchSettingsScreen(this)))
                .bounds(listX + listWidth - rightButtonWidth * 2 - gap, navY, rightButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(listX + listWidth - rightButtonWidth, navY, rightButtonWidth, 20).build());
    }

    private Component entryLabel(KnowledgeEntry entry) {
        Component category = ResearchUiText.category(entry.category());
        Component level = ResearchUiText.level(entry.knowledgeLevel());
        Component state = ResearchUiText.state(entry.state());
        int reserved = font.width(category) + font.width(level) + font.width(state) + 46;
        String name = font.plainSubstrByWidth(entry.installed().displayName(),
                Math.max(48, listWidth - reserved));
        MutableComponent result = Component.literal(name).withStyle(ChatFormatting.WHITE);
        return result.append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(category).append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(level).append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(state);
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(page + delta, pages - 1));
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) {
            KnowledgeBaseSnapshot snapshot = ModResearchManager.getInstance().knowledgeBase().snapshot();
            String current = signature(snapshot);
            if (!current.equals(snapshotSignature)) {
                rebuildWidgets();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        KnowledgeBaseSnapshot snapshot = ModResearchManager.getInstance().knowledgeBase().snapshot();
        graphics.fill(listX - 6, 4, listX + listWidth + 6, 58, 0xB8101014);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.knowledge.summary.primary",
                snapshot.entries().size(), needsResearch(snapshot), snapshot.count(ResearchState.READY)),
                width / 2, 26, 0xFFE5E1D8);
        graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.knowledge.summary.secondary",
                active(snapshot), snapshot.count(ResearchState.IGNORED), failures(snapshot)),
                width / 2, 38, 0xFFB8B4AB);
        graphics.drawCenteredString(font, ResearchUiText.baseState(snapshot.state()), width / 2, 50, 0xFFFFFFFF);

        int visibleRows = Math.min(rowsPerPage,
                Math.max(0, snapshot.entries().size() - page * rowsPerPage));
        if (visibleRows > 0) {
            graphics.fill(listX - 4, LIST_TOP - 4, listX + listWidth + 4,
                    LIST_TOP + visibleRows * ROW_HEIGHT, 0x70101014);
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        int start = page * rowsPerPage;
        int end = Math.min(snapshot.entries().size(), start + rowsPerPage);
        for (int index = start; index < end; index++) {
            KnowledgeEntry entry = snapshot.entries().get(index);
            int rowY = LIST_TOP + (index - start) * ROW_HEIGHT;
            int completed = progress(entry.state());
            graphics.fill(listX + 2, rowY + 22, listX + listWidth - 2, rowY + 24, 0xFF242429);
            graphics.fill(listX + 2, rowY + 22,
                    listX + 2 + (listWidth - 4) * completed / 100, rowY + 24,
                    ResearchUiText.progressColor(entry.state()));
        }
        graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.knowledge.page",
                page + 1, pages), listX + 116, navY + 6, 0xFFB8B4AB);
    }

    private static long needsResearch(KnowledgeBaseSnapshot snapshot) {
        return snapshot.entries().stream().filter(entry -> entry.state() != ResearchState.IGNORED).count();
    }

    private static long active(KnowledgeBaseSnapshot snapshot) {
        return snapshot.entries().stream().filter(entry -> switch (entry.state()) {
            case SCANNING, IDENTIFYING, FETCHING, ANALYZING, VERIFYING -> true;
            default -> false;
        }).count();
    }

    private static long failures(KnowledgeBaseSnapshot snapshot) {
        return snapshot.count(ResearchState.FAILED) + snapshot.count(ResearchState.PARTIAL);
    }

    private static int progress(ResearchState state) {
        return switch (state) {
            case NOT_STARTED -> 0;
            case SCANNING -> 10;
            case IDENTIFYING -> 30;
            case FETCHING -> 50;
            case WAITING_FOR_AI -> 60;
            case ANALYZING -> 75;
            case VERIFYING -> 90;
            case READY, IGNORED, PARTIAL, FAILED -> 100;
        };
    }

    private static String signature(KnowledgeBaseSnapshot snapshot) {
        StringBuilder value = new StringBuilder(snapshot.state().name()).append('|').append(snapshot.paused());
        for (KnowledgeEntry entry : snapshot.entries()) {
            value.append('|').append(entry.installed().modId()).append(':').append(entry.category())
                    .append(':').append(entry.knowledgeLevel()).append(':').append(entry.state())
                    .append(':').append(entry.updatedAt());
        }
        return value.toString();
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
