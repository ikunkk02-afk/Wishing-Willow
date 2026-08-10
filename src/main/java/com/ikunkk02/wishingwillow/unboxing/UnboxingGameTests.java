package com.ikunkk02.wishingwillow.unboxing;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(WishingWillow.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnboxingGameTests {
    private UnboxingGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(UnboxingGameTests.class);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void survivalExchangeIsIdempotent(GameTestHelper helper) {
        ServerPlayer player = player(helper, "SurvivalUnboxer");
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.PACKAGED_WISHING_WILLOW.get()));
        if (!UnboxingInventoryExchange.exchange(player, InteractionHand.MAIN_HAND)) {
            helper.fail("First packaged exchange was rejected");
            return;
        }
        if (!player.getMainHandItem().is(ModItems.WISHING_WILLOW.get())) {
            helper.fail("Survival exchange did not place the willow in the original hand");
            return;
        }
        if (UnboxingInventoryExchange.exchange(player, InteractionHand.MAIN_HAND)) {
            helper.fail("Repeated exchange produced another willow");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void creativeExchangeRetainsPackage(GameTestHelper helper) {
        ServerPlayer player = player(helper, "CreativeUnboxer");
        player.getAbilities().instabuild = true;
        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ModItems.PACKAGED_WISHING_WILLOW.get()));
        if (!UnboxingInventoryExchange.exchange(player, InteractionHand.OFF_HAND)
                || !player.getOffhandItem().is(ModItems.WISHING_WILLOW.get())
                || player.getInventory().countItem(ModItems.PACKAGED_WISHING_WILLOW.get()) != 1) {
            helper.fail("Creative exchange did not retain exactly one package and equip one willow");
            return;
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name));
    }
}
