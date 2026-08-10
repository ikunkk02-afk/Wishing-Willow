package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.ai.AiConnectionResult;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiModelListResult;
import com.ikunkk02.wishingwillow.ai.AiProviderType;
import com.ikunkk02.wishingwillow.ai.AiService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;

public final class AiSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 230;

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

    public AiSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.wishing_willow.ai.title"));
        this.parent = parent;
        this.draftConfig = AiConfigManager.getInstance().get();
    }

    @Override
    protected void init() {
        captureDraft();
        panelWidth = Math.min(PANEL_WIDTH, width - 24);
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(5, (height - PANEL_HEIGHT) / 2);
        int fieldX = panelLeft + 18;
        int fieldWidth = panelWidth - 36;

        providerButton = addRenderableWidget(CycleButton.<AiProviderType>builder(
                        value -> Component.translatable(providerKey(value))
                )
                .withValues(AiProviderType.values())
                .withInitialValue(draftConfig.providerType())
                .create(
                        fieldX, panelTop + 22, fieldWidth, 20,
                        Component.translatable("screen.wishing_willow.ai.provider"),
                        (button, value) -> selectProvider(value)
                ));

        baseUrlInput = new EditBox(
                font, fieldX, panelTop + 58, fieldWidth, 20,
                Component.translatable("screen.wishing_willow.ai.base_url")
        );
        baseUrlInput.setMaxLength(AiConfig.MAX_BASE_URL_LENGTH);
        baseUrlInput.setValue(draftConfig.baseUrl());
        baseUrlInput.setResponder(value -> invalidateTest());
        addRenderableWidget(baseUrlInput);

        apiKeyInput = new PasswordEditBox(
                font, fieldX, panelTop + 94, fieldWidth - 64, 20,
                Component.translatable("screen.wishing_willow.ai.api_key")
        );
        apiKeyInput.setMaxLength(AiConfig.MAX_API_KEY_LENGTH);
        apiKeyInput.setValue(draftConfig.apiKey());
        apiKeyInput.setResponder(value -> invalidateTest());
        addRenderableWidget(apiKeyInput);
        showKeyButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.show"),
                        button -> toggleKeyVisibility()
                )
                .bounds(fieldX + fieldWidth - 60, panelTop + 94, 60, 20)
                .build());

        modelInput = new EditBox(
                font, fieldX, panelTop + 130, fieldWidth, 20,
                Component.translatable("screen.wishing_willow.ai.model")
        );
        modelInput.setMaxLength(AiConfig.MAX_MODEL_LENGTH);
        modelInput.setValue(draftConfig.model());
        modelInput.setResponder(value -> invalidateTest());
        addRenderableWidget(modelInput);

        int actionsY = panelTop + 158;
        int actionWidth = (fieldWidth - 12) / 3;
        fetchModelsButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.fetch_models"),
                        button -> fetchModels()
                )
                .bounds(fieldX, actionsY, actionWidth, 20)
                .build());
        testConnectionButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.test_connection"),
                        button -> testConnection()
                )
                .bounds(fieldX + actionWidth + 6, actionsY, actionWidth, 20)
                .build());
        testInterpretationButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.test_interpretation"),
                        button -> openTestInterpretation()
                )
                .bounds(fieldX + (actionWidth + 6) * 2, actionsY, actionWidth, 20)
                .build());

        int bottomY = panelTop + 204;
        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.ai.save"),
                        button -> save()
                )
                .bounds(width / 2 - 106, bottomY, 100, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.wishing_willow.cancel"),
                        button -> onClose()
                )
                .bounds(width / 2 + 6, bottomY, 100, 20)
                .build());
        updateButtonStates();
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
            status = Component.translatable("screen.wishing_willow.ai.status.success");
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
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + PANEL_HEIGHT, 0xE31A1816);
        graphics.renderOutline(panelLeft, panelTop, panelWidth, PANEL_HEIGHT, 0xFF62594F);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 7, 0xFFFFFFFF);
        int labelX = panelLeft + 18;
        graphics.drawString(font, Component.translatable("screen.wishing_willow.ai.base_url"), labelX, panelTop + 48, 0xFFD6D2CB);
        graphics.drawString(font, Component.translatable("screen.wishing_willow.ai.api_key"), labelX, panelTop + 84, 0xFFD6D2CB);
        graphics.drawString(font, Component.translatable("screen.wishing_willow.ai.model"), labelX, panelTop + 120, 0xFFD6D2CB);
        graphics.drawString(font, Component.translatable("screen.wishing_willow.ai.status", status), labelX, panelTop + 184, 0xFFBDB7AF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
