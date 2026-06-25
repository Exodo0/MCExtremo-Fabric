package com.egologic.mcextremo.skilltree;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class SkillEffectApplier {
    private static final UUID BASE_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    public static void applySkill(ServerPlayerEntity player, Skill skill) {
        if (skill.getAttribute() == null) return;

        EntityAttributeInstance attrInstance = player.getAttributeInstance(skill.getAttribute());
        if (attrInstance == null) return;

        UUID modifierId = getModifierId(skill);
        attrInstance.removeModifier(modifierId);

        attrInstance.addPersistentModifier(new EntityAttributeModifier(
            modifierId,
            "skill_" + skill.getId(),
            skill.getAttributeValue(),
            EntityAttributeModifier.Operation.ADDITION
        ));

        if (skill.getAttribute() == net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH) {
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    public static void removeSkill(ServerPlayerEntity player, Skill skill) {
        if (skill.getAttribute() == null) return;

        EntityAttributeInstance attrInstance = player.getAttributeInstance(skill.getAttribute());
        if (attrInstance == null) return;

        attrInstance.removeModifier(getModifierId(skill));

        if (skill.getAttribute() == net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH) {
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    public static void removeAllModifiers(ServerPlayerEntity player) {
        for (Skill skill : Skill.values()) {
            if (skill.getAttribute() != null) {
                removeSkill(player, skill);
            }
        }
    }

    private static UUID getModifierId(Skill skill) {
        return UUID.nameUUIDFromBytes(
            ("mcextremo_skill_" + skill.getId()).getBytes()
        );
    }
}
