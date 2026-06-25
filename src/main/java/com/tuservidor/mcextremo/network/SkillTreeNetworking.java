package com.tuservidor.mcextremo.network;

import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.skilltree.Skill;
import com.tuservidor.mcextremo.skilltree.SkillTreeManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class SkillTreeNetworking {
    public static final Identifier OPEN_SKILL_TREE = new Identifier(MCExtremo.MOD_ID, "open_skill_tree");
    public static final Identifier UNLOCK_SKILL = new Identifier(MCExtremo.MOD_ID, "unlock_skill");

    private SkillTreeNetworking() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(UNLOCK_SKILL, (server, player, handler, buf, responseSender) -> {
            String skillId = buf.readString(64);
            server.execute(() -> unlockSkill(player, skillId));
        });
    }

    public static boolean sendSkillTree(ServerPlayerEntity player, SkillTreeManager manager) {
        if (!ServerPlayNetworking.canSend(player, OPEN_SKILL_TREE)) {
            return false;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(player.experienceLevel);
        buf.writeInt(Skill.values().length);
        for (Skill skill : Skill.values()) {
            buf.writeString(skill.getId());
            buf.writeBoolean(manager.hasSkill(player.getUuid(), skill));
            buf.writeBoolean(manager.canUnlock(player.getUuid(), skill));
            buf.writeInt(skill.getCost());
        }

        ServerPlayNetworking.send(player, OPEN_SKILL_TREE, buf);
        return true;
    }

    private static void unlockSkill(ServerPlayerEntity player, String skillId) {
        SkillTreeManager manager = MCExtremo.getInstance().getSkillTreeManager();
        if (manager == null) {
            player.sendMessage(Text.literal("\u00A7cEl sistema de habilidades no esta disponible."), false);
            return;
        }

        Skill skill = Skill.fromId(skillId);
        if (skill == null) {
            player.sendMessage(Text.literal("\u00A7cHabilidad desconocida."), false);
            return;
        }

        manager.tryUnlockSkill(player, skill);
    }
}
