package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
@SuppressWarnings("target")
public abstract class AbstractFurnaceBlockEntityMixin {
    private static final ThreadLocal<AbstractFurnaceBlockEntity> RMC$CURRENT_FURNACE = new ThreadLocal<>();

    @Inject(
            method = {
                    "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)V",
                    "m_155013_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Tracks the furnace behind static furnace recipe checks.")
    private static void rmc$beginFurnaceTick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo callback) {
        RMC$CURRENT_FURNACE.set(blockEntity);
    }

    @Inject(
            method = {
                    "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)V",
                    "m_155013_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void rmc$endFurnaceTick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo callback) {
        RMC$CURRENT_FURNACE.remove();
    }

    @Inject(
            method = {
                    "getBurnDuration(Lnet/minecraft/world/item/ItemStack;)I",
                    "m_7743_(Lnet/minecraft/world/item/ItemStack;)I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractFurnaceBlockEntity#getBurnDuration.")
    private void rmc$corruptBurnDuration(ItemStack fuel, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptFurnaceBurnDuration((BlockEntity) (Object) this, fuel, callback.getReturnValue()));
    }

    @Inject(
            method = {
                    "canBurn(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/crafting/Recipe;Lnet/minecraft/core/NonNullList;I)Z",
                    "m_155005_(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/crafting/Recipe;Lnet/minecraft/core/NonNullList;I)Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for AbstractFurnaceBlockEntity#canBurn.")
    private void rmc$corruptCanBurn(RegistryAccess registryAccess, Recipe<?> recipe, NonNullList<ItemStack> items, int maxStackSize, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptFurnaceCanBurn((BlockEntity) (Object) this, callback.getReturnValue()));
    }
}
