package com.ikunkk02.wishingwillow.client.gui;

import com.ikunkk02.wishingwillow.research.KnowledgeBaseState;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.ModCategory;
import com.ikunkk02.wishingwillow.research.ResearchState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

final class ResearchUiText {
    private ResearchUiText() {
    }

    static MutableComponent category(ModCategory value) {
        return Component.translatable(key("category", value.name())).withStyle(switch (value) {
            case HORROR -> ChatFormatting.RED;
            case TECHNOLOGY -> ChatFormatting.AQUA;
            case MAGIC, DIMENSION -> ChatFormatting.LIGHT_PURPLE;
            case MOBS, COMBAT -> ChatFormatting.GOLD;
            case WORLDGEN, CONTENT -> ChatFormatting.GREEN;
            case LIBRARY, API, PERFORMANCE -> ChatFormatting.GRAY;
            default -> ChatFormatting.WHITE;
        });
    }

    static MutableComponent level(KnowledgeLevel value) {
        return Component.translatable(key("level", value.name())).withStyle(switch (value) {
            case VERIFIED -> ChatFormatting.GREEN;
            case UNDERSTOOD -> ChatFormatting.AQUA;
            case IDENTIFIED -> ChatFormatting.YELLOW;
            case UNKNOWN -> ChatFormatting.GRAY;
        });
    }

    static MutableComponent state(ResearchState value) {
        return Component.translatable(key("state", value.name())).withStyle(switch (value) {
            case READY -> ChatFormatting.GREEN;
            case PARTIAL, FAILED -> ChatFormatting.RED;
            case IGNORED -> ChatFormatting.DARK_GRAY;
            case WAITING_FOR_AI -> ChatFormatting.GOLD;
            case SCANNING, IDENTIFYING, FETCHING, ANALYZING, VERIFYING -> ChatFormatting.AQUA;
            default -> ChatFormatting.GRAY;
        });
    }

    static MutableComponent baseState(KnowledgeBaseState value) {
        return Component.translatable(key("base_state", value.name())).withStyle(switch (value) {
            case READY -> ChatFormatting.GREEN;
            case PARTIAL_READY -> ChatFormatting.YELLOW;
            case LOCAL_ONLY -> ChatFormatting.GOLD;
            case RUNNING -> ChatFormatting.AQUA;
            case PAUSED -> ChatFormatting.GRAY;
            case NOT_STARTED -> ChatFormatting.DARK_GRAY;
        });
    }

    static int progressColor(ResearchState value) {
        return switch (value) {
            case FAILED, PARTIAL -> 0xFFCE5656;
            case WAITING_FOR_AI -> 0xFFD69B3C;
            case READY -> 0xFF55B96B;
            case IGNORED -> 0xFF777777;
            default -> 0xFF52A9C9;
        };
    }

    private static String key(String group, String value) {
        return "screen.wishing_willow.knowledge." + group + "." + value.toLowerCase(Locale.ROOT);
    }
}
