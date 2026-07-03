package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(MerchantResultSlot.class)
@SuppressWarnings("target")
public abstract class MerchantResultSlotMixin {
    private static final Field RMC$MERCHANT_FIELD = rmc$field("merchant", "f_40081_");
    private static final Field RMC$SLOTS_FIELD = rmc$field("slots", "f_40078_");
    private static final Field RMC$PLAYER_FIELD = rmc$field("player", "f_40079_");

    @Inject(
            method = {
                    "remove(I)Lnet/minecraft/world/item/ItemStack;",
                    "m_6201_(I)Lnet/minecraft/world/item/ItemStack;"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for MerchantResultSlot#remove.")
    private void rmc$blockCorruptedTradeRemoval(int amount, CallbackInfoReturnable<ItemStack> callback) {
        if (rmc$tradeBlocked()) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(
            method = {
                    "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
                    "m_142406_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for MerchantResultSlot#onTake.")
    private void rmc$blockCorruptedTradeCompletion(Player player, ItemStack stack, CallbackInfo callback) {
        if (rmc$tradeBlocked()) {
            callback.cancel();
        }
    }

    private boolean rmc$tradeBlocked() {
        MerchantContainer slots = rmc$get(RMC$SLOTS_FIELD, MerchantContainer.class);
        Merchant merchant = rmc$get(RMC$MERCHANT_FIELD, Merchant.class);
        Player player = rmc$get(RMC$PLAYER_FIELD, Player.class);
        MerchantOffer offer = slots == null ? null : slots.getActiveOffer();
        return CorruptionMechanicsManager.shouldBlockMerchantTrade(merchant, player, offer);
    }

    private static Field rmc$field(String... names) {
        Class<?> type = MerchantResultSlot.class;
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private <T> T rmc$get(Field field, Class<T> type) {
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(this);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }
}
