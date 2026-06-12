package com.maxsters.realtimeminecraftcorruptionsimulator.registry;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.item.CorruptionToolItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RealtimeMinecraftCorruptionSimulator.MOD_ID);

    public static final RegistryObject<Item> CORRUPTION_TOOL = ITEMS.register(
            "corruption_tool",
            () -> new CorruptionToolItem(new Item.Properties().stacksTo(1))
    );

    private ModItems() {
    }
}
