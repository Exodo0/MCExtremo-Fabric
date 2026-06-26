package com.egologic.mcextremo.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.util.DifficultyPhase;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ModCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(createEnglishAlias()));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createEnglishAlias() {
        return literal("mce")
            .executes(ctx -> comandoAyudaMce(ctx.getSource()))
            .then(literal("lives")
                .executes(ctx -> comandoVidas(ctx.getSource()))
                .then(argument("player", StringArgumentType.word())
                    .executes(ctx -> comandoVidasJugador(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                )
            )
            .then(literal("setlives")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("player", StringArgumentType.word())
                    .then(argument("amount", IntegerArgumentType.integer(0))
                        .executes(ctx -> comandoSetVidas(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            IntegerArgumentType.getInteger(ctx, "amount")))
                    )
                )
            )
            .then(literal("addlives")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("player", StringArgumentType.word())
                    .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> comandoAddVidas(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            IntegerArgumentType.getInteger(ctx, "amount")))
                    )
                )
            )
            .then(literal("revive")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("player", StringArgumentType.word())
                    .executes(ctx -> comandoRevivir(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                )
            )
            .then(literal("status")
                .executes(ctx -> comandoEstado(ctx.getSource()))
                .then(argument("player", StringArgumentType.word())
                    .requires(s -> s.hasPermissionLevel(2))
                    .executes(ctx -> comandoEstadoJugador(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                )
            )
            .then(literal("dead")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> comandoMuertos(ctx.getSource()))
            )
            .then(literal("upgrade")
                .executes(ctx -> comandoMejorar(ctx.getSource()))
            )
            .then(literal("trial")
                .executes(ctx -> comandoTrialVoluntario(ctx.getSource()))
                .then(literal("start")
                    .requires(s -> s.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                        .executes(ctx -> comandoTrialIniciar(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                    )
                )
            )
            .then(literal("event")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("start")
                    .executes(ctx -> comandoEventStart(ctx.getSource()))
                )
                .then(literal("stop")
                    .executes(ctx -> comandoEventStop(ctx.getSource()))
                )
                .then(literal("status")
                    .executes(ctx -> comandoEventStatus(ctx.getSource()))
                )
            )
            .then(literal("hearts")
                .executes(ctx -> comandoCorazones(ctx.getSource(), null))
                .then(argument("player", StringArgumentType.word())
                    .requires(s -> s.hasPermissionLevel(2))
                    .executes(ctx -> comandoCorazones(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                )
            )
            .then(literal("days")
                .executes(ctx -> comandoDias(ctx.getSource()))
            )
            .then(literal("skipdays")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("amount", IntegerArgumentType.integer(1))
                    .executes(ctx -> comandoSaltarDias(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))
                )
            )
            .then(literal("reducedays")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("amount", IntegerArgumentType.integer(1))
                    .executes(ctx -> comandoReducirDias(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))
                )
            )
            .then(literal("skills")
                .executes(ctx -> comandoSkills(ctx.getSource()))
                .then(literal("info")
                    .executes(ctx -> comandoSkillsInfo(ctx.getSource()))
                )
                .then(literal("reset")
                    .requires(s -> s.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                        .executes(ctx -> comandoSkillsReset(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                    )
                )
            )
            .then(literal("reload")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> comandoReload(ctx.getSource()))
            )
            .then(literal("update")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> comandoUpdate(ctx.getSource()))
            )
            .then(literal("scoreboard")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> comandoScoreboard(ctx.getSource()))
            )
            .then(literal("spawnzombie")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> comandoSpawnZombie(ctx.getSource()))
            )
            .then(literal("zombieinfo")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> comandoZombieInfo(ctx.getSource()))
            )
            .then(literal("revivearena")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("set").executes(ctx -> comandoReviveArenaSet(ctx.getSource())))
                .then(literal("test").executes(ctx -> comandoReviveArenaTest(ctx.getSource())))
            )
            .then(literal("config")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> comandoConfig(ctx.getSource(), "list", ""))
                .then(literal("list").executes(ctx -> comandoConfig(ctx.getSource(), "list", "")))
                .then(literal("get")
                    .then(argument("path", StringArgumentType.greedyString())
                        .executes(ctx -> comandoConfig(ctx.getSource(), "get", StringArgumentType.getString(ctx, "path")))
                    )
                )
                .then(literal("set")
                    .then(argument("path", StringArgumentType.word())
                        .then(argument("value", StringArgumentType.greedyString())
                            .executes(ctx -> comandoConfigSet(ctx.getSource(), StringArgumentType.getString(ctx, "path"), StringArgumentType.getString(ctx, "value")))
                        )
                    )
                )
                .then(literal("reload").executes(ctx -> comandoConfigReload(ctx.getSource())))
                .then(literal("reset").executes(ctx -> comandoConfigReset(ctx.getSource())))
            )
            .then(literal("pvp")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("state", StringArgumentType.word())
                    .executes(ctx -> comandoPvp(ctx.getSource(), StringArgumentType.getString(ctx, "state")))
                )
            );
    }

    private static int comandoVidas(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("Usa: /mce lives <player>"), false);
            return 0;
        }
        int vidas = MCExtremo.getInstance().getLivesManager().getVidas(player.getUuid());
        source.sendFeedback(() -> Text.literal("\u00A7eTienes \u00A7c" + vidas + " \u00A7evida(s)."), false);
        return 1;
    }

    private static int comandoVidasJugador(ServerCommandSource source, String nombre) {
        var player = getOnlinePlayer(source.getServer(), nombre);
        if (player != null) {
            int vidas = MCExtremo.getInstance().getLivesManager().getVidas(player.getUuid());
            source.sendFeedback(() -> Text.literal("\u00A7e" + player.getName().getString() + "\u00A7c tiene " + vidas + " \u00A7evida(s)."), false);
        } else {
            source.sendFeedback(() -> Text.literal("\u00A7cJugador no encontrado."), false);
        }
        return 1;
    }

    private static int comandoSetVidas(ServerCommandSource source, String nombre, int cantidad) {
        var player = getOnlinePlayer(source.getServer(), nombre);
        if (player != null) {
            MCExtremo.getInstance().getLivesManager().setVidas(player.getUuid(), cantidad);
            source.sendFeedback(() -> Text.literal("\u00A7aVidas de " + player.getName().getString() + " establecidas a " + cantidad + "."), true);
        } else {
            source.sendFeedback(() -> Text.literal("\u00A7cJugador no encontrado."), false);
        }
        return 1;
    }

    private static int comandoAddVidas(ServerCommandSource source, String nombre, int cantidad) {
        var player = getOnlinePlayer(source.getServer(), nombre);
        if (player != null) {
            MCExtremo.getInstance().getLivesManager().addVidas(player.getUuid(), cantidad);
            source.sendFeedback(() -> Text.literal("\u00A7aSe agregaron " + cantidad + " vida(s) a " + player.getName().getString() + "."), true);
        } else {
            source.sendFeedback(() -> Text.literal("\u00A7cJugador no encontrado."), false);
        }
        return 1;
    }

    private static int comandoPvp(ServerCommandSource source, String estado) {
        MCExtremo mod = MCExtremo.getInstance();
        if (estado.equalsIgnoreCase("on")) {
            mod.getPvpScheduler().forzarEstado(source.getServer(), true);
            source.sendFeedback(() -> Text.literal("\u00A7aPvP activado."), true);
        } else if (estado.equalsIgnoreCase("off")) {
            mod.getPvpScheduler().forzarEstado(source.getServer(), false);
            source.sendFeedback(() -> Text.literal("\u00A7aPvP desactivado."), true);
        } else {
            source.sendFeedback(() -> Text.literal("\u00A7cUsa: /mce pvp <on|off>"), false);
        }
        return 1;
    }

    private static int comandoRevivir(ServerCommandSource source, String nombre) {
        boolean revived = MCExtremo.getInstance().getLivesManager().revivir(source.getServer(), nombre);
        if (revived) {
            source.sendFeedback(() -> Text.literal("\u00A7a" + nombre + " ha sido desbaneado y revivido."), true);
        } else {
            source.sendFeedback(() -> Text.literal("\u00A7cNo se pudo resolver el perfil de " + nombre + "."), false);
        }
        return 1;
    }

    private static int comandoReload(ServerCommandSource source) {
        ModConfig.reload();
        source.sendFeedback(() -> Text.literal("\u00A7aConfiguracion recargada."), true);
        return 1;
    }

    private static int comandoUpdate(ServerCommandSource source) {
        MCExtremo mod = MCExtremo.getInstance();
        source.sendFeedback(() -> Text.literal("\u00A7eRevisando GitHub Releases para la version del servidor..."), false);
        mod.getUpdateChecker().checkAsync(true).thenAccept(info ->
            source.getServer().execute(() -> mod.getUpdateChecker().sendStatus(source, info)));
        return 1;
    }

    private static int comandoScoreboard(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores pueden usar este comando."), false);
            return 0;
        }
        MCExtremo mod = MCExtremo.getInstance();
        if (mod.getScoreboardManager() != null) {
            mod.getScoreboardManager().forceUpdateScoreboard(player);
            source.sendFeedback(() -> Text.literal("\u00A7aScoreboard actualizado."), false);
        }
        return 1;
    }

    private static int comandoEstado(ServerCommandSource source) {
        MCExtremo mod = MCExtremo.getInstance();
        ServerPlayerEntity player = source.getEntity() instanceof ServerPlayerEntity p ? p : null;
        ServerWorld world = player != null ? (ServerWorld) player.getWorld() : source.getServer().getOverworld();

        int day = mod.getZombieManager().getDay(world);
        DifficultyPhase phase = mod.getZombieManager().getPhase(day);
        int intelligence = mod.getZombieManager().getIntelligenceLevel(day);
        String pvpState = mod.getPvpScheduler().isPvpEnabled() ? "\u00A7cACTIVO" : "\u00A7aINACTIVO";
        String pvpTimer = ModConfig.get().pvpProgramado.activado
            ? mod.getPvpScheduler().getTiempoRestanteFormatted()
            : "manual";

        source.sendFeedback(() -> Text.literal("\u00A76=== Estado MCExtremo ==="), false);
        if (player != null) {
            int lives = mod.getLivesManager().getVidas(player.getUuid());
            source.sendFeedback(() -> Text.literal("\u00A7eVidas: \u00A7c" + lives), false);
            int hearts = mod.getDataManager().getCorazones(player.getUuid());
            source.sendFeedback(() -> Text.literal("\u00A7eCorazones extra: \u00A7c" + hearts + "/" + ModConfig.get().corazones.maxCorazonesPorJugador), false);
            source.sendFeedback(() -> Text.literal("\u00A7eTrial: \u00A7f" + mod.getDataManager().getTrialState(player.getUuid())), false);
        }
        source.sendFeedback(() -> Text.literal("\u00A7eDia: \u00A7f" + day + " \u00A77(" + phase.getDisplayName() + ")"), false);
        source.sendFeedback(() -> Text.literal("\u00A7eAmenaza: \u00A7f" + mod.getZombieManager().getThreatSummary(day)), false);
        source.sendFeedback(() -> Text.literal("\u00A7eInteligencia zombie: \u00A7f" + mod.getZombieManager().getIntelligenceName(intelligence)), false);
        source.sendFeedback(() -> Text.literal("\u00A7ePvP: " + pvpState + " \u00A77Timer: \u00A7f" + pvpTimer), false);
        source.sendFeedback(() -> Text.literal("\u00A7eHordas: " + (mod.getZombieManager().canUseHordes(day) ? "\u00A7cactivas" : "\u00A78bloqueadas")), false);
        source.sendFeedback(() -> Text.literal("\u00A7eRomper bloques: " + (mod.getZombieManager().canBreakBlocks(day) ? "\u00A7cactivo" : "\u00A78bloqueado")), false);
        source.sendFeedback(() -> Text.literal("\u00A7eConstruccion zombie: " + (mod.getZombieManager().canBuild(day) ? "\u00A7cactiva" : "\u00A78bloqueada")), false);
        return 1;
    }

    private static int comandoEstadoJugador(ServerCommandSource source, String nombre) {
        MCExtremo mod = MCExtremo.getInstance();
        var online = getOnlinePlayer(source.getServer(), nombre);
        UUID uuid = null;
        String displayName = nombre;

        if (online != null) {
            uuid = online.getUuid();
            displayName = online.getName().getString();
        } else {
            Optional<com.mojang.authlib.GameProfile> cached = source.getServer().getUserCache().findByName(nombre);
            if (cached.isPresent()) {
                uuid = cached.get().getId();
                displayName = cached.get().getName();
            }
        }

        if (uuid == null) {
            source.sendFeedback(() -> Text.literal("\u00A7cNo se encontro perfil cacheado para " + nombre + "."), false);
            return 0;
        }

        int lives = mod.getLivesManager().getVidas(uuid);
        int hearts = mod.getDataManager().getCorazones(uuid);
        String trialState = mod.getDataManager().getTrialState(uuid);
        boolean banned = source.getServer().getPlayerManager().getUserBanList().contains(
            new com.mojang.authlib.GameProfile(uuid, displayName)
        );
        boolean onlineNow = online != null;
        String status = lives <= 0 ? "\u00A7cELIMINADO" : "\u00A7aVIVO";

        String finalDisplayName = displayName;
        source.sendFeedback(() -> Text.literal("\u00A76=== Estado de " + finalDisplayName + " ==="), false);
        source.sendFeedback(() -> Text.literal("\u00A7eVidas: \u00A7f" + lives), false);
        source.sendFeedback(() -> Text.literal("\u00A7eCorazones: \u00A7f" + hearts + "/" + ModConfig.get().corazones.maxCorazonesPorJugador), false);
        source.sendFeedback(() -> Text.literal("\u00A7eTrial: \u00A7f" + trialState), false);
        source.sendFeedback(() -> Text.literal("\u00A7eEstado: " + status), false);
        source.sendFeedback(() -> Text.literal("\u00A7eOnline: " + (onlineNow ? "\u00A7asi" : "\u00A78no")), false);
        source.sendFeedback(() -> Text.literal("\u00A7eBaneado: " + (banned ? "\u00A7csi" : "\u00A7ano")), false);
        return 1;
    }

    private static int comandoMuertos(ServerCommandSource source) {
        MCExtremo mod = MCExtremo.getInstance();
        Map<UUID, Integer> allLives = mod.getLivesManager().getAllVidas();
        source.sendFeedback(() -> Text.literal("\u00A76=== Jugadores eliminados ==="), false);

        int count = 0;
        for (Map.Entry<UUID, Integer> entry : allLives.entrySet()) {
            if (entry.getValue() > 0) continue;
            UUID uuid = entry.getKey();
            String name = source.getServer().getUserCache().getByUuid(uuid)
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse(uuid.toString());
            source.sendFeedback(() -> Text.literal("\u00A7c- " + name + " \u00A77(" + uuid + ")"), false);
            count++;
        }

        if (count == 0) {
            source.sendFeedback(() -> Text.literal("\u00A7aNo hay jugadores eliminados registrados."), false);
        }
        return 1;
    }

    private static int comandoMejorar(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        return MCExtremo.getInstance().getArmorUpgradeManager().upgradeHeldArmor(player) ? 1 : 0;
    }

    private static int comandoCorazones(ServerCommandSource source, String nombre) {
        ServerPlayerEntity target = nombre == null && source.getEntity() instanceof ServerPlayerEntity p
            ? p
            : nombre != null ? getOnlinePlayer(source.getServer(), nombre) : null;
        if (target == null) {
            source.sendFeedback(() -> Text.literal("\u00A7cJugador no encontrado o no esta online."), false);
            return 0;
        }
        int hearts = MCExtremo.getInstance().getDataManager().getCorazones(target.getUuid());
        int max = ModConfig.get().corazones.maxCorazonesPorJugador;
        source.sendFeedback(() -> Text.literal("\u00A7c\u2764 \u00A7e" + target.getName().getString() + ": \u00A7f" + hearts + "/" + max + " corazones"), false);
        return 1;
    }

    private static int comandoReviveArenaSet(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        MCExtremo.getInstance().getReviveTrialManager().setArena(player);
        source.sendFeedback(() -> Text.literal("\u00A7aArena de revive configurada en tu posicion."), true);
        return 1;
    }

    private static int comandoReviveArenaTest(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        MCExtremo.getInstance().getReviveTrialManager().startTrial(player);
        source.sendFeedback(() -> Text.literal("\u00A7aPrueba de revive iniciada."), true);
        return 1;
    }

    private static int comandoTrialVoluntario(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores pueden iniciar la prueba voluntaria."), false);
            return 0;
        }
        return MCExtremo.getInstance().getReviveTrialManager().startVoluntaryTrial(player) ? 1 : 0;
    }

    private static int comandoEventStart(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo un admin dentro del juego puede iniciar el event trial."), false);
            return 0;
        }
        return MCExtremo.getInstance().getEventTrialManager().startEvent(player) ? 1 : 0;
    }

    private static int comandoEventStop(ServerCommandSource source) {
        MCExtremo.getInstance().getEventTrialManager().stopEvent(source.getServer(), "\u00A7cEvent trial detenido por un admin.");
        source.sendFeedback(() -> Text.literal("\u00A7aEvent trial detenido."), true);
        return 1;
    }

    private static int comandoEventStatus(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("\u00A76=== Event Trial ==="), false);
        source.sendFeedback(() -> Text.literal("\u00A7e" + MCExtremo.getInstance().getEventTrialManager().getStatus()), false);
        return 1;
    }

    private static int comandoTrialIniciar(ServerCommandSource source, String nombre) {
        ServerPlayerEntity player = getOnlinePlayer(source.getServer(), nombre);
        if (player == null) {
            source.sendFeedback(() -> Text.literal("\u00A7cJugador no encontrado."), false);
            return 0;
        }
        MCExtremo.getInstance().getReviveTrialManager().startTrial(player);
        source.sendFeedback(() -> Text.literal("\u00A7aTrial iniciado para " + nombre + "."), true);
        return 1;
    }

    private static int comandoDias(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        MCExtremo mod = MCExtremo.getInstance();
        int day = mod.getZombieManager().getDay((ServerWorld) player.getWorld());
        double mult = mod.getZombieManager().getMultiplier(day);
        int intel = mod.getZombieManager().getIntelligenceLevel(day);
        DifficultyPhase phase = mod.getZombieManager().getPhase(day);
        DifficultyPhase next = phase.next();

        source.sendFeedback(() -> Text.literal("\u00A76=== Zombies Info ==="), false);
        source.sendFeedback(() -> Text.literal("\u00A7eDia actual: \u00A7f" + day), false);
        source.sendFeedback(() -> Text.literal("\u00A7eFase: \u00A7f" + phase.getDisplayName() + " \u00A77- " + phase.getDescription()), false);
        source.sendFeedback(() -> Text.literal("\u00A7eMultiplicador: \u00A7f" + mult + "x"), false);
        source.sendFeedback(() -> Text.literal("\u00A7eNivel inteligencia: \u00A7f" + mod.getZombieManager().getIntelligenceName(intel)), false);
        source.sendFeedback(() -> Text.literal("\u00A7eCapacidades: \u00A7f" + mod.getZombieManager().getThreatSummary(day)), false);
        if (next != null) {
            source.sendFeedback(() -> Text.literal("\u00A77Siguiente fase: \u00A7eDia " + next.getMinDay()
                + " \u00A77- " + next.getDisplayName()), false);
        }
        return 1;
    }

    private static int comandoSaltarDias(ServerCommandSource source, int days) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        ServerWorld serverWorld = (ServerWorld) player.getWorld();
        MCExtremo mod = MCExtremo.getInstance();
        mod.getZombieManager().skipDays(serverWorld, days);
        int newDay = mod.getZombieManager().getDay(serverWorld);
        source.sendFeedback(() -> Text.literal("\u00A7aSaltaste " + days + " dia(s). Ahora es dia " + newDay + "."), true);
        source.sendFeedback(() -> Text.literal("\u00A7eMultiplicador: " + mod.getZombieManager().getMultiplier(newDay) + "x"), false);
        return 1;
    }

    private static int comandoReducirDias(ServerCommandSource source, int days) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        ServerWorld serverWorld = (ServerWorld) player.getWorld();
        MCExtremo mod = MCExtremo.getInstance();
        mod.getZombieManager().reduceDays(serverWorld, days);
        int newDay = mod.getZombieManager().getDay(serverWorld);
        source.sendFeedback(() -> Text.literal("\u00A7aRetrocediste " + days + " dia(s). Ahora es dia " + newDay + "."), true);
        source.sendFeedback(() -> Text.literal("\u00A7eMultiplicador: " + mod.getZombieManager().getMultiplier(newDay) + "x"), false);
        return 1;
    }

    private static int comandoSpawnZombie(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        var pos = player.getBlockPos().offset(player.getHorizontalFacing(), 3);
        ZombieEntity zombie = EntityType.ZOMBIE.create(world);
        if (zombie != null) {
            zombie.setPosition(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            world.spawnEntity(zombie);

            MCExtremo mod = MCExtremo.getInstance();
            int day = mod.getZombieManager().getDay(world);
            mod.getZombieManager().applyScaling(zombie, day);
            zombie.setTarget(player);

            source.sendFeedback(() -> Text.literal("\u00A7aZombie spawneado con inteligencia nivel " + mod.getZombieManager().getIntelligenceName(mod.getZombieManager().getIntelligenceLevel(day))), true);
        }
        return 1;
    }

    private static int comandoZombieInfo(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        Collection<ZombieEntity> zombies = world.getEntitiesByClass(ZombieEntity.class, new Box(player.getBlockPos()).expand(50), e -> true);
        source.sendFeedback(() -> Text.literal("\u00A76=== Zombies en el mundo ==="), false);
        source.sendFeedback(() -> Text.literal("\u00A7eTotal cercanos: \u00A7f" + zombies.size()), false);
        for (ZombieEntity z : zombies) {
            double dist = z.distanceTo(player);
            double hp = z.getHealth();
            double maxHp = z.getMaxHealth();
            source.sendFeedback(() -> Text.literal("\u00A7e  - \u00A7f" + z.getName().getString() +
                " \u00A77(HP: " + (int) hp + "/" + (int) maxHp + ", Dist: " + (int) dist + "m)"), false);
        }
        return 1;
    }

    private static int comandoAyuda(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("\u00A76=== MCExtremo ==="), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce lives \u00A77- Ver tus vidas"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce lives <player> \u00A77- Ver vidas de otro"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce setlives <player> <amount> \u00A77- Establecer vidas"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce addlives <player> <amount> \u00A77- Agregar vidas"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce pvp <on|off> \u00A77- Activar/desactivar PvP"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce revive <player> \u00A77- Revivir jugador"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce status [player] \u00A77- Ver estado"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce dead \u00A77- Listar eliminados"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce upgrade \u00A77- Mejorar equipo o herramienta en mano con fragmentos"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce hearts [player] \u00A77- Ver corazones extra"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce trial \u00A77- Prueba voluntaria para recuperar una vida"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce event start|stop|status \u00A77- Event trial grupal"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce revivearena set|test \u00A77- Configurar/probar arena"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce scoreboard \u00A77- Actualizar scoreboard"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce reload \u00A77- Recargar config"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce skills \u00A77- Abrir arbol de habilidades"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce config \u00A77- Ver/modificar config"), false);
        source.sendFeedback(() -> Text.literal("\u00A76--- Zombies ---"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce days \u00A77- Ver dia actual y nivel"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce skipdays <amount> \u00A77- Saltar dias"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce reducedays <amount> \u00A77- Retroceder dias"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce spawnzombie \u00A77- Spawn zombie mejorado"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce zombieinfo \u00A77- Info zombies cercanos"), false);
        return 1;
    }

    private static int comandoAyudaMce(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("\u00A76=== MCExtremo Shortcuts (/mce) ==="), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce lives [player] \u00A77- Check lives"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce status [player] \u00A77- Check status"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce days \u00A77- Zombie phase and multiplier"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce skills \u00A77- Open skill tree"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce skills info \u00A77- List unlocked skills"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce upgrade \u00A77- Upgrade held gear/tool"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce trial \u00A77- Start life recovery trial"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce event start|stop|status \u00A77- Group event trial"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce hearts [player] \u00A77- Check extra hearts"), false);
        source.sendFeedback(() -> Text.literal("\u00A76--- Admin ---"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce revive <player> \u00A77- Revive player"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce setlives <player> <amount> \u00A77- Set lives"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce addlives <player> <amount> \u00A77- Add lives"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce dead \u00A77- List eliminated players"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce pvp <on|off> \u00A77- Toggle PvP"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce reload \u00A77- Reload config"), false);
        source.sendFeedback(() -> Text.literal("\u00A7e/mce update \u00A77- Check latest GitHub release"), false);
        source.sendFeedback(() -> Text.literal("\u00A78All commands are now under /mce."), false);
        return 1;
    }

    private static int comandoSkills(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        MCExtremo mod = MCExtremo.getInstance();
        if (!ModConfig.get().skillTree.activado) {
            source.sendFeedback(() -> Text.literal("\u00A7cEl sistema de habilidades esta deshabilitado."), false);
            return 0;
        }
        mod.getSkillTreeManager().openSkillTree(player);
        return 1;
    }

    private static int comandoSkillsReset(ServerCommandSource source, String nombre) {
        var player = getOnlinePlayer(source.getServer(), nombre);
        if (player != null) {
            MCExtremo mod = MCExtremo.getInstance();
            mod.getSkillTreeManager().resetSkills(player);
            source.sendFeedback(() -> Text.literal("\u00A7aSkills de " + nombre + " reseteadas."), true);
        } else {
            source.sendFeedback(() -> Text.literal("\u00A7cJugador no encontrado."), false);
        }
        return 1;
    }

    private static int comandoSkillsInfo(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("\u00A7cSolo jugadores."), false);
            return 0;
        }
        MCExtremo mod = MCExtremo.getInstance();
        var unlocked = mod.getSkillTreeManager().getUnlockedSkills(player.getUuid());
        source.sendFeedback(() -> Text.literal("\u00A76=== Tus Habilidades ==="), false);
        source.sendFeedback(() -> Text.literal("\u00A7eDesbloqueadas: \u00A7f" + unlocked.size() + "/"
            + com.egologic.mcextremo.skilltree.Skill.values().length), false);
        source.sendFeedback(() -> Text.literal("\u00A7eNiveles: \u00A7f" + player.experienceLevel), false);
        for (com.egologic.mcextremo.skilltree.Skill skill : com.egologic.mcextremo.skilltree.Skill.values()) {
            boolean has = unlocked.contains(skill.getId());
            String status = has ? "\u00A7a\u2714" : "\u00A7c\u2716";
            source.sendFeedback(() -> Text.literal("  " + status + " " + skill.getDisplayName() +
                (has ? "" : " \u00A77(" + skill.getCost() + " lvl)")), false);
        }
        return 1;
    }

    private static int comandoConfig(ServerCommandSource source, String action, String path) {
        switch (action) {
            case "list" -> {
                source.sendFeedback(() -> Text.literal("\u00A76=== Configuracion MCExtremo ==="), false);
                Map<String, String> allConfig = ModConfig.getAllConfig();
                for (var entry : allConfig.entrySet()) {
                    source.sendFeedback(() -> Text.literal("\u00A7e" + entry.getKey() + ": \u00A7f" + entry.getValue()), false);
                }
            }
            case "get" -> {
                if (path.isEmpty()) {
                    source.sendFeedback(() -> Text.literal("\u00A7cUsa: /mce config get <path>"), false);
                    break;
                }
                String value = ModConfig.getConfigValue(path);
                if (value != null) {
                    source.sendFeedback(() -> Text.literal("\u00A7e" + path + " = \u00A7f" + value), false);
                } else {
                    source.sendFeedback(() -> Text.literal("\u00A7cRuta no encontrada: " + path), false);
                }
            }
        }
        return 1;
    }

    private static int comandoConfigSet(ServerCommandSource source, String path, String value) {
        boolean success = ModConfig.setConfigValue(path, value);
        if (success) {
            reapplyRuntimeConfig(source);
            source.sendFeedback(() -> Text.literal("\u00A7a" + path + " establecido a: " + value), true);
        } else {
            source.sendFeedback(() -> Text.literal("\u00A7cError al establecer " + path + ". Verifica la ruta y el tipo de dato."), false);
        }
        return 1;
    }

    private static int comandoConfigReload(ServerCommandSource source) {
        ModConfig.reload();
        reapplyRuntimeConfig(source);
        source.sendFeedback(() -> Text.literal("\u00A7aConfiguracion recargada desde disco."), true);
        return 1;
    }

    private static int comandoConfigReset(ServerCommandSource source) {
        ModConfig fresh = new ModConfig();
        ModConfig target = ModConfig.get();
        try {
            for (var field : ModConfig.class.getDeclaredFields()) {
                field.setAccessible(true);
                field.set(target, field.get(fresh));
            }
            ModConfig.validate();
            me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
            reapplyRuntimeConfig(source);
            source.sendFeedback(() -> Text.literal("\u00A7aConfiguracion reiniciada a valores por defecto."), true);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("\u00A7cError al reiniciar config: " + e.getMessage()), false);
        }
        return 1;
    }

    private static void reapplyRuntimeConfig(ServerCommandSource source) {
        MCExtremo mod = MCExtremo.getInstance();
        if (mod != null && mod.getHardcoreManager() != null && source.getServer() != null) {
            mod.getHardcoreManager().reapply(source.getServer());
        }
    }

    private static ServerPlayerEntity getOnlinePlayer(net.minecraft.server.MinecraftServer server, String name) {
        ServerPlayerEntity exact = server.getPlayerManager().getPlayer(name);
        if (exact != null) return exact;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }
}
