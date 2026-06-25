package com.egologic.mcextremo.skilltree;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class SkillTreeScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final SkillTreeManager manager;
    private final PlayerEntity viewer;

    public SkillTreeScreenHandler(int syncId, PlayerInventory playerInv, Inventory inventory,
                                  SkillTreeManager manager, PlayerEntity viewer) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.inventory = inventory;
        this.manager = manager;
        this.viewer = viewer;

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 184 + row * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) { return false; }
                });
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 242) {
                @Override
                public boolean canInsert(ItemStack stack) { return false; }
            });
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (actionType != SlotActionType.PICKUP) return;
        if (slotIndex < 0 || slotIndex >= 54) return;
        if (manager == null) return;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        Skill skill = manager.getSkillForSlot(slotIndex);
        if (skill == null) return;

        if (manager.hasSkill(player.getUuid(), skill)) {
            serverPlayer.sendMessage(
                Text.literal("\u00A7e\u2139 \u00A77Ya tienes desbloqueada: " + skill.getDisplayName()), false);
            return;
        }

        if (!manager.canUnlock(player.getUuid(), skill)) {
            serverPlayer.sendMessage(
                Text.literal("\u00A7c\u2716 \u00A77Requisito no cumplido para: " + skill.getDisplayName()), false);
            return;
        }

        if (player.experienceLevel < skill.getCost()) {
            serverPlayer.sendMessage(
                Text.literal("\u00A7c\u2716 \u00A77Necesitas " + skill.getCost() + " niveles (tienes " + player.experienceLevel + ")"), false);
            return;
        }

        manager.unlockSkill(serverPlayer, skill);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
    }

    public Inventory getInventory() {
        return inventory;
    }
}
