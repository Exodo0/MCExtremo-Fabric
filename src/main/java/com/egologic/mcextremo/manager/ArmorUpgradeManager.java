package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.util.TextUtil;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

public class ArmorUpgradeManager {
    private final MCExtremo mod;

    public ArmorUpgradeManager(MCExtremo mod) {
        this.mod = mod;
    }

    public boolean upgradeHeldArmor(ServerPlayerEntity player) {
        if (!ModConfig.get().mejoras.activado) {
            player.sendMessage(TextUtil.literal("&cLas mejoras estan deshabilitadas."), false);
            return false;
        }

        ItemStack stack = player.getMainHandStack();
        if (!isUpgradeable(stack)) {
            player.sendMessage(TextUtil.literal("&cTen una armadura, arma, arco o herramienta en la mano principal."), false);
            return false;
        }

        ModConfig.Mejoras config = ModConfig.get().mejoras;
        Map<net.minecraft.enchantment.Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.get(stack));
        UpgradeChoice choice = chooseUpgrade(stack, enchantments, config);
        if (choice == null) {
            player.sendMessage(TextUtil.literal("&eEse equipo ya esta al maximo configurable."), false);
            return false;
        }

        int cost = config.materialesPorNivel;
        if (!mod.getRewardManager().removeMaterials(player, cost)) {
            player.sendMessage(TextUtil.literal("&cNecesitas " + cost + " Fragmentos de Horda."), false);
            return false;
        }

        enchantments.put(choice.enchantment(), choice.nextLevel());
        EnchantmentHelper.set(enchantments, stack);
        player.sendMessage(TextUtil.literal("&aEquipo mejorado: &e" + choice.displayName() + " " + choice.nextLevel()), false);
        return true;
    }

    private boolean isUpgradeable(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem
            || stack.getItem() instanceof SwordItem
            || stack.getItem() instanceof AxeItem
            || stack.getItem() instanceof PickaxeItem
            || stack.getItem() instanceof ShovelItem
            || stack.getItem() instanceof HoeItem
            || stack.getItem() instanceof BowItem
            || stack.getItem() instanceof CrossbowItem;
    }

    private UpgradeChoice chooseUpgrade(ItemStack stack, Map<net.minecraft.enchantment.Enchantment, Integer> enchantments, ModConfig.Mejoras config) {
        if (stack.getItem() instanceof ArmorItem) {
            UpgradeChoice protection = nextUpgrade(enchantments, Enchantments.PROTECTION, config.nivelMaxProteccion, "Proteccion");
            if (protection != null) return protection;
            return nextUpgrade(enchantments, Enchantments.UNBREAKING, config.nivelMaxIrrompibilidad, "Irrompibilidad");
        }

        if (stack.getItem() instanceof SwordItem) {
            UpgradeChoice sharpness = nextUpgrade(enchantments, Enchantments.SHARPNESS, config.nivelMaxFilo, "Filo");
            if (sharpness != null) return sharpness;
            return nextUpgrade(enchantments, Enchantments.UNBREAKING, config.nivelMaxIrrompibilidad, "Irrompibilidad");
        }

        if (stack.getItem() instanceof AxeItem) {
            UpgradeChoice sharpness = nextUpgrade(enchantments, Enchantments.SHARPNESS, config.nivelMaxFilo, "Filo");
            if (sharpness != null) return sharpness;
            UpgradeChoice efficiency = nextUpgrade(enchantments, Enchantments.EFFICIENCY, config.nivelMaxEficiencia, "Eficiencia");
            if (efficiency != null) return efficiency;
            return nextUpgrade(enchantments, Enchantments.UNBREAKING, config.nivelMaxIrrompibilidad, "Irrompibilidad");
        }

        if (stack.getItem() instanceof PickaxeItem || stack.getItem() instanceof ShovelItem || stack.getItem() instanceof HoeItem) {
            UpgradeChoice efficiency = nextUpgrade(enchantments, Enchantments.EFFICIENCY, config.nivelMaxEficiencia, "Eficiencia");
            if (efficiency != null) return efficiency;
            return nextUpgrade(enchantments, Enchantments.UNBREAKING, config.nivelMaxIrrompibilidad, "Irrompibilidad");
        }

        if (stack.getItem() instanceof BowItem) {
            UpgradeChoice power = nextUpgrade(enchantments, Enchantments.POWER, config.nivelMaxPoder, "Poder");
            if (power != null) return power;
            return nextUpgrade(enchantments, Enchantments.UNBREAKING, config.nivelMaxIrrompibilidad, "Irrompibilidad");
        }

        if (stack.getItem() instanceof CrossbowItem) {
            UpgradeChoice quickCharge = nextUpgrade(enchantments, Enchantments.QUICK_CHARGE, config.nivelMaxCargaRapida, "Carga Rapida");
            if (quickCharge != null) return quickCharge;
            return nextUpgrade(enchantments, Enchantments.UNBREAKING, config.nivelMaxIrrompibilidad, "Irrompibilidad");
        }

        return null;
    }

    private UpgradeChoice nextUpgrade(Map<net.minecraft.enchantment.Enchantment, Integer> enchantments, net.minecraft.enchantment.Enchantment enchantment, int maxLevel, String displayName) {
        int current = enchantments.getOrDefault(enchantment, 0);
        if (current >= maxLevel) return null;
        return new UpgradeChoice(enchantment, current + 1, displayName);
    }

    private record UpgradeChoice(net.minecraft.enchantment.Enchantment enchantment, int nextLevel, String displayName) {
    }
}
