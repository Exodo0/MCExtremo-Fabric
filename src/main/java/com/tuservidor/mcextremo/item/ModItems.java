package com.tuservidor.mcextremo.item;

import com.tuservidor.mcextremo.MCExtremo;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
    public static final Item LIFE_HEART = register("life_heart", new Item(new Item.Settings()));
    public static final Item HORDE_SHARD = register("horde_shard", new Item(new Item.Settings()));

    private ModItems() {
    }

    public static void register() {
        MCExtremo.LOGGER.info("MCExtremo items registered");
    }

    private static Item register(String id, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(MCExtremo.MOD_ID, id), item);
    }
}
