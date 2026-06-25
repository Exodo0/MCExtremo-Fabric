package com.egologic.mcextremo.manager;

import com.mojang.authlib.GameProfile;
import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.util.TextUtil;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import net.minecraft.world.GameMode;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LivesManager {
    private static final int BAN_DELAY_TICKS = 6 * 20;

    private final MCExtremo mod;
    private final Map<UUID, PendingElimination> pendingEliminations = new HashMap<>();

    private record PendingElimination(GameProfile profile, int ticksRemaining) {
        PendingElimination tick() {
            return new PendingElimination(profile, ticksRemaining - 1);
        }
    }

    public LivesManager(MCExtremo mod) {
        this.mod = mod;
    }

    public int getDefaultLives() {
        return ModConfig.get().vidas.defaultLives;
    }

    public void load() {
        mod.getDataManager().load();
    }

    public void save() {
        mod.getDataManager().save();
    }

    public int getVidas(UUID uuid) {
        return mod.getDataManager().getVidas(uuid);
    }

    public Map<UUID, Integer> getAllVidas() {
        return mod.getDataManager().getAllVidas();
    }

    public void setVidas(UUID uuid, int count) {
        mod.getDataManager().setVidas(uuid, count);
        ServerPlayerEntity player = mod.getDataManager().getServer() != null
            ? mod.getDataManager().getServer().getPlayerManager().getPlayer(uuid) : null;
        if (player != null) {
            mod.getScoreboardManager().updateScoreboard(player);
        }
    }

    public void addVidas(UUID uuid, int amount) {
        setVidas(uuid, getVidas(uuid) + amount);
    }

    public int quitarVida(ServerPlayerEntity player) {
        int actuales = getVidas(player.getUuid());
        int nuevas = Math.max(0, actuales - 1);
        setVidas(player.getUuid(), nuevas);
        return nuevas;
    }

    public boolean isEliminated(UUID uuid) {
        return getVidas(uuid) <= 0;
    }

    public Text getEliminationText(String playerName) {
        String name = playerName == null || playerName.isBlank() ? "jugador" : playerName;
        return TextUtil.literal(formatDisconnectMessage(name));
    }

    public void eliminar(ServerPlayerEntity player) {
        if (pendingEliminations.containsKey(player.getUuid())) return;

        player.changeGameMode(GameMode.SPECTATOR);
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.sendMessage(TextUtil.literal("&4Has muerto definitivamente. &7Seras expulsado en &c6 segundos&7."), false);

        if (ModConfig.get().reviveTrial.activado) {
            mod.getReviveTrialManager().startTrial(player);
            return;
        }

        if (!ModConfig.get().baneo.activado) return;

        pendingEliminations.put(player.getUuid(), new PendingElimination(player.getGameProfile(), BAN_DELAY_TICKS));
    }

    public void scheduleEliminationBan(ServerPlayerEntity player) {
        if (!ModConfig.get().baneo.activado) return;
        pendingEliminations.put(player.getUuid(), new PendingElimination(player.getGameProfile(), BAN_DELAY_TICKS));
    }

    public void tick(MinecraftServer server) {
        if (pendingEliminations.isEmpty()) return;

        var iterator = pendingEliminations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingElimination> entry = iterator.next();
            PendingElimination pending = entry.getValue().tick();
            if (pending.ticksRemaining() > 0) {
                entry.setValue(pending);
                continue;
            }

            banProfile(server, pending.profile());
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                player.networkHandler.disconnect(getEliminationText(pending.profile().getName()));
            }
            iterator.remove();
        }
    }

    public boolean handleJoin(ServerPlayerEntity player) {
        if (getVidas(player.getUuid()) <= 0) {
            if (ModConfig.get().reviveTrial.activado && mod.getReviveTrialManager().canResumeTrial(player.getUuid())) {
                mod.getReviveTrialManager().startTrial(player);
                return true;
            }
            if (ModConfig.get().baneo.activado) {
                banProfile(player.getServer(), player.getGameProfile());
                player.networkHandler.disconnect(getEliminationText(player.getName().getString()));
            } else {
                player.changeGameMode(GameMode.SPECTATOR);
            }
            return false;
        }

        mod.getReviveTrialManager().recoverInterruptedVoluntaryTrial(player);
        if (player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
            restoreOnlinePlayer(player);
        }
        mod.getRewardManager().applyHeartBonus(player);
        return true;
    }

    public void revivir(ServerPlayerEntity player) {
        pendingEliminations.remove(player.getUuid());
        unban(player.getServer(), player.getGameProfile());
        reviveProfile(player.getGameProfile());
        restoreAfterRevive(player);
    }

    public boolean revivir(MinecraftServer server, String playerName) {
        ServerPlayerEntity online = getOnlinePlayer(server, playerName);
        if (online != null) {
            revivir(online);
            return true;
        }

        GameProfile profile = resolveProfile(server, playerName);
        if (profile == null || profile.getId() == null) {
            return false;
        }

        pendingEliminations.remove(profile.getId());
        unban(server, profile);
        reviveProfile(profile);
        mod.getDataManager().setTrialState(profile.getId(), "ALIVE");
        mod.getDataManager().clearTrialInventory(profile.getId());
        return true;
    }

    private void reviveProfile(GameProfile profile) {
        setVidas(profile.getId(), getDefaultLives());
    }

    public void restoreAfterRevive(ServerPlayerEntity player) {
        mod.getDataManager().setTrialState(player.getUuid(), "ALIVE");
        unban(player.getServer(), player.getGameProfile());
        restoreOnlinePlayer(player);
    }

    private void restoreOnlinePlayer(ServerPlayerEntity player) {
        player.changeGameMode(GameMode.SURVIVAL);
        ServerWorld spawnWorld = player.getServer().getOverworld();
        player.teleport(
            spawnWorld,
            spawnWorld.getSpawnPos().getX() + 0.5,
            spawnWorld.getSpawnPos().getY(),
            spawnWorld.getSpawnPos().getZ() + 0.5,
            player.getYaw(),
            player.getPitch()
        );
        player.setHealth(20.0f);
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
        mod.getRewardManager().applyHeartBonus(player);
        player.setHealth(player.getMaxHealth());
    }

    private void unban(MinecraftServer server, GameProfile profile) {
        if (server.getPlayerManager().getUserBanList().contains(profile)) {
            server.getPlayerManager().getUserBanList().remove(profile);
        }
    }

    private void banProfile(MinecraftServer server, GameProfile profile) {
        if (server.getPlayerManager().getUserBanList().contains(profile)) return;

        server.getPlayerManager().getUserBanList().add(new BannedPlayerEntry(
            profile,
            new Date(),
            "MCExtremo",
            null,
            TextUtil.color(ModConfig.get().baneo.mensaje
                .replace("\\n", " ")
                .replace("\n", " ")
                .replace("{jugador}", profile.getName()))
        ));
    }

    private GameProfile resolveProfile(MinecraftServer server, String playerName) {
        Optional<GameProfile> cached = server.getUserCache().findByName(playerName);
        return cached.orElseGet(() -> Uuids.getOfflinePlayerProfile(playerName));
    }

    private ServerPlayerEntity getOnlinePlayer(MinecraftServer server, String playerName) {
        ServerPlayerEntity exact = server.getPlayerManager().getPlayer(playerName);
        if (exact != null) return exact;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(playerName)) {
                return player;
            }
        }
        return null;
    }

    private String formatDisconnectMessage(String playerName) {
        return ModConfig.get().baneo.mensaje
            .replace("\\n", "\n")
            .replace("{jugador}", playerName);
    }

    public void sendMessage(ServerPlayerEntity player, String message) {
        player.sendMessage(TextUtil.literal(message), false);
    }

    public String formatMessage(String template, String jugador, int vidas) {
        return template.replace("{jugador}", jugador).replace("{vidas}", String.valueOf(vidas));
    }
}
