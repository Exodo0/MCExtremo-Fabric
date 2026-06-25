package com.tuservidor.mcextremo.manager;

import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.config.ModConfig;
import com.tuservidor.mcextremo.item.ModItems;
import com.tuservidor.mcextremo.skilltree.Skill;
import com.tuservidor.mcextremo.skilltree.SkillPassiveHandler;
import com.tuservidor.mcextremo.util.TextUtil;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;

import java.util.Random;
import java.util.UUID;

public class RewardManager {
    public static final String NBT_KEY = "mcextremo_item";
    public static final String HEART_ID = "life_heart";
    public static final String MATERIAL_ID = "horde_shard";

    private static final UUID HEART_MODIFIER_ID = UUID.fromString("6d7116cf-a8a9-4e40-9864-a1278537a572");

    private final MCExtremo mod;
    private final Random random = new Random();

    public RewardManager(MCExtremo mod) {
        this.mod = mod;
    }

    public void registerEvents() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!isCustomItem(stack, HEART_ID)) return TypedActionResult.pass(stack);
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return TypedActionResult.pass(stack);

            boolean consumed = consumeHeart(serverPlayer, stack);
            return consumed ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
        });
    }

    public void giveHordeRewards(ServerPlayerEntity player, String tierName, int day) {
        ModConfig.Zombies.Horda config = ModConfig.get().zombies.horda;
        if (!config.recompensasActivado) return;

        int materials = getMaterialCount(day, config);
        if (day >= 35 && SkillPassiveHandler.hasSkill(player, Skill.CAZADOR_T2)) {
            materials++;
        }
        if (materials > 0) {
            giveOrDrop(player, createMaterial(materials));
        }

        double heartChance = getHeartChance(day, config);
        if (ModConfig.get().corazones.activado && random.nextDouble() < heartChance) {
            giveOrDrop(player, createHeart(1));
            player.sendMessage(TextUtil.literal("&c\u2764 &eLa horda ha soltado un Corazon de Vida."), false);
        }

        if (materials > 0) {
            player.sendMessage(TextUtil.literal("&6Recompensa de horda: &e" + materials + " fragmento(s) de mejora."), false);
        }
    }

    public ItemStack createHeart(int count) {
        ItemStack stack = new ItemStack(ModItems.LIFE_HEART, count);
        stack.setCustomName(TextUtil.literal("&c\u2764 Corazon de Vida"));
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(NBT_KEY, HEART_ID);
        setLore(stack,
            "&7Consumelo para ganar vida maxima permanente.",
            "&7Limite: &e" + ModConfig.get().corazones.maxCorazonesPorJugador + " corazones"
        );
        return stack;
    }

    public ItemStack createMaterial(int count) {
        ItemStack stack = new ItemStack(ModItems.HORDE_SHARD, count);
        stack.setCustomName(TextUtil.literal("&5Fragmento de Horda"));
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(NBT_KEY, MATERIAL_ID);
        setLore(stack,
            "&7Material usado para mejorar armaduras y herramientas.",
            "&7Usa &e/mce upgrade &7con el equipo en la mano."
        );
        return stack;
    }

    public boolean isCustomItem(ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) return false;
        if (HEART_ID.equals(id) && stack.isOf(ModItems.LIFE_HEART)) return true;
        if (MATERIAL_ID.equals(id) && stack.isOf(ModItems.HORDE_SHARD)) return true;
        return stack.hasNbt() && id.equals(stack.getNbt().getString(NBT_KEY));
    }

    public int countMaterials(ServerPlayerEntity player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isCustomItem(stack, MATERIAL_ID)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public boolean removeMaterials(ServerPlayerEntity player, int amount) {
        if (countMaterials(player) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!isCustomItem(stack, MATERIAL_ID)) continue;
            int remove = Math.min(remaining, stack.getCount());
            stack.decrement(remove);
            remaining -= remove;
        }
        return true;
    }

    public void applyHeartBonus(ServerPlayerEntity player) {
        var attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr == null) return;

        attr.removeModifier(HEART_MODIFIER_ID);
        int hearts = mod.getDataManager().getCorazones(player.getUuid());
        double bonus = hearts * ModConfig.get().corazones.hpPorCorazon;
        if (bonus > 0) {
            attr.addPersistentModifier(new EntityAttributeModifier(
                HEART_MODIFIER_ID,
                "mcextremo_life_hearts",
                bonus,
                EntityAttributeModifier.Operation.ADDITION
            ));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private boolean consumeHeart(ServerPlayerEntity player, ItemStack stack) {
        ModConfig.Corazones config = ModConfig.get().corazones;
        if (!config.activado) return false;

        int current = mod.getDataManager().getCorazones(player.getUuid());
        if (current >= config.maxCorazonesPorJugador) {
            player.sendMessage(TextUtil.literal("&cYa alcanzaste el maximo de corazones permanentes."), false);
            return false;
        }

        mod.getDataManager().setCorazones(player.getUuid(), current + 1);
        stack.decrement(1);
        applyHeartBonus(player);
        player.setHealth(player.getMaxHealth());
        player.sendMessage(TextUtil.literal("&c\u2764 &aHas ganado vida maxima permanente."), false);
        return true;
    }

    private void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack.copy())) {
            player.dropItem(stack, false);
        }
    }

    private int getMaterialCount(int day, ModConfig.Zombies.Horda config) {
        if (day < 15) return Math.min(config.materialesMini, 1);
        if (day < 35) return Math.min(config.materialesHorda, 2);
        if (day < 65) return Math.min(config.materialesGrande, 4);
        if (day < 100) return Math.min(config.materialesMasiva, 5);
        return Math.min(config.materialesExtrema, 7);
    }

    private double getHeartChance(int day, ModConfig.Zombies.Horda config) {
        if (day < 15) return Math.min(config.corazonChanceMini, 0.08);
        if (day < 35) return Math.min(config.corazonChanceHorda, 0.14);
        if (day < 65) return Math.min(config.corazonChanceGrande, 0.24);
        if (day < 100) return Math.min(config.corazonChanceMasiva, 0.34);
        return Math.min(config.corazonChanceExtrema, 0.45);
    }

    private void setLore(ItemStack stack, String... lines) {
        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList lore = new NbtList();
        for (String line : lines) {
            lore.add(NbtString.of(Text.Serialization.toJsonString(TextUtil.literal(line))));
        }
        display.put("Lore", lore);
    }
}
