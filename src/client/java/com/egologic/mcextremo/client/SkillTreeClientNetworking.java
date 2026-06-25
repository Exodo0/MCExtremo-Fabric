package com.egologic.mcextremo.client;

import com.egologic.mcextremo.client.screen.SkillTreeScreen;
import com.egologic.mcextremo.network.SkillTreeNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.HashMap;
import java.util.Map;

public final class SkillTreeClientNetworking {
    private SkillTreeClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SkillTreeNetworking.OPEN_SKILL_TREE,
            (client, handler, buf, responseSender) -> {
                int experienceLevel = buf.readInt();
                int count = buf.readInt();
                Map<String, SkillTreeScreen.SkillState> states = new HashMap<>();
                for (int i = 0; i < count; i++) {
                    String id = buf.readString(64);
                    boolean unlocked = buf.readBoolean();
                    boolean canUnlock = buf.readBoolean();
                    int cost = buf.readInt();
                    states.put(id, new SkillTreeScreen.SkillState(unlocked, canUnlock, cost));
                }

                client.execute(() -> client.setScreen(new SkillTreeScreen(experienceLevel, states)));
            });
    }
}
