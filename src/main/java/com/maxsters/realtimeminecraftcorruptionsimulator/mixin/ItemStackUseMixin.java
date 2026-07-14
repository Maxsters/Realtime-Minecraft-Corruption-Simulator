package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
@SuppressWarnings("target")
public abstract class ItemStackUseMixin {
    @Inject(
            method = {
                    "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
                    "m_41682_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for ItemStack#use.")
    private void rmc$breakAirUseCallback(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (CorruptionMechanicsManager.shouldDisableItemUse(stack, level, "air_use")) {
            callback.setReturnValue(InteractionResultHolder.fail(stack));
        }
    }

    @Inject(
            method = {
                    "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
                    "m_41661_(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for ItemStack#useOn.")
    private void rmc$breakUseOnCallback(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        String subsystem = stack.getItem() instanceof BlockItem ? "block_placement" : "use_on";
        if (CorruptionMechanicsManager.shouldDisableItemUse(stack, context.getLevel(), subsystem)) {
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }
}
