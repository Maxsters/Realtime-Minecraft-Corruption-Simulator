package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ItemEntity.class)
@SuppressWarnings("target")
public abstract class ItemEntityPhysicsMixin {
    @ModifyConstant(
            method = {"tick()V", "m_8119_()V"},
            constant = @Constant(doubleValue = -0.04D),
            remap = false,
            require = 0
    )
    @Dynamic("Targets the actual gravity constant in ItemEntity#tick.")
    private double rmc$corruptGravity(double vanilla) {
        return CorruptionMechanicsManager.corruptDroppedItemGravity((ItemEntity) (Object) this, vanilla);
    }

    @ModifyConstant(
            method = {"tick()V", "m_8119_()V"},
            constant = @Constant(doubleValue = -0.5D),
            remap = false,
            require = 0
    )
    @Dynamic("Targets the actual ground restitution constant in ItemEntity#tick.")
    private double rmc$corruptGroundBounce(double vanilla) {
        return CorruptionMechanicsManager.corruptDroppedItemBounce((ItemEntity) (Object) this, vanilla);
    }
}
