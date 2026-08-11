package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.ai.AiConnectionResult;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiModelListResult;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;

public final class AiSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 246;

    @Nullable
    private final Screen parent;
    private AiConfig draftConfig;
    @Nullable
    private AiConfig testedConfig;
    private Component status = Component.translatable("screen.wishing_willow.ai.status.not_tested");
    private CycleButton<AiProviderType> providerButton;
    private EditBox baseUrlInput;
    private PasswordEditBox apiKeyInput;
    private EditBox modelInput;
    private Button showKeyButton;
    private Button fetchModelsButton;
    private Button testConnectionButton;
    private Button testInterpretationButton;
    private Button saveButton;
    private boolean busy;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int viewportTop;
    private int viewportBottom;
    private int contentOrigin;
    private int statusY;
    private int scrollOffset;

    public AiSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.wishing_willow.ai.title"));
        this.parent = parent;
        this.draftConfig = AiConfigManager.getInstance().get();
    }

    @Override
    protected void init() {
        captureDraft();
        panelWidth = Math.min(PANEL_WIDTH, Math.max(190, width - 12));
        panelLeft = (width - panelWidth) / 2;
        panelTop = 3;
        panelHeight = height - 7;
        viewportTop = 29;
        viewportBottom = height - 32;
        int fieldX = panelLeft + 12;
        int fieldWidth = panelWidth - 24;
        boolean narrow = fieldWidth < 300;
        int totalContent = narrow ? 252 : 205;
        scrollOffset = Math.max(0, Math.min(scrollOffset,
                Math.max(0, totalContent - Math.max(1, viewportBottom - viewportTop))));
        contentOrigin = viewportTop + 2 - scrollOffset;

        providerButton = addRenderableWidget(CycleButton.<AiProviderType>builder(
                        value -> Component.translatable(providerKey(value))
                )
                .withValues(AiProviderType.values())
                .withInitialValue(draftConfig.providerType())
                .create(
                        fieldX, contentOrigin, fieldWidth, 20,
                        Component.translatable("screen.wishing_willow.ai.provider"),
                        (button, value) -> selectProvider(value)
                ));

        baseUrlInput = new EditBox(
                font, fieldX, contentOrigin + 38, fieldWidth, 20,
                Component.translatable("screen.wishing_willow.ai.base_url")
        );
        baseUrlInput.setMaxLength(AiConfig.MAX_BASE_URL_LENGTH);
        baseUrlInput.setValue(draftConfig.baseUrl());
        baseUrlInput.setResponder(value -> invalidateTest());
        addRenderableWidget(baseUrlInput);

        apiKeyInput = new PasswordEditBox(
                font, fieldX, contentOrigin + 75, fieldWidth - 64, 20,
                Component.translatable("screen.wishing_willow.ai.api_key")
        );
        apiKeyInput.setMaxLength(AiConfig.MAX_API_KEY_LENGTH);
        apiKeyInput.setValue(draftConfig.apiKey());
        apiKeyInput.setResponder(value -> invalidateTest());
        addRenderableWidget(apiKeyInput);
        showKeyButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.ai.show"), button -> toggleKeyVisibility(),
                fieldX + fieldWidth - 60, contentOrigin + 75, 60, 20));

        modelInput = new EditBox(
                font, fieldX, contentOrigin + 112, fieldWidth, 20,
                Component.translatable("screen.wishing_willow.ai.model")
        );
        modelInput.setMaxLength(AiConfig.MAX_MODEL_LENGTH);
        modelInput.setValue(draftConfig.model());
        modelInput.setResponder(value -> invalidateTest());
        addRenderableWidget(modelInput);

        int actionsY = contentOrigin + 142;
        int actionWidth = narrow ? fieldWidth : (fieldWidth - 12) / 3;
        fetchModelsButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.ai.fetch_models"), button -> fetchModels(),
                fieldX, actionsY, actionWidth, 20));
        testConnectionButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.ai.test_connection"), button -> testConnection(),
                fieldX + (narrow ? 0 : actionWidth + 6), actionsY + (narrow ? 24 : 0), actionWidth, 20));
        testInterpretationButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.ai.test_interpretation"),
                button -> openTestInterpretation(), fieldX + (narrow ? 0 : (actionWidth + 6) * 2),
                actionsY + (narrow ? 48 : 0), actionWidth, 20));
        statusY = actionsY + (narrow ? 76 : 29);

        int bottomY = height - 27;
        int footerWidth = Math.min(100, (panelWidth - 28) / 2);
        saveButton = addRenderableWidget(RetroButton.create(
                Component.translatable("screen.wishing_willow.ai.save"), button -> save(),
                width / 2 - footerWidth - 3, bottomY, footerWidth, 20));
        addRenderableWidget(RetroButton.create(Component.translatable("screen.wishing_willow.cancel"),
                button -> onClose(), width / 2 + 3, bottomY, footerWidth, 20));
        setContentVisibility(providerButton, baseUrlInput, apiKeyInput, showKeyButton, modelInput,
                fetchModelsButton, testConnectionButton, testInterpretationButton);
        updateButtonStates();
    }

    private void setContentVisibility(AbstractWidget... widgets) {
        for (AbstractWidget widget : widgets) {
            widget.visible = widget.getY() >= viewportTop && widget.getY() + widget.getHeight() <= viewportBottom;
        }
    }

    private void selectProvider(AiProviderType providerType) {
        AiConfig preset = AiConfig.forProvider(providerType);
        baseUrlInput.setValue(preset.baseUrl());
        apiKeyInput.setValue("");
        modelInput.setValue(preset.model());
        invalidateTest();
    }

    private void toggleKeyVisibility() {
        apiKeyInput.setPasswordVisible(!apiKeyInput.isPasswordVisible());
        showKeyButton.setMessage(Component.translatable(
                apiKeyInput.isPasswordVisible()
                        ? "screen.wishing_willow.ai.hide"
                        : "screen.wishing_willow.ai.show"
        ));
    }

    private void fetchModels() {
        if (busy) {
            return;
        }
        AiConfig config = currentDraft();
        busy = true;
        status = Component.translatable("screen.wishing_willow.ai.status.fetching_models");
        updateButtonStates();
        AiService.getInstance().listModels(config).whenComplete((result, throwable) -> {
            if (minecraft == null) {
                return;
            }
            minecraft.execute(() -> finishModelFetch(result, throwable));
        });
    }

    private void finishModelFetch(@Nullable AiModelListResult result, @Nullable Throwable throwable) {
        if (minecraft == null || minecraft.screen != this) {
            return;
        }
        busy = false;
        updateButtonStates();
        if (throwable != null || result == null) {
            status = errorStatus(AiErrorCategory.UNKNOWN);
        } else if (!result.supported()) {
            status = Component.translatable("screen.wishing_willow.ai.status.models_unsupported");
        } else if (result.errorCategory() != AiErrorCategory.NONE) {
            status = errorStatus(result.errorCategory());
        } else if (result.models().isEmpty()) {
            status = Component.translatable("screen.wishing_willow.ai.status.no_models");
        } else {
            captureDraft();
            minecraft.setScreen(new AiModelSelectionScreen(this, result.models(), model -> modelInput.setValue(model)));
        }
    }

    private void testConnection() {
        if (busy) {
            return;
        }
        AiConfig config = currentDraft();
        if (!config.isConfigured()) {
            status = errorStatus(AiErrorCategory.NOT_CONFIGURED);
            return;
        }
        busy = true;
        status = Component.translatable("screen.wishing_willow.ai.status.testing");
        updateButtonStates();
        AiService.getInstance().testConnection(config).whenComplete((result, throwable) -> {
            if (minecraft == null) {
                return;
            }
            minecraft.execute(() -> finishConnectionTest(config, result, throwable));
        });
    }

    private void finishConnectionTest(
            AiConfig tested,
            @Nullable AiConnectionResult result,
            @Nullable Throwable throwable
    ) {
        if (minecraft == null || minecraft.screen != this) {
            return;
        }
        busy = false;
        if (throwable == null && result != null && result.success() && sameConnection(tested, currentDraft())) {
            testedConfig = tested;
            status = Component.translatable(result.toolCallingSupport() == com.ikunkk02.wishingwillow.ai.ToolCallingSupport.SUPPORTED
                    ? "screen.wishing_willow.ai.status.success_tools"
                    : "screen.wishing_willow.ai.status.success_json");
        } else {
            testedConfig = null;
            status = errorStatus(result == null ? AiErrorCategory.UNKNOWN : result.errorCategory());
        }
        updateButtonStates();
    }

    private void openTestInterpretation() {
        AiConfig current = currentDraft();
        if (testedConfig != null && sameConnection(testedConfig, current) && minecraft != null) {
            captureDraft();
            minecraft.setScreen(new AiTestWishScreen(this, current));
        }
    }

    private void save() {
        AiConfig config = currentDraft();
        if (!config.isConfigured()) {
            status = errorStatus(AiErrorCategory.NOT_CONFIGURED);
            return;
        }
        if (AiConfigManager.getInstance().save(config)) {
            ModResearchManager.getInstance().resumeWaitingForAi();
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        } else {
            status = Component.translatable("screen.wishing_willow.ai.status.save_failed");
        }
    }

    private void invalidateTest() {
        testedConfig = null;
        if (!busy) {
            status = Component.translatable("screen.wishing_willow.ai.status.not_tested");
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (fetchModelsButton == null) {
            return;
        }
        AiConfig current = currentDraft();
        fetchModelsButton.active = !busy && !current.baseUrl().isBlank();
        testConnectionButton.active = !busy && current.isConfigured();
        testInterpretationButton.active = !busy
                && testedConfig != null
                && sameConnection(testedConfig, current);
        saveButton.active = !busy && current.isConfigured();
    }

    private AiConfig currentDraft() {
        if (providerButton == null) {
            return draftConfig;
        }
        return new AiConfig(
                draftConfig.executionMode(),
                providerButton.getValue(),
                baseUrlInput.getValue(),
                apiKeyInput.getValue(),
                modelInput.getValue()
        );
    }

    private void captureDraft() {
        if (providerButton != null) {
            draftConfig = currentDraft();
        }
    }

    private static boolean sameConnection(AiConfig left, AiConfig right) {
        return left.executionMode() == right.executionMode()
                && left.providerType() == right.providerType()
                && left.baseUrl().equals(right.baseUrl())
                && left.apiKey().equals(right.apiKey())
                && left.model().equals(right.model());
    }

    private static Component errorStatus(AiErrorCategory category) {
        return Component.translatable(
                "screen.wishing_willow.ai.status.error." + category.name().toLowerCase(Locale.ROOT)
        );
    }

    private static String providerKey(AiProviderType provider) {
        return "screen.wishing_willow.ai.provider." + provider.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public void tick() {
        baseUrlInput.tick();
        apiKeyInput.tick();
        modelInput.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int fieldWidth = panelWidth - 24;
        int totalContent = fieldWidth < 300 ? 252 : 205;
        int maximum = Math.max(0, totalContent - Math.max(1, viewportBottom - viewportTop));
        if (maximum > 0) {
            scrollOffset = Math.max(0, Math.min(maximum, scrollOffset - (int) Math.signum(delta) * 20));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RetroUiTheme.drawBackdrop(graphics);
        RetroUiTheme.drawPaperPanel(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        graphics.drawCenteredString(font, title, width / 2, 10, RetroUiTheme.OXBLOOD_DARK);
        int labelX = panelLeft + 12;
        drawLabelIfVisible(graphics, Component.translatable("screen.wishing_willow.ai.base_url"),
                labelX, contentOrigin + 27);
        drawLabelIfVisible(graphics, Component.translatable("screen.wishing_willow.ai.api_key"),
                labelX, contentOrigin + 64);
        drawLabelIfVisible(graphics, Component.translatable("screen.wishing_willow.ai.model"),
                labelX, contentOrigin + 101);
        if (statusY >= viewportTop && statusY + 20 <= viewportBottom) {
            boolean connected = testedConfig != null && !busy;
            if (font.width(status) <= panelWidth - 42) {
                RetroUiTheme.drawStatusBadge(graphics, font, status, width / 2, statusY, connected);
            } else {
                int lineY = statusY - 3;
                for (net.minecraft.util.FormattedCharSequence line : font.split(status, panelWidth - 34)) {
                    graphics.drawCenteredString(font, line, width / 2, lineY,
                            connected ? RetroUiTheme.STATUS_OK : RetroUiTheme.STATUS_WARN);
                    lineY += 10;
                    if (lineY > statusY + 10) break;
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawLabelIfVisible(GuiGraphics graphics, Component label, int x, int y) {
        if (y >= viewportTop && y + font.lineHeight <= viewportBottom) {
            graphics.drawString(font, label, x, y, RetroUiTheme.INK, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
