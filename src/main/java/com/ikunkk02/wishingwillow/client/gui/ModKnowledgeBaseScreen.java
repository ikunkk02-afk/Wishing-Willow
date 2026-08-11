package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.research.KnowledgeBaseSnapshot;
import com.ikunkk02.wishingwillow.research.KnowledgeEntry;
import com.ikunkk02.wishingwillow.research.ModCategory;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import com.ikunkk02.wishingwillow.research.ResearchState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class ModKnowledgeBaseScreen extends Screen {
    private static final int ROW_HEIGHT = 25;
    private final Screen parent;
    private String searchText = "";
    private int categoryIndex = -1;
    private int statusIndex;
    private int scrollRow;
    private int ticks;
    private int listX;
    private int listWidth;
    private int listTop;
    private int listBottom;
    private int visibleRows;
    private boolean draggingScrollbar;
    private String snapshotSignature = "";
    private List<KnowledgeEntry> filtered = List.of();
    private EditBox searchBox;

    public ModKnowledgeBaseScreen(Screen parent) {
        super(Component.translatable("screen.wishing_willow.knowledge.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        KnowledgeBaseSnapshot snapshot = ModResearchManager.getInstance().knowledgeBase().snapshot();
        boolean compact = height < 170;
        listWidth = Math.min(700, Math.max(190, width - 12));
        listX = (width - listWidth) / 2;
        int filterY = compact ? 18 : 51;
        listTop = compact ? 43 : 76;
        listBottom = height - 29;
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        snapshotSignature = signature(snapshot);
        filtered = filter(snapshot.entries());
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, filtered.size() - visibleRows)));

        int gap = 4;
        int categoryWidth = Math.min(120, Math.max(48, listWidth / 5));
        int statusWidth = categoryWidth;
        int searchWidth = listWidth - categoryWidth - statusWidth - gap * 2;
        searchBox = new EditBox(font, listX, filterY, searchWidth, 20,
                Component.translatable("screen.wishing_willow.knowledge.search"));
        searchBox.setHint(Component.translatable("screen.wishing_willow.knowledge.search"));
        searchBox.setMaxLength(80);
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            if (!value.equals(searchText)) {
                searchText = value;
                scrollRow = 0;
                rebuildWidgets();
            }
        });
        addRenderableWidget(searchBox);
        addRenderableWidget(RetroButton.create(categoryLabel(), button -> {
            categoryIndex++;
            if (categoryIndex >= ModCategory.values().length) categoryIndex = -1;
            scrollRow = 0;
            rebuildWidgets();
        }, listX + searchWidth + gap, filterY, categoryWidth, 20));
        addRenderableWidget(RetroButton.create(statusLabel(), button -> {
            statusIndex = (statusIndex + 1) % 5;
            scrollRow = 0;
            rebuildWidgets();
        }, listX + searchWidth + gap + categoryWidth + gap, filterY, statusWidth, 20));

        int end = Math.min(filtered.size(), scrollRow + visibleRows);
        for (int index = scrollRow; index < end; index++) {
            KnowledgeEntry entry = filtered.get(index);
            int rowY = listTop + (index - scrollRow) * ROW_HEIGHT;
            addRenderableWidget(RetroButton.create(entryLabel(entry), button -> minecraft.setScreen(
                            new ModKnowledgeDetailScreen(this, entry.installed().modId())),
                    listX, rowY, listWidth - 8, 22));
        }

        int footerY = height - 24;
        int footerGap = 3;
        int footerWidth = (listWidth - footerGap * 5) / 6;
        boolean narrow = width < 420;
        addFooter(snapshot, 0, narrow ? "↻" : "screen.wishing_willow.knowledge.rescan",
                button -> ModResearchManager.getInstance().rescan(), footerY, footerWidth, footerGap, narrow);
        addFooter(snapshot, 1, narrow ? "!" : "screen.wishing_willow.knowledge.retry",
                button -> ModResearchManager.getInstance().retryFailed(), footerY, footerWidth, footerGap, narrow);
        addFooter(snapshot, 2, narrow ? (snapshot.paused() ? "▶" : "Ⅱ")
                        : (snapshot.paused() ? "screen.wishing_willow.knowledge.resume"
                        : "screen.wishing_willow.knowledge.pause"),
                button -> {
                    if (snapshot.paused()) ModResearchManager.getInstance().resume();
                    else ModResearchManager.getInstance().pause();
                    rebuildWidgets();
                }, footerY, footerWidth, footerGap, narrow);
        addFooter(snapshot, 3, narrow ? "×" : "screen.wishing_willow.knowledge.clear",
                button -> minecraft.setScreen(new ConfirmKnowledgeCacheClearScreen(this)),
                footerY, footerWidth, footerGap, narrow);
        addFooter(snapshot, 4, narrow ? "⚙" : "screen.wishing_willow.settings.research",
                button -> minecraft.setScreen(new ResearchSettingsScreen(this)),
                footerY, footerWidth, footerGap, narrow);
        addFooter(snapshot, 5, narrow ? "✓" : "gui.done", button -> onClose(),
                footerY, footerWidth, footerGap, narrow);
    }

    private void addFooter(KnowledgeBaseSnapshot snapshot, int index, String text,
                           net.minecraft.client.gui.components.Button.OnPress action,
                           int y, int buttonWidth, int gap, boolean literal) {
        Component label = literal ? Component.literal(text) : Component.translatable(text);
        addRenderableWidget(RetroButton.create(label, action,
                listX + index * (buttonWidth + gap), y, buttonWidth, 20));
    }

    private List<KnowledgeEntry> filter(List<KnowledgeEntry> entries) {
        String needle = searchText.strip().toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> needle.isEmpty()
                        || entry.installed().displayName().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.installed().modId().toLowerCase(Locale.ROOT).contains(needle))
                .filter(entry -> categoryIndex < 0 || entry.category() == ModCategory.values()[categoryIndex])
                .filter(entry -> matchesStatus(entry.state())).toList();
    }

    private boolean matchesStatus(ResearchState state) {
        return switch (statusIndex) {
            case 1 -> state == ResearchState.READY;
            case 2 -> switch (state) {
                case SCANNING, IDENTIFYING, FETCHING, WAITING_FOR_AI, ANALYZING, VERIFYING -> true;
                default -> false;
            };
            case 3 -> state == ResearchState.FAILED || state == ResearchState.PARTIAL;
            case 4 -> state == ResearchState.IGNORED;
            default -> true;
        };
    }

    private Component categoryLabel() {
        return categoryIndex < 0 ? Component.translatable("screen.wishing_willow.knowledge.filter.category")
                : ResearchUiText.category(ModCategory.values()[categoryIndex]);
    }

    private Component statusLabel() {
        String key = switch (statusIndex) {
            case 1 -> "ready";
            case 2 -> "active";
            case 3 -> "attention";
            case 4 -> "ignored";
            default -> "all";
        };
        return Component.translatable("screen.wishing_willow.knowledge.filter.status." + key);
    }

    private Component entryLabel(KnowledgeEntry entry) {
        int reserved = font.width(ResearchUiText.category(entry.category()))
                + font.width(ResearchUiText.level(entry.knowledgeLevel()))
                + font.width(ResearchUiText.state(entry.state())) + 48;
        String name = font.plainSubstrByWidth(entry.installed().displayName(),
                Math.max(30, listWidth - reserved));
        return Component.literal(name + "  ·  ").append(ResearchUiText.category(entry.category()))
                .append("  ·  ").append(ResearchUiText.level(entry.knowledgeLevel()))
                .append("  ·  ").append(ResearchUiText.state(entry.state()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maximum = Math.max(0, filtered.size() - visibleRows);
        if (maximum > 0) {
            scrollRow = Math.max(0, Math.min(maximum, scrollRow - (int) Math.signum(delta)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listX + listWidth - 7 && mouseX <= listX + listWidth
                && mouseY >= listTop && mouseY <= listBottom) {
            draggingScrollbar = true;
            scrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void scrollFromMouse(double mouseY) {
        int maximum = Math.max(0, filtered.size() - visibleRows);
        if (maximum == 0) return;
        double fraction = (mouseY - listTop) / Math.max(1.0, listBottom - listTop);
        int next = Math.max(0, Math.min(maximum, (int) Math.round(fraction * maximum)));
        if (next != scrollRow) {
            scrollRow = next;
            rebuildWidgets();
            draggingScrollbar = true;
        }
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) {
            KnowledgeBaseSnapshot snapshot = ModResearchManager.getInstance().knowledgeBase().snapshot();
            if (!signature(snapshot).equals(snapshotSignature)) rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        RetroUiTheme.drawPaperPanel(graphics, listX - 4, 3, listWidth + 8, height - 7);
        KnowledgeBaseSnapshot snapshot = ModResearchManager.getInstance().knowledgeBase().snapshot();
        graphics.drawCenteredString(font, title, width / 2, 7, RetroUiTheme.OXBLOOD_DARK);
        if (height >= 170) {
            graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.knowledge.summary.primary",
                    snapshot.entries().size(), needsResearch(snapshot), snapshot.count(ResearchState.READY)),
                    width / 2, 22, RetroUiTheme.INK);
            graphics.drawCenteredString(font, ResearchUiText.baseState(snapshot.state()), width / 2, 34,
                    snapshot.paused() ? RetroUiTheme.STATUS_WARN : RetroUiTheme.STATUS_OK);
        }
        graphics.fill(listX, listTop - 2, listX + listWidth, listBottom, 0x35FFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderScrollbar(graphics);
        if (filtered.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.wishing_willow.knowledge.empty"),
                    width / 2, listTop + 8, RetroUiTheme.MUTED_INK);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int trackX = listX + listWidth - 5;
        graphics.fill(trackX, listTop, trackX + 3, listBottom, 0x55482F26);
        if (filtered.size() <= visibleRows) {
            graphics.fill(trackX, listTop, trackX + 3, listBottom, RetroUiTheme.OXBLOOD);
            return;
        }
        int trackHeight = Math.max(1, listBottom - listTop);
        int thumbHeight = Math.max(10, trackHeight * visibleRows / filtered.size());
        int maximum = filtered.size() - visibleRows;
        int thumbY = listTop + (trackHeight - thumbHeight) * scrollRow / maximum;
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, RetroUiTheme.OXBLOOD);
    }

    private static long needsResearch(KnowledgeBaseSnapshot snapshot) {
        return snapshot.entries().stream().filter(entry -> entry.state() != ResearchState.IGNORED).count();
    }

    private static String signature(KnowledgeBaseSnapshot snapshot) {
        StringBuilder value = new StringBuilder(snapshot.state().name()).append('|').append(snapshot.paused());
        for (KnowledgeEntry entry : snapshot.entries()) value.append('|').append(entry.installed().modId())
                .append(':').append(entry.category()).append(':').append(entry.knowledgeLevel())
                .append(':').append(entry.state()).append(':').append(entry.updatedAt());
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
