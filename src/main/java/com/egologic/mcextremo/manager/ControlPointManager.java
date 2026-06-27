package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.util.TextUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class ControlPointManager {
    private static final String DEFENSE_MOB_TAG = "mcextremo_control_point_defense";
    private static final String VILLAGER_TAG = "mcextremo_control_point_villager";
    private static final String REGEN_TAG = "mcextremo_control_point_regeneration";
    private static final String RESISTANCE_TAG = "mcextremo_control_point_resistance";
    private static final String SPEED_TAG = "mcextremo_control_point_speed";
    private static final String ABSORPTION_TAG = "mcextremo_control_point_absorption";
    private static final int OUTPOST_RADIUS = 24;

    private final MCExtremo mod;
    private final Random random = new Random();
    private final Map<String, OutpostBuildTask> buildTasks = new HashMap<>();
    private final Map<String, DefenseEvent> defenseEvents = new HashMap<>();
    private int scanTicks;
    private int buffTicks;
    private int defenseTicks;
    private boolean buildRecoveryChecked;

    public ControlPointManager(MCExtremo mod) {
        this.mod = mod;
    }

    public void tick(MinecraftServer server) {
        if (!ModConfig.get().puntosControl.activado) return;
        ServerWorld world = server.getOverworld();
        if (!buildRecoveryChecked) {
            buildRecoveryChecked = true;
            resumeInterruptedBuilds(world);
        }
        tickBuildTasks(world);
        if (++scanTicks >= 20) {
            scanTicks = 0;
            tickCapture(server, world);
        }
        if (++buffTicks >= ModConfig.get().puntosControl.minutosBuffRenovacion * 60 * 20) {
            buffTicks = 0;
            applyOwnerBuffs(server);
        }
        if (++defenseTicks >= 20) {
            defenseTicks = 0;
            tickDefenseEvents(server, world);
        }
    }

    public void generate(ServerWorld world, BlockPos center) {
        int distance = 180;
        createPoint(world, "norte", center.add(0, 0, -distance));
        createPoint(world, "sur", center.add(0, 0, distance));
        createPoint(world, "este", center.add(distance, 0, 0));
        createPoint(world, "oeste", center.add(-distance, 0, 0));
        mod.getDataManager().save();
    }

    public void showInfo(ServerPlayerEntity player) {
        player.sendMessage(TextUtil.literal("&6=== Puntos de Control ==="), false);
        for (Map.Entry<String, DataManager.ControlPointState> entry : mod.getDataManager().getControlPoints().entrySet()) {
            UUID owner = entry.getValue().ownerUuid();
            String ownerName = owner == null ? "Sin dueno" : owner.toString();
            ServerPlayerEntity ownerPlayer = player.getServer().getPlayerManager().getPlayer(owner);
            if (ownerPlayer != null) ownerName = ownerPlayer.getName().getString();
            String built = entry.getValue().outpostBuilt ? " &aAsentamiento" : " &7Sin asentamiento";
            player.sendMessage(TextUtil.literal("&e⚑ " + displayName(entry.getKey()) + ": &f" + ownerName
                + " &7(" + entry.getValue().x + ", " + entry.getValue().y + ", " + entry.getValue().z + ")" + built), false);
        }
    }

    public void onJoin(ServerPlayerEntity player) {
        applyControlPointBuffs(player, getOwnedCount(player.getUuid()));
    }

    public boolean forceDefense(MinecraftServer server, String id) {
        DataManager.ControlPointState point = mod.getDataManager().getControlPoints().get(id);
        if (point == null || point.ownerUuid() == null || defenseEvents.containsKey(id)) return false;
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(point.ownerUuid());
        if (owner == null) return false;
        startDefenseEvent(server, server.getOverworld(), id, point, owner);
        return true;
    }

    public boolean rebuildOutpost(ServerWorld world, String id) {
        DataManager.ControlPointState point = mod.getDataManager().getControlPoints().get(id);
        if (point == null || point.ownerUuid() == null) return false;
        startOutpostBuild(world, id, point);
        return true;
    }

    public int getOwnedCount(UUID owner) {
        int count = 0;
        for (DataManager.ControlPointState state : mod.getDataManager().getControlPoints().values()) {
            if (owner.equals(state.ownerUuid())) count++;
        }
        return count;
    }

    public String scoreboardLine(ServerPlayerEntity viewer) {
        if (mod.getDataManager().getControlPoints().isEmpty()) return "\u00A78Sin puntos";
        return "\u00A7e" + getOwnedCount(viewer.getUuid()) + "\u00A77/4 tuyos";
    }

    private void createPoint(ServerWorld world, String id, BlockPos around) {
        BlockPos ground = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, around);
        BlockPos base = ground.down();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.setBlockState(base.add(dx, 0, dz), Blocks.OBSIDIAN.getDefaultState());
                world.setBlockState(base.add(dx, 1, dz), Blocks.AIR.getDefaultState());
            }
        }
        for (int y = 1; y <= 3; y++) {
            world.setBlockState(base.add(0, y, 0), Blocks.CRYING_OBSIDIAN.getDefaultState());
        }
        world.setBlockState(base.add(0, 4, 0), Blocks.BEACON.getDefaultState());
        world.setBlockState(base.add(0, 5, 0), Blocks.WHITE_STAINED_GLASS.getDefaultState());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                    world.setBlockState(base.add(dx, 1, dz), Blocks.BARRIER.getDefaultState());
                }
            }
        }
        mod.getDataManager().setControlPoint(id, new DataManager.ControlPointState(null, base.getX(), base.getY(), base.getZ()));
    }

    private void tickCapture(MinecraftServer server, ServerWorld world) {
        int radius = ModConfig.get().puntosControl.radioCaptura;
        int required = ModConfig.get().puntosControl.ticksParaCapturar;
        for (Map.Entry<String, DataManager.ControlPointState> entry : mod.getDataManager().getControlPoints().entrySet()) {
            String id = entry.getKey();
            DataManager.ControlPointState point = entry.getValue();
            BlockPos pos = new BlockPos(point.x, point.y, point.z);
            ServerPlayerEntity candidate = null;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getWorld() != world || player.isCreative() || player.isSpectator()) continue;
                if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) > radius * radius) continue;
                UUID owner = point.ownerUuid();
                if (owner != null && owner.equals(player.getUuid())) continue;
                if (owner != null && ModConfig.get().puntosControl.soloCapturaEnPvP && !mod.getPvpScheduler().isPvpEnabled()) continue;
                candidate = player;
                break;
            }
            if (candidate == null) {
                mod.getDataManager().setControlPointCapture(id, null);
                continue;
            }
            if (ModConfig.get().puntosControl.pausarCapturaConEnemigos && hasHostilesNearPoint(world, pos, ModConfig.get().puntosControl.radioEnemigosCaptura)) {
                candidate.sendMessage(Text.literal("\u00A7cCaptura pausada: elimina los enemigos cercanos al punto."), true);
                continue;
            }
            DataManager.ControlPointCaptureState capture = mod.getDataManager().getControlPointCapture().get(id);
            int progress = capture != null && candidate.getUuid().equals(capture.capturingUuid()) ? capture.progress + 20 : 20;
            candidate.sendMessage(Text.literal("\u00A7eCapturando " + displayName(id) + " \u00A77[" + Math.min(progress, required) + "/" + required + "]"), true);
            if (progress >= required) {
                UUID previousOwner = point.ownerUuid();
                point.owner = candidate.getUuid().toString();
                point.nextDefenseEventAt = System.currentTimeMillis() + ModConfig.get().puntosControl.minutosEntreEventosDefensa * 60_000L;
                mod.getDataManager().setControlPoint(id, point);
                updateBeaconColor(world, point);
                startOutpostBuild(world, id, point);
                mod.getDataManager().setControlPointCapture(id, null);
                mod.getDailyMissionManager().onControlPointCaptured(candidate);
                if (previousOwner != null && !previousOwner.equals(candidate.getUuid())) {
                    refreshOwnerBuffs(server, previousOwner);
                }
                refreshOwnerBuffs(server, candidate.getUuid());
                broadcast(server, "&6⚑ &e" + candidate.getName().getString() + " capturo el punto " + displayName(id) + ". &7El asentamiento se esta levantando.");
            } else {
                mod.getDataManager().setControlPointCapture(id, new DataManager.ControlPointCaptureState(candidate.getUuid(), progress));
            }
        }
    }

    private void tickBuildTasks(ServerWorld world) {
        Iterator<Map.Entry<String, OutpostBuildTask>> iterator = buildTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, OutpostBuildTask> entry = iterator.next();
            OutpostBuildTask task = entry.getValue();
            int placed = 0;
            while (placed++ < 24 && !task.blocks.isEmpty()) {
                OutpostBlock block = task.blocks.poll();
                world.setBlockState(block.pos, block.state, 3);
                if (task.ticks % 8 == 0) {
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, block.pos.getX() + 0.5, block.pos.getY() + 1.05, block.pos.getZ() + 0.5, 1, 0.15, 0.2, 0.15, 0.01);
                }
            }
            if (task.ticks % 20 == 0) {
                world.playSound(null, task.center, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.7f, 0.85f + random.nextFloat() * 0.25f);
                world.spawnParticles(ParticleTypes.CLOUD, task.center.getX() + 0.5, task.center.getY() + 2.0, task.center.getZ() + 0.5, 16, 7.0, 1.2, 7.0, 0.02);
            }
            task.ticks++;
            if (task.blocks.isEmpty()) {
                finishOutpost(world, entry.getKey(), task);
                iterator.remove();
            }
        }
    }

    private void resumeInterruptedBuilds(ServerWorld world) {
        for (Map.Entry<String, DataManager.ControlPointState> entry : mod.getDataManager().getControlPoints().entrySet()) {
            DataManager.ControlPointState point = entry.getValue();
            if (point.ownerUuid() != null && !point.outpostBuilt && !buildTasks.containsKey(entry.getKey())) {
                startOutpostBuild(world, entry.getKey(), point);
            }
        }
    }

    private void startOutpostBuild(ServerWorld world, String id, DataManager.ControlPointState point) {
        BlockPos center = new BlockPos(point.x, point.y, point.z);
        OutpostBuildTask task = new OutpostBuildTask(center);
        queueOutpostBlocks(task, world, center);
        buildTasks.put(id, task);
        if (point.outpostBuilt) {
            point.outpostBuilt = false;
            mod.getDataManager().setControlPoint(id, point);
        }
        world.playSound(null, center, SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1.4f, 1.0f);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, center.getX() + 0.5, center.getY() + 3.0, center.getZ() + 0.5, 60, 4.0, 2.0, 4.0, 0.12);
    }

    private void queueOutpostBlocks(OutpostBuildTask task, ServerWorld world, BlockPos center) {
        BlockState stone = Blocks.STONE_BRICKS.getDefaultState();
        BlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        BlockState plank = Blocks.OAK_PLANKS.getDefaultState();
        BlockState log = Blocks.OAK_LOG.getDefaultState();
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();

        queueTerrainPreparation(task, world, center);
        for (int dx = -OUTPOST_RADIUS; dx <= OUTPOST_RADIUS; dx++) {
            for (int dz = -OUTPOST_RADIUS; dz <= OUTPOST_RADIUS; dz++) {
                int max = Math.max(Math.abs(dx), Math.abs(dz));
                BlockPos pos = center.add(dx, 1, dz);
                if (max <= OUTPOST_RADIUS - 2 && (Math.abs(dx) > 3 || Math.abs(dz) > 3)) {
                    boolean mainRoad = Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
                    boolean diagonalRoad = Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1 && max <= 15;
                    if (mainRoad || diagonalRoad) {
                        task.add(pos, ((dx + dz) & 1) == 0 ? stone : cobble);
                    } else {
                        int noise = Math.floorMod(dx * 11 + dz * 17, 5);
                        task.add(pos, noise == 0 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.GRAVEL.getDefaultState());
                    }
                }
                boolean gate = Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
                if (max == OUTPOST_RADIUS && !gate) {
                    task.add(center.add(dx, 2, dz), stone);
                    task.add(center.add(dx, 3, dz), (dx + dz) % 3 == 0 ? mossy : stone);
                    if ((dx * 17 + dz * 7) % 5 == 0) task.add(center.add(dx, 4, dz), Blocks.COBBLESTONE_WALL.getDefaultState());
                }
            }
        }

        buildCentralPlaza(task, center);
        buildClockTower(task, center.add(0, 1, -8));
        buildMedievalHouse(task, center.add(9, 1, 8), 7, 6, 5);
        buildMedievalHouse(task, center.add(-17, 1, 8), 7, 6, 6);
        buildMedievalHouse(task, center.add(9, 1, -18), 6, 7, 5);
        buildMedievalHouse(task, center.add(-18, 1, -17), 6, 6, 5);
        buildChapel(task, center.add(-5, 1, -14));
        buildTavern(task, center.add(12, 1, -10));
        buildWorkshop(task, center.add(-4, 1, 17));
        buildBlacksmith(task, center.add(4, 1, 17));
        buildManor(task, center.add(-4, 1, -21));
        buildLibrary(task, center.add(14, 1, -3));
        buildWell(task, center.add(-8, 1, -7));
        buildFountain(task, center.add(6, 1, -7));
        buildMarket(task, center.add(9, 1, 18));
        buildMarketStall(task, center.add(-14, 1, 18));
        buildMarketStall(task, center.add(14, 1, 15));
        buildGarden(task, center.add(15, 1, -5));
        buildGarden(task, center.add(-20, 1, -2));
        buildFarm(task, center.add(-18, 1, -8));
        buildAnimalPen(task, center.add(18, 1, -10));
        buildStorage(task, center.add(-12, 1, 18));
        buildBakery(task, center.add(-10, 1, 10));
        buildVillageGreen(task, center.add(18, 1, 12));
        buildGatehouses(task, center);
        buildGuardPosts(task, center);
        buildDecor(task, center);
        buildStreetDetails(task, center);
        buildBushes(task, center);
        buildLampPosts(task, center);
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8.0;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * 16);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * 16);
            task.add(new BlockPos(x, center.getY() + 2, z), Blocks.SPRUCE_FENCE.getDefaultState());
            task.add(new BlockPos(x, center.getY() + 3, z), Blocks.TORCH.getDefaultState());
        }
    }

    private void queueTerrainPreparation(OutpostBuildTask task, ServerWorld world, BlockPos center) {
        for (int dx = -OUTPOST_RADIUS; dx <= OUTPOST_RADIUS; dx++) {
            for (int dz = -OUTPOST_RADIUS; dz <= OUTPOST_RADIUS; dz++) {
                if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) continue;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int terrainTop = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                int clearTop = Math.min(center.getY() + 32, Math.max(center.getY() + 14, terrainTop + 1));
                for (int y = center.getY() + 2; y <= clearTop; y++) {
                    task.add(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                }
                for (int y = center.getY() - 4; y <= center.getY(); y++) {
                    BlockState foundation = y == center.getY()
                        ? Blocks.STONE_BRICKS.getDefaultState()
                        : Blocks.COBBLESTONE.getDefaultState();
                    task.add(new BlockPos(x, y, z), foundation);
                }
            }
        }
    }

    private boolean hasHostilesNearPoint(ServerWorld world, BlockPos pos, int radius) {
        Box box = new Box(pos).expand(radius, 10, radius);
        return !world.getEntitiesByClass(HostileEntity.class, box, hostile ->
            hostile.isAlive() && !hostile.isRemoved()
        ).isEmpty();
    }

    private void buildCentralPlaza(OutpostBuildTask task, BlockPos center) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist <= 7 && (Math.abs(dx) > 3 || Math.abs(dz) > 3)) {
                    task.add(center.add(dx, 1, dz), dist % 2 == 0 ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState());
                }
            }
        }
        for (int dx : new int[]{-5, 5}) {
            for (int dz : new int[]{-5, 5}) {
                task.add(center.add(dx, 2, dz), Blocks.OAK_FENCE.getDefaultState());
                task.add(center.add(dx, 3, dz), Blocks.LANTERN.getDefaultState());
            }
        }
    }

    private void buildHouse(OutpostBuildTask task, BlockPos origin, int width, int depth, BlockState plank, BlockState log, BlockState glass) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                task.add(origin.add(x, 0, z), Blocks.SPRUCE_PLANKS.getDefaultState());
                boolean wall = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
                if (!wall) continue;
                for (int y = 1; y <= 3; y++) {
                    boolean door = z == 0 && x == width / 2 && y <= 2;
                    if (door) {
                        task.add(origin.add(x, y, z), Blocks.AIR.getDefaultState());
                    } else if (y == 2 && (x == width / 2 || z == depth / 2)) {
                        task.add(origin.add(x, y, z), glass);
                    } else {
                        task.add(origin.add(x, y, z), (x == 0 || x == width - 1) && (z == 0 || z == depth - 1) ? log : plank);
                    }
                }
            }
        }
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= depth; z++) {
                task.add(origin.add(x, 4, z), Blocks.DARK_OAK_SLAB.getDefaultState());
            }
        }
    }

    private void buildMedievalHouse(OutpostBuildTask task, BlockPos origin, int width, int depth, int wallHeight) {
        BlockState frame = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
        BlockState wall = Blocks.OAK_PLANKS.getDefaultState();
        BlockState trim = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState window = Blocks.GLASS_PANE.getDefaultState();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                task.add(origin.add(x, 0, z), Blocks.SPRUCE_PLANKS.getDefaultState());
                boolean edge = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
                if (!edge) continue;
                for (int y = 1; y <= wallHeight; y++) {
                    boolean corner = (x == 0 || x == width - 1) && (z == 0 || z == depth - 1);
                    boolean door = z == 0 && x == width / 2 && y <= 2;
                    boolean windowSlot = y == 3 && (x == width / 2 || z == depth / 2);
                    if (door) {
                        task.add(origin.add(x, y, z), Blocks.AIR.getDefaultState());
                    } else if (windowSlot && !corner) {
                        task.add(origin.add(x, y, z), window);
                    } else if (corner || y == wallHeight || y == 1) {
                        task.add(origin.add(x, y, z), frame);
                    } else {
                        task.add(origin.add(x, y, z), wall);
                    }
                }
            }
        }
        for (int y = 2; y <= wallHeight - 1; y += 2) {
            task.add(origin.add(-1, y, depth / 2), Blocks.DARK_OAK_FENCE.getDefaultState());
            task.add(origin.add(width, y, depth / 2), Blocks.DARK_OAK_FENCE.getDefaultState());
        }
        for (int x = -1; x <= width; x++) {
            task.add(origin.add(x, wallHeight + 1, -1), Blocks.DARK_OAK_PLANKS.getDefaultState());
            task.add(origin.add(x, wallHeight + 1, depth), Blocks.DARK_OAK_PLANKS.getDefaultState());
        }
        for (int z = 0; z < depth; z++) {
            task.add(origin.add(-1, wallHeight + 1, z), Blocks.DARK_OAK_PLANKS.getDefaultState());
            task.add(origin.add(width, wallHeight + 1, z), Blocks.DARK_OAK_PLANKS.getDefaultState());
        }
        for (int step = 0; step <= Math.max(width, depth) / 2; step++) {
            int y = wallHeight + 1 + step;
            for (int x = -1 + step; x <= width - step; x++) {
                task.add(origin.add(x, y, -1 + step), step % 2 == 0 ? Blocks.ORANGE_TERRACOTTA.getDefaultState() : Blocks.BROWN_TERRACOTTA.getDefaultState());
                task.add(origin.add(x, y, depth - step), step % 2 == 0 ? Blocks.ORANGE_TERRACOTTA.getDefaultState() : Blocks.BROWN_TERRACOTTA.getDefaultState());
            }
            for (int z = step; z <= depth - 1 - step; z++) {
                task.add(origin.add(-1 + step, y, z), Blocks.DARK_OAK_PLANKS.getDefaultState());
                task.add(origin.add(width - step, y, z), Blocks.DARK_OAK_PLANKS.getDefaultState());
            }
            if (step >= depth / 2) break;
        }
        for (int x = 0; x < width; x++) {
            task.add(origin.add(x, wallHeight + 2, depth / 2), trim);
        }
        task.add(origin.add(width - 2, wallHeight + 3, depth - 2), Blocks.BRICKS.getDefaultState());
        task.add(origin.add(width - 2, wallHeight + 4, depth - 2), Blocks.BRICKS.getDefaultState());
        task.add(origin.add(width - 2, wallHeight + 5, depth - 2), Blocks.CAMPFIRE.getDefaultState());
        if (width >= 7) {
            task.add(origin.add(2, wallHeight + 3, depth / 2), Blocks.GLASS_PANE.getDefaultState());
            task.add(origin.add(width - 3, wallHeight + 3, depth / 2), Blocks.GLASS_PANE.getDefaultState());
        }
        task.add(origin.add(width / 2, 1, -1), Blocks.LANTERN.getDefaultState());
    }

    private void buildManor(OutpostBuildTask task, BlockPos origin) {
        buildMedievalHouse(task, origin, 9, 7, 6);
        for (int y = 1; y <= 9; y++) {
            task.add(origin.add(4, y, 3), y == 9 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.DARK_OAK_PLANKS.getDefaultState());
        }
        task.add(origin.add(4, 10, 3), Blocks.LANTERN.getDefaultState());
    }

    private void buildWorkshop(OutpostBuildTask task, BlockPos origin) {
        buildMedievalHouse(task, origin, 8, 5, 4);
        task.add(origin.add(1, 1, -1), Blocks.SMITHING_TABLE.getDefaultState());
        task.add(origin.add(2, 1, -1), Blocks.BLAST_FURNACE.getDefaultState());
        task.add(origin.add(3, 1, -1), Blocks.ANVIL.getDefaultState());
        task.add(origin.add(6, 1, -1), Blocks.BARREL.getDefaultState());
    }

    private void buildWell(OutpostBuildTask task, BlockPos origin) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                task.add(origin.add(dx, 0, dz), Blocks.COBBLESTONE.getDefaultState());
            }
        }
        task.add(origin, Blocks.WATER.getDefaultState());
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                task.add(origin.add(dx, 1, dz), Blocks.OAK_FENCE.getDefaultState());
                task.add(origin.add(dx, 2, dz), Blocks.OAK_FENCE.getDefaultState());
            }
        }
        task.add(origin.add(0, 3, 0), Blocks.OAK_SLAB.getDefaultState());
    }

    private void buildMarket(OutpostBuildTask task, BlockPos origin) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                task.add(origin.add(dx, 0, dz), Blocks.OAK_PLANKS.getDefaultState());
                if (Math.abs(dx) == 2 && Math.abs(dz) == 1) {
                    task.add(origin.add(dx, 1, dz), Blocks.OAK_FENCE.getDefaultState());
                    task.add(origin.add(dx, 2, dz), Blocks.OAK_FENCE.getDefaultState());
                }
                task.add(origin.add(dx, 3, dz), (dx + dz) % 2 == 0 ? Blocks.RED_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState());
            }
        }
        task.add(origin.add(0, 1, 0), Blocks.BELL.getDefaultState());
    }

    private void buildGarden(OutpostBuildTask task, BlockPos origin) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean border = Math.abs(dx) == 3 || Math.abs(dz) == 2;
                task.add(origin.add(dx, 0, dz), border ? Blocks.OAK_SLAB.getDefaultState() : Blocks.FARMLAND.getDefaultState());
                if (!border && ((dx + dz) & 1) == 0) {
                    task.add(origin.add(dx, 1, dz), Blocks.WHEAT.getDefaultState());
                }
            }
        }
        task.add(origin.add(0, 0, 0), Blocks.WATER.getDefaultState());
        task.add(origin.add(-4, 1, 0), Blocks.COMPOSTER.getDefaultState());
        task.add(origin.add(4, 1, 0), Blocks.HAY_BLOCK.getDefaultState());
    }

    private void buildStorage(OutpostBuildTask task, BlockPos origin) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                task.add(origin.add(dx, 0, dz), Blocks.COBBLESTONE.getDefaultState());
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    task.add(origin.add(dx, 1, dz), Blocks.OAK_FENCE.getDefaultState());
                }
            }
        }
        task.add(origin.add(-1, 1, 0), Blocks.BARREL.getDefaultState());
        task.add(origin.add(0, 1, 0), Blocks.CHEST.getDefaultState());
        task.add(origin.add(1, 1, 0), Blocks.CRAFTING_TABLE.getDefaultState());
        task.add(origin.add(0, 1, 1), Blocks.HAY_BLOCK.getDefaultState());
    }

    private void buildGuardPosts(OutpostBuildTask task, BlockPos center) {
        int[][] posts = {{17, 17}, {-17, 17}, {17, -17}, {-17, -17}};
        for (int[] post : posts) {
            BlockPos origin = center.add(post[0], 1, post[1]);
            for (int y = 0; y <= 4; y++) {
                task.add(origin.add(0, y, 0), y == 4 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState());
            }
            task.add(origin.add(1, 2, 0), Blocks.COBBLESTONE_WALL.getDefaultState());
            task.add(origin.add(-1, 2, 0), Blocks.COBBLESTONE_WALL.getDefaultState());
            task.add(origin.add(0, 2, 1), Blocks.COBBLESTONE_WALL.getDefaultState());
            task.add(origin.add(0, 2, -1), Blocks.COBBLESTONE_WALL.getDefaultState());
            task.add(origin.add(0, 5, 0), Blocks.TORCH.getDefaultState());
        }
    }

    private void buildGatehouses(OutpostBuildTask task, BlockPos center) {
        buildGatehouse(task, center.add(0, 1, OUTPOST_RADIUS), true);
        buildGatehouse(task, center.add(0, 1, -OUTPOST_RADIUS), true);
        buildGatehouse(task, center.add(OUTPOST_RADIUS, 1, 0), false);
        buildGatehouse(task, center.add(-OUTPOST_RADIUS, 1, 0), false);
    }

    private void buildGatehouse(OutpostBuildTask task, BlockPos origin, boolean eastWestOpening) {
        for (int a = -3; a <= 3; a++) {
            for (int y = 1; y <= 5; y++) {
                boolean opening = Math.abs(a) <= 1 && y <= 3;
                BlockPos left = eastWestOpening ? origin.add(a, y, -1) : origin.add(-1, y, a);
                BlockPos right = eastWestOpening ? origin.add(a, y, 1) : origin.add(1, y, a);
                if (!opening) {
                    task.add(left, y == 5 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState());
                    task.add(right, y == 5 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState());
                }
            }
            BlockPos cap = eastWestOpening ? origin.add(a, 6, 0) : origin.add(0, 6, a);
            task.add(cap, Math.abs(a) % 2 == 0 ? Blocks.DARK_OAK_PLANKS.getDefaultState() : Blocks.ORANGE_TERRACOTTA.getDefaultState());
        }
        task.add(origin.add(0, 4, 0), Blocks.LANTERN.getDefaultState());
    }

    private void buildVillageGreen(OutpostBuildTask task, BlockPos origin) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int dist = Math.abs(dx) + Math.abs(dz);
                task.add(origin.add(dx, 0, dz), dist <= 5 ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState());
                if (dist == 5) task.add(origin.add(dx, 1, dz), Blocks.OAK_SLAB.getDefaultState());
            }
        }
        task.add(origin.add(0, 1, 0), Blocks.OAK_LOG.getDefaultState());
        task.add(origin.add(0, 2, 0), Blocks.OAK_LOG.getDefaultState());
        task.add(origin.add(0, 3, 0), Blocks.OAK_LOG.getDefaultState());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    task.add(origin.add(dx, 4, dz), Blocks.OAK_LEAVES.getDefaultState());
                    if (Math.abs(dx) + Math.abs(dz) <= 2) task.add(origin.add(dx, 5, dz), Blocks.OAK_LEAVES.getDefaultState());
                }
            }
        }
        task.add(origin.add(-3, 1, -3), Blocks.BEEHIVE.getDefaultState());
        task.add(origin.add(3, 1, 3), Blocks.COMPOSTER.getDefaultState());
    }

    private void buildDecor(OutpostBuildTask task, BlockPos center) {
        int[][] decor = {
            {6, 0}, {-6, 0}, {0, 6}, {0, -6}, {14, 3}, {-13, 4}, {4, -13}, {-5, 13}, {11, 12}, {-11, -9}
        };
        for (int i = 0; i < decor.length; i++) {
            BlockPos pos = center.add(decor[i][0], 1, decor[i][1]);
            switch (i % 5) {
                case 0 -> {
                    task.add(pos, Blocks.OAK_FENCE.getDefaultState());
                    task.add(pos.up(), Blocks.TORCH.getDefaultState());
                }
                case 1 -> {
                    task.add(pos, Blocks.BARREL.getDefaultState());
                    task.add(pos.east(), Blocks.HAY_BLOCK.getDefaultState());
                }
                case 2 -> {
                    task.add(pos, Blocks.CAMPFIRE.getDefaultState());
                    task.add(pos.north(), Blocks.OAK_SLAB.getDefaultState());
                    task.add(pos.south(), Blocks.OAK_SLAB.getDefaultState());
                }
                case 3 -> {
                    task.add(pos, Blocks.SMITHING_TABLE.getDefaultState());
                    task.add(pos.west(), Blocks.STONECUTTER.getDefaultState());
                }
                default -> {
                    task.add(pos, Blocks.POTTED_DANDELION.getDefaultState());
                    task.add(pos.east(), Blocks.BOOKSHELF.getDefaultState());
                }
            }
        }
    }

    private void buildStreetDetails(OutpostBuildTask task, BlockPos center) {
        int[][] lamps = {
            {7, 7}, {-7, 7}, {7, -7}, {-7, -7}, {18, 0}, {-18, 0}, {0, 18}, {0, -18}
        };
        for (int[] lamp : lamps) {
            BlockPos pos = center.add(lamp[0], 1, lamp[1]);
            task.add(pos, Blocks.COBBLESTONE.getDefaultState());
            task.add(pos.up(), Blocks.SPRUCE_FENCE.getDefaultState());
            task.add(pos.up(2), Blocks.SPRUCE_FENCE.getDefaultState());
            task.add(pos.up(3), Blocks.LANTERN.getDefaultState());
        }

        int[][] benches = {
            {5, 12}, {-6, 11}, {12, -7}, {-12, -6}, {15, 9}, {-16, 11}
        };
        for (int[] bench : benches) {
            BlockPos pos = center.add(bench[0], 1, bench[1]);
            task.add(pos, Blocks.DARK_OAK_SLAB.getDefaultState());
            task.add(pos.east(), Blocks.DARK_OAK_SLAB.getDefaultState());
            task.add(pos.west(), Blocks.DARK_OAK_SIGN.getDefaultState());
        }

        int[][] planters = {
            {3, 15}, {-3, 15}, {15, 3}, {15, -3}, {-15, 3}, {-15, -3}, {3, -15}, {-3, -15}
        };
        for (int[] planter : planters) {
            BlockPos pos = center.add(planter[0], 1, planter[1]);
            task.add(pos, Blocks.DIRT.getDefaultState());
            task.add(pos.up(), ((planter[0] + planter[1]) & 1) == 0 ? Blocks.POPPY.getDefaultState() : Blocks.DANDELION.getDefaultState());
            task.add(pos.north(), Blocks.OAK_SLAB.getDefaultState());
            task.add(pos.south(), Blocks.OAK_SLAB.getDefaultState());
        }

        for (int i = -20; i <= 20; i += 4) {
            if (Math.abs(i) <= 4) continue;
            task.add(center.add(i, 1, 2), Blocks.ANDESITE.getDefaultState());
            task.add(center.add(i, 1, -2), Blocks.ANDESITE.getDefaultState());
            task.add(center.add(2, 1, i), Blocks.ANDESITE.getDefaultState());
            task.add(center.add(-2, 1, i), Blocks.ANDESITE.getDefaultState());
        }
    }

    private void buildClockTower(OutpostBuildTask task, BlockPos origin) {
        BlockState stone = Blocks.STONE_BRICKS.getDefaultState();
        BlockState dark = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();
        // Base 5x5
        for (int y = 0; y <= 12; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                    boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                    if (y == 0) {
                        task.add(origin.add(x, y, z), Blocks.STONE_BRICK_SLAB.getDefaultState());
                    } else if (corner) {
                        task.add(origin.add(x, y, z), y <= 10 ? stone : Blocks.COBBLESTONE_WALL.getDefaultState());
                    } else if (edge) {
                        if (y <= 10) {
                            task.add(origin.add(x, y, z), stone);
                        } else {
                            task.add(origin.add(x, y, z), Blocks.COBBLESTONE_WALL.getDefaultState());
                        }
                    } else if (y == 0) {
                        task.add(origin.add(x, y, z), Blocks.STONE_BRICK_SLAB.getDefaultState());
                    }
                }
            }
            // Clock face on 4 sides
            if (y == 8) {
                task.add(origin.add(0, y, -3), glass);
                task.add(origin.add(0, y, 3), glass);
                task.add(origin.add(-3, y, 0), glass);
                task.add(origin.add(3, y, 0), glass);
            }
            if (y == 9) {
                task.add(origin.add(-1, y, -3), glass);
                task.add(origin.add(1, y, -3), glass);
                task.add(origin.add(-1, y, 3), glass);
                task.add(origin.add(1, y, 3), glass);
                task.add(origin.add(-3, y, -1), glass);
                task.add(origin.add(-3, y, 1), glass);
                task.add(origin.add(3, y, -1), glass);
                task.add(origin.add(3, y, 1), glass);
            }
        }
        // Spire
        task.add(origin.add(0, 13, 0), Blocks.DARK_OAK_LOG.getDefaultState());
        task.add(origin.add(0, 14, 0), Blocks.DARK_OAK_LOG.getDefaultState());
        task.add(origin.add(0, 15, 0), Blocks.DARK_OAK_LOG.getDefaultState());
        task.add(origin.add(0, 16, 0), Blocks.STONE_BRICKS.getDefaultState());
        task.add(origin.add(0, 17, 0), Blocks.POINTED_DRIPSTONE.getDefaultState());
        // Bells
        task.add(origin.add(0, 11, 0), Blocks.BELL.getDefaultState());
        // Door
        task.add(origin.add(0, 1, -3), Blocks.AIR.getDefaultState());
        task.add(origin.add(0, 2, -3), Blocks.AIR.getDefaultState());
        // Lantern
        task.add(origin.add(0, 3, -3), Blocks.LANTERN.getDefaultState());
        // Base decoration
        task.add(origin.add(-2, 1, -3), Blocks.STONE_BRICK_SLAB.getDefaultState());
        task.add(origin.add(2, 1, -3), Blocks.STONE_BRICK_SLAB.getDefaultState());
    }

    private void buildChapel(OutpostBuildTask task, BlockPos origin) {
        BlockState stone = Blocks.STONE_BRICKS.getDefaultState();
        BlockState plank = Blocks.OAK_PLANKS.getDefaultState();
        BlockState dark = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();
        // 7x9 base
        for (int x = -3; x <= 3; x++) {
            for (int z = -4; z <= 4; z++) {
                task.add(origin.add(x, 0, z), Blocks.STONE_BRICK_SLAB.getDefaultState());
                boolean wall = Math.abs(x) == 3 || Math.abs(z) == 4;
                if (!wall) continue;
                for (int y = 1; y <= 6; y++) {
                    boolean door = z == -4 && Math.abs(x) <= 1 && y <= 3;
                    boolean window = y == 4 && (x == -3 || x == 3 || z == -4 || z == 4);
                    if (door) {
                        task.add(origin.add(x, y, z), Blocks.AIR.getDefaultState());
                    } else if (window && (Math.abs(x) == 3 || Math.abs(z) == 4)) {
                        task.add(origin.add(x, y, z), glass);
                    } else {
                        task.add(origin.add(x, y, z), stone);
                    }
                }
            }
        }
        // Cross on front
        task.add(origin.add(0, 7, -4), Blocks.GOLD_BLOCK.getDefaultState());
        task.add(origin.add(0, 8, -4), Blocks.GOLD_BLOCK.getDefaultState());
        task.add(origin.add(-1, 8, -4), Blocks.GOLD_BLOCK.getDefaultState());
        task.add(origin.add(1, 8, -4), Blocks.GOLD_BLOCK.getDefaultState());
        // Roof
        for (int step = 0; step <= 4; step++) {
            int y = 7 + step;
            for (int x = -3 + step; x <= 3 - step; x++) {
                task.add(origin.add(x, y, -4 + step), dark);
                task.add(origin.add(x, y, 4 - step), dark);
            }
            for (int z = -3 + step; z <= 4 - step; z++) {
                task.add(origin.add(-3 + step, y, z), dark);
                task.add(origin.add(3 - step, y, z), dark);
            }
        }
        // Bell tower
        task.add(origin.add(0, 11, 0), Blocks.BELL.getDefaultState());
        task.add(origin.add(0, 12, 0), Blocks.LANTERN.getDefaultState());
        // Altar
        task.add(origin.add(0, 1, 3), Blocks.STONE_BRICKS.getDefaultState());
        task.add(origin.add(0, 2, 3), Blocks.RED_CARPET.getDefaultState());
        // Pews
        for (int z = -2; z <= 1; z++) {
            task.add(origin.add(-1, 1, z), Blocks.OAK_STAIRS.getDefaultState());
            task.add(origin.add(1, 1, z), Blocks.OAK_STAIRS.getDefaultState());
        }
    }

    private void buildTavern(OutpostBuildTask task, BlockPos origin) {
        BlockState frame = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
        BlockState wall = Blocks.OAK_PLANKS.getDefaultState();
        BlockState dark = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();
        // 8x6 base, 2 stories
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 6; z++) {
                task.add(origin.add(x, 0, z), Blocks.SPRUCE_PLANKS.getDefaultState());
                boolean edge = x == 0 || z == 0 || x == 7 || z == 5;
                if (!edge) continue;
                for (int y = 1; y <= 5; y++) {
                    boolean corner = (x == 0 || x == 7) && (z == 0 || z == 5);
                    boolean door = z == 0 && (x == 3 || x == 4) && y <= 2;
                    boolean window = y == 3 && (x == 2 || x == 5 || z == 2 || z == 3);
                    if (door) {
                        task.add(origin.add(x, y, z), Blocks.AIR.getDefaultState());
                    } else if (window && !corner) {
                        task.add(origin.add(x, y, z), glass);
                    } else if (corner || y == 1 || y == 5) {
                        task.add(origin.add(x, y, z), frame);
                    } else {
                        task.add(origin.add(x, y, z), wall);
                    }
                }
            }
        }
        // Roof
        for (int step = 0; step <= 3; step++) {
            int y = 6 + step;
            for (int x = -1 + step; x <= 8 - step; x++) {
                task.add(origin.add(x, y, -1), dark);
                task.add(origin.add(x, y, 6), dark);
            }
            for (int z = 0; z <= 5; z++) {
                task.add(origin.add(-1 + step, y, z), dark);
                task.add(origin.add(8 - step, y, z), dark);
            }
        }
        // Sign
        task.add(origin.add(-1, 3, 0), Blocks.OAK_FENCE.getDefaultState());
        task.add(origin.add(-2, 3, 0), Blocks.OAK_SIGN.getDefaultState());
        // Interior
        task.add(origin.add(1, 1, 2), Blocks.BARREL.getDefaultState());
        task.add(origin.add(2, 1, 2), Blocks.BREWING_STAND.getDefaultState());
        task.add(origin.add(5, 1, 1), Blocks.CRAFTING_TABLE.getDefaultState());
        // Campfire outside
        task.add(origin.add(4, 1, -1), Blocks.CAMPFIRE.getDefaultState());
        // Lantern
        task.add(origin.add(3, 3, -1), Blocks.LANTERN.getDefaultState());
    }

    private void buildBlacksmith(OutpostBuildTask task, BlockPos origin) {
        BlockState stone = Blocks.STONE_BRICKS.getDefaultState();
        BlockState dark = Blocks.DARK_OAK_PLANKS.getDefaultState();
        // 5x5 base with forge
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                task.add(origin.add(x, 0, z), Blocks.STONE_BRICK_SLAB.getDefaultState());
                boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
                if (!edge) continue;
                for (int y = 1; y <= 4; y++) {
                    boolean door = z == 0 && x == 2 && y <= 2;
                    if (door) {
                        task.add(origin.add(x, y, z), Blocks.AIR.getDefaultState());
                    } else if (y == 3 && (x == 0 || x == 4 || z == 0 || z == 4)) {
                        task.add(origin.add(x, y, z), Blocks.GLASS_PANE.getDefaultState());
                    } else {
                        task.add(origin.add(x, y, z), stone);
                    }
                }
            }
        }
        // Forge area
        task.add(origin.add(1, 1, 3), Blocks.BLAST_FURNACE.getDefaultState());
        task.add(origin.add(2, 1, 3), Blocks.SMOKER.getDefaultState());
        task.add(origin.add(3, 1, 3), Blocks.ANVIL.getDefaultState());
        // Chimney
        for (int y = 4; y <= 7; y++) {
            task.add(origin.add(1, y, 3), Blocks.STONE_BRICKS.getDefaultState());
        }
        task.add(origin.add(1, 8, 3), Blocks.CAMPFIRE.getDefaultState());
        // Roof
        for (int step = 0; step <= 2; step++) {
            int y = 5 + step;
            for (int x = -1 + step; x <= 5 - step; x++) {
                task.add(origin.add(x, y, -1), dark);
                task.add(origin.add(x, y, 5), dark);
            }
            for (int z = 0; z <= 4; z++) {
                task.add(origin.add(-1 + step, y, z), dark);
                task.add(origin.add(5 - step, y, z), dark);
            }
        }
        // Tools outside
        task.add(origin.add(-1, 1, 1), Blocks.GRINDSTONE.getDefaultState());
        task.add(origin.add(-1, 1, 2), Blocks.STONECUTTER.getDefaultState());
    }

    private void buildLibrary(OutpostBuildTask task, BlockPos origin) {
        BlockState frame = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
        BlockState wall = Blocks.OAK_PLANKS.getDefaultState();
        BlockState dark = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();
        // 6x5 base
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 5; z++) {
                task.add(origin.add(x, 0, z), Blocks.SPRUCE_PLANKS.getDefaultState());
                boolean edge = x == 0 || z == 0 || x == 5 || z == 4;
                if (!edge) continue;
                for (int y = 1; y <= 4; y++) {
                    boolean corner = (x == 0 || x == 5) && (z == 0 || z == 4);
                    boolean door = z == 0 && (x == 2 || x == 3) && y <= 2;
                    boolean window = y == 3 && (x == 1 || x == 4);
                    if (door) {
                        task.add(origin.add(x, y, z), Blocks.AIR.getDefaultState());
                    } else if (window && !corner) {
                        task.add(origin.add(x, y, z), glass);
                    } else if (corner || y == 1 || y == 4) {
                        task.add(origin.add(x, y, z), frame);
                    } else {
                        task.add(origin.add(x, y, z), wall);
                    }
                }
            }
        }
        // Roof
        for (int step = 0; step <= 2; step++) {
            int y = 5 + step;
            for (int x = -1 + step; x <= 6 - step; x++) {
                task.add(origin.add(x, y, -1), dark);
                task.add(origin.add(x, y, 5), dark);
            }
            for (int z = 0; z <= 4; z++) {
                task.add(origin.add(-1 + step, y, z), dark);
                task.add(origin.add(6 - step, y, z), dark);
            }
        }
        // Bookshelves inside
        task.add(origin.add(1, 1, 1), Blocks.BOOKSHELF.getDefaultState());
        task.add(origin.add(1, 2, 1), Blocks.BOOKSHELF.getDefaultState());
        task.add(origin.add(4, 1, 1), Blocks.BOOKSHELF.getDefaultState());
        task.add(origin.add(4, 2, 1), Blocks.BOOKSHELF.getDefaultState());
        task.add(origin.add(1, 1, 3), Blocks.BOOKSHELF.getDefaultState());
        task.add(origin.add(4, 1, 3), Blocks.BOOKSHELF.getDefaultState());
        // Lectern and candle
        task.add(origin.add(2, 1, 2), Blocks.LECTERN.getDefaultState());
        task.add(origin.add(3, 1, 2), Blocks.CANDLE.getDefaultState());
    }

    private void buildFountain(OutpostBuildTask task, BlockPos origin) {
        BlockState stone = Blocks.STONE_BRICKS.getDefaultState();
        // Base 3x3
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                task.add(origin.add(x, 0, z), stone);
            }
        }
        // Water basin
        task.add(origin.add(0, 0, 0), Blocks.WATER.getDefaultState());
        // Center pillar
        task.add(origin.add(0, 1, 0), stone);
        task.add(origin.add(0, 2, 0), stone);
        task.add(origin.add(0, 3, 0), stone);
        // Water top
        task.add(origin.add(0, 4, 0), Blocks.WATER.getDefaultState());
        // Outer ring
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    task.add(origin.add(x, 0, z), Blocks.STONE_BRICK_SLAB.getDefaultState());
                }
            }
        }
        // Flower pots on corners
        task.add(origin.add(-2, 1, -2), Blocks.POTTED_POPPY.getDefaultState());
        task.add(origin.add(2, 1, -2), Blocks.POTTED_DANDELION.getDefaultState());
        task.add(origin.add(-2, 1, 2), Blocks.POTTED_OAK_SAPLING.getDefaultState());
        task.add(origin.add(2, 1, 2), Blocks.POTTED_DARK_OAK_SAPLING.getDefaultState());
    }

    private void buildMarketStall(OutpostBuildTask task, BlockPos origin) {
        // Simple stall: 3x3 with awning
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                task.add(origin.add(x, 0, z), Blocks.OAK_PLANKS.getDefaultState());
                if (Math.abs(x) == 1 && Math.abs(z) == 1) {
                    task.add(origin.add(x, 1, z), Blocks.OAK_FENCE.getDefaultState());
                    task.add(origin.add(x, 2, z), Blocks.OAK_FENCE.getDefaultState());
                }
            }
        }
        // Awning
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 0; z++) {
                task.add(origin.add(x, 3, z), ((x + z) & 1) == 0 ? Blocks.RED_WOOL.getDefaultState() : Blocks.ORANGE_WOOL.getDefaultState());
            }
        }
        // Goods
        task.add(origin.add(-1, 1, -1), Blocks.CHEST.getDefaultState());
        task.add(origin.add(1, 1, -1), Blocks.BARREL.getDefaultState());
        task.add(origin.add(0, 1, -1), Blocks.HAY_BLOCK.getDefaultState());
    }

    private void buildFarm(OutpostBuildTask task, BlockPos origin) {
        // 9x5 farm plot
        for (int x = -4; x <= 4; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean border = Math.abs(x) == 4 || Math.abs(z) == 2;
                task.add(origin.add(x, 0, z), border ? Blocks.OAK_SLAB.getDefaultState() : Blocks.FARMLAND.getDefaultState());
                if (!border) {
                    int crop = Math.floorMod(x + z * 3, 4);
                    BlockState plant = switch (crop) {
                        case 0 -> Blocks.WHEAT.getDefaultState();
                        case 1 -> Blocks.CARROTS.getDefaultState();
                        case 2 -> Blocks.POTATOES.getDefaultState();
                        default -> Blocks.BEETROOTS.getDefaultState();
                    };
                    task.add(origin.add(x, 1, z), plant);
                }
            }
        }
        // Water in center
        task.add(origin.add(0, 0, 0), Blocks.WATER.getDefaultState());
        // Scarecrow
        task.add(origin.add(4, 1, 0), Blocks.HAY_BLOCK.getDefaultState());
        task.add(origin.add(4, 2, 0), Blocks.OAK_FENCE.getDefaultState());
        task.add(origin.add(4, 3, 0), Blocks.CARVED_PUMPKIN.getDefaultState());
    }

    private void buildAnimalPen(OutpostBuildTask task, BlockPos origin) {
        // 5x5 fenced area
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean border = Math.abs(x) == 2 || Math.abs(z) == 2;
                task.add(origin.add(x, 0, z), border ? Blocks.COBBLESTONE.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState());
                if (border) {
                    task.add(origin.add(x, 1, z), Blocks.OAK_FENCE.getDefaultState());
                    task.add(origin.add(x, 2, z), Blocks.OAK_FENCE.getDefaultState());
                }
            }
        }
        // Gate
        task.add(origin.add(0, 1, -2), Blocks.AIR.getDefaultState());
        task.add(origin.add(0, 2, -2), Blocks.AIR.getDefaultState());
        // Trough
        task.add(origin.add(-1, 1, 0), Blocks.OAK_STAIRS.getDefaultState());
        task.add(origin.add(1, 1, 0), Blocks.OAK_STAIRS.getDefaultState());
        // Hay
        task.add(origin.add(2, 1, 2), Blocks.HAY_BLOCK.getDefaultState());
        // Water trough
        task.add(origin.add(0, 1, 2), Blocks.WATER.getDefaultState());
    }

    private void buildBakery(OutpostBuildTask task, BlockPos origin) {
        BlockState frame = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
        BlockState wall = Blocks.OAK_PLANKS.getDefaultState();
        BlockState dark = Blocks.DARK_OAK_PLANKS.getDefaultState();
        // 5x4 base
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 4; z++) {
                task.add(origin.add(x, 0, z), Blocks.SPRUCE_PLANKS.getDefaultState());
                boolean edge = x == 0 || z == 0 || x == 4 || z == 3;
                if (!edge) continue;
                for (int y = 1; y <= 4; y++) {
                    boolean corner = (x == 0 || x == 4) && (z == 0 || z == 3);
                    boolean door = z == 0 && x == 2 && y <= 2;
                    boolean window = y == 3 && (x == 1 || x == 3);
                    if (door) {
                        task.add(origin.add(x, y, z), Blocks.AIR.getDefaultState());
                    } else if (window && !corner) {
                        task.add(origin.add(x, y, z), Blocks.GLASS_PANE.getDefaultState());
                    } else if (corner || y == 1 || y == 4) {
                        task.add(origin.add(x, y, z), frame);
                    } else {
                        task.add(origin.add(x, y, z), wall);
                    }
                }
            }
        }
        // Roof
        for (int step = 0; step <= 2; step++) {
            int y = 5 + step;
            for (int x = -1 + step; x <= 5 - step; x++) {
                task.add(origin.add(x, y, -1), dark);
                task.add(origin.add(x, y, 4), dark);
            }
            for (int z = 0; z <= 3; z++) {
                task.add(origin.add(-1 + step, y, z), dark);
                task.add(origin.add(5 - step, y, z), dark);
            }
        }
        // Oven
        task.add(origin.add(1, 1, 2), Blocks.SMOKER.getDefaultState());
        task.add(origin.add(2, 1, 2), Blocks.FURNACE.getDefaultState());
        // Chimney
        for (int y = 4; y <= 7; y++) {
            task.add(origin.add(1, y, 2), Blocks.STONE_BRICKS.getDefaultState());
        }
        task.add(origin.add(1, 8, 2), Blocks.CAMPFIRE.getDefaultState());
        // Display
        task.add(origin.add(3, 1, 1), Blocks.CAKE.getDefaultState());
    }

    private void buildBushes(OutpostBuildTask task, BlockPos center) {
        int[][] bushes = {
            {7, -12}, {-9, -11}, {11, 6}, {-13, 7}, {20, -6}, {-21, 5},
            {5, 20}, {-6, 19}, {16, 16}, {-16, -13}, {13, -16}, {-14, 14}
        };
        for (int[] b : bushes) {
            BlockPos pos = center.add(b[0], 1, b[1]);
            task.add(pos, Blocks.OAK_LEAVES.getDefaultState());
            if ((b[0] + b[1]) % 3 == 0) {
                task.add(pos.east(), Blocks.OAK_LEAVES.getDefaultState());
            }
            if ((b[0] * b[1]) % 2 == 0) {
                task.add(pos.north(), Blocks.OAK_LEAVES.getDefaultState());
            }
        }
        // Flower patches
        int[][] flowers = {
            {10, -15}, {-11, 13}, {17, 3}, {-18, -6}
        };
        for (int[] f : flowers) {
            BlockPos pos = center.add(f[0], 1, f[1]);
            task.add(pos, Blocks.POPPY.getDefaultState());
            task.add(pos.east(), Blocks.DANDELION.getDefaultState());
            task.add(pos.north(), Blocks.BLUE_ORCHID.getDefaultState());
        }
    }

    private void buildLampPosts(OutpostBuildTask task, BlockPos center) {
        int[][] lamps = {
            {12, -18}, {-12, -18}, {18, 12}, {-18, 12},
            {15, -3}, {-15, -3}, {3, 15}, {3, -15}
        };
        for (int[] l : lamps) {
            BlockPos pos = center.add(l[0], 1, l[1]);
            task.add(pos, Blocks.STONE_BRICKS.getDefaultState());
            task.add(pos.up(), Blocks.STONE_BRICK_WALL.getDefaultState());
            task.add(pos.up(2), Blocks.STONE_BRICK_WALL.getDefaultState());
            task.add(pos.up(3), Blocks.LANTERN.getDefaultState());
            // Base slab
            task.add(pos.north(), Blocks.STONE_BRICK_SLAB.getDefaultState());
            task.add(pos.south(), Blocks.STONE_BRICK_SLAB.getDefaultState());
            task.add(pos.east(), Blocks.STONE_BRICK_SLAB.getDefaultState());
            task.add(pos.west(), Blocks.STONE_BRICK_SLAB.getDefaultState());
        }
    }

    private void finishOutpost(ServerWorld world, String id, OutpostBuildTask task) {
        DataManager.ControlPointState point = mod.getDataManager().getControlPoints().get(id);
        if (point != null) {
            point.outpostBuilt = true;
            if (point.nextDefenseEventAt <= 0L) {
                point.nextDefenseEventAt = System.currentTimeMillis() + ModConfig.get().puntosControl.minutosEntreEventosDefensa * 60_000L;
            }
            mod.getDataManager().setControlPoint(id, point);
        }
        Box villageArea = new Box(task.center).expand(OUTPOST_RADIUS + 4, 16, OUTPOST_RADIUS + 4);
        for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class, villageArea,
            entity -> entity.getCommandTags().contains(VILLAGER_TAG))) {
            villager.discard();
        }
        for (int i = 0; i < 4; i++) {
            spawnVillager(world, task.center.add(random.nextInt(13) - 6, 2, random.nextInt(13) - 6), displayName(id));
        }
        world.playSound(null, task.center, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.5f, 1.0f);
        world.spawnParticles(ParticleTypes.FIREWORK, task.center.getX() + 0.5, task.center.getY() + 5.0, task.center.getZ() + 0.5, 80, 8.0, 2.0, 8.0, 0.12);
    }

    private void spawnVillager(ServerWorld world, BlockPos pos, String pointName) {
        VillagerEntity villager = EntityType.VILLAGER.create(world);
        if (villager == null) return;
        villager.refreshPositionAndAngles(pos, random.nextFloat() * 360.0f, 0.0f);
        villager.setPersistent();
        villager.addCommandTag(VILLAGER_TAG);
        villager.setCustomName(Text.literal("Aldeano de " + pointName));
        villager.setCustomNameVisible(false);
        world.spawnEntity(villager);
    }

    private void tickDefenseEvents(MinecraftServer server, ServerWorld world) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, DefenseEvent>> iterator = defenseEvents.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, DefenseEvent> entry = iterator.next();
            if (tickDefenseEvent(server, world, entry.getKey(), entry.getValue())) {
                iterator.remove();
            }
        }
        if (!ModConfig.get().puntosControl.eventosDefensa) return;
        for (Map.Entry<String, DataManager.ControlPointState> entry : mod.getDataManager().getControlPoints().entrySet()) {
            String id = entry.getKey();
            DataManager.ControlPointState point = entry.getValue();
            UUID owner = point.ownerUuid();
            if (owner == null || !point.outpostBuilt || defenseEvents.containsKey(id)) continue;
            ServerPlayerEntity ownerPlayer = server.getPlayerManager().getPlayer(owner);
            if (ownerPlayer == null) {
                point.nextDefenseEventAt = now + 5 * 60_000L;
                mod.getDataManager().setControlPoint(id, point);
                continue;
            }
            if (point.nextDefenseEventAt <= 0L) {
                point.nextDefenseEventAt = now + ModConfig.get().puntosControl.minutosEntreEventosDefensa * 60_000L;
                mod.getDataManager().setControlPoint(id, point);
            } else if (now >= point.nextDefenseEventAt) {
                startDefenseEvent(server, world, id, point, ownerPlayer);
            }
        }
    }

    private void startDefenseEvent(MinecraftServer server, ServerWorld world, String id, DataManager.ControlPointState point, ServerPlayerEntity owner) {
        DefenseEvent event = new DefenseEvent(owner.getUuid(), new BlockPos(point.x, point.y, point.z));
        event.ticksRemaining = ModConfig.get().puntosControl.duracionEventoDefensaSegundos * 20;
        event.remainingToSpawn = ModConfig.get().puntosControl.mobsEventoDefensaBase + Math.max(0, getOwnedCount(owner.getUuid()) - 1) * 3;
        defenseEvents.put(id, event);
        broadcast(server, "&5⚑ &d" + displayName(id) + " esta bajo ataque. &7Defiende el asentamiento.");
        world.playSound(null, event.center, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.5f, 0.85f);
        world.spawnParticles(ParticleTypes.SMOKE, event.center.getX() + 0.5, event.center.getY() + 4.0, event.center.getZ() + 0.5, 80, 10.0, 1.5, 10.0, 0.05);
    }

    private boolean tickDefenseEvent(MinecraftServer server, ServerWorld world, String id, DefenseEvent event) {
        DataManager.ControlPointState point = mod.getDataManager().getControlPoints().get(id);
        if (point == null || point.ownerUuid() == null || !point.ownerUuid().equals(event.owner)) {
            cleanupDefenseMobs(world, event);
            return true;
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(event.owner);
        if (owner == null) {
            point.nextDefenseEventAt = System.currentTimeMillis() + 5 * 60_000L;
            mod.getDataManager().setControlPoint(id, point);
            cleanupDefenseMobs(world, event);
            return true;
        }

        cleanupDeadDefenseMobs(world, event);
        if (event.remainingToSpawn > 0 && --event.spawnCooldown <= 0) {
            event.spawnCooldown = 40;
            spawnDefenseMob(world, event, owner);
            event.remainingToSpawn--;
        }
        for (UUID mobId : event.mobs) {
            Entity entity = world.getEntity(mobId);
            if (entity instanceof HostileEntity hostile) hostile.setTarget(owner);
        }
        event.ticksRemaining -= 20;
        owner.sendMessage(Text.literal("\u00A75Defensa de " + displayName(id) + " \u00A77Mobs: " + event.mobs.size()
            + " | Tiempo: " + Math.max(0, event.ticksRemaining / 20) + "s"), true);

        if (event.remainingToSpawn <= 0 && event.mobs.isEmpty()) {
            point.nextDefenseEventAt = System.currentTimeMillis() + ModConfig.get().puntosControl.minutosEntreEventosDefensa * 60_000L;
            mod.getDataManager().setControlPoint(id, point);
            mod.getRewardManager().giveFragments(owner, 6 + getOwnedCount(owner.getUuid()) * 2);
            broadcast(server, "&a⚑ " + owner.getName().getString() + " defendio " + displayName(id) + ".");
            world.playSound(null, event.center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            return true;
        }
        if (event.ticksRemaining <= 0) {
            UUID previousOwner = point.ownerUuid();
            point.owner = null;
            point.nextDefenseEventAt = 0L;
            mod.getDataManager().setControlPoint(id, point);
            updateBeaconColor(world, point);
            if (previousOwner != null) refreshOwnerBuffs(server, previousOwner);
            cleanupDefenseMobs(world, event);
            broadcast(server, "&c⚑ " + displayName(id) + " fue perdido por no defenderse a tiempo.");
            return true;
        }
        return false;
    }

    private void spawnDefenseMob(ServerWorld world, DefenseEvent event, LivingEntity target) {
        BlockPos spawn = findDefenseSpawn(world, event.center);
        HostileEntity mob;
        int roll = random.nextInt(3);
        if (roll == 0) {
            mob = new SkeletonEntity(EntityType.SKELETON, world);
        } else if (roll == 1) {
            mob = new HuskEntity(EntityType.HUSK, world);
        } else {
            mob = new ZombieEntity(EntityType.ZOMBIE, world);
        }
        mob.refreshPositionAndAngles(spawn, random.nextFloat() * 360.0f, 0.0f);
        mob.initialize(world, world.getLocalDifficulty(spawn), SpawnReason.EVENT, null, null);
        mob.setTarget(target);
        mob.setPersistent();
        mob.addCommandTag(DEFENSE_MOB_TAG);
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60 * 5, 0, false, false));
        world.spawnEntity(mob);
        event.mobs.add(mob.getUuid());
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, 18, 0.5, 0.8, 0.5, 0.04);
        world.playSound(null, spawn, SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 0.9f, 0.8f);
    }

    private BlockPos findDefenseSpawn(ServerWorld world, BlockPos center) {
        for (int i = 0; i < 20; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = OUTPOST_RADIUS + 5 + random.nextInt(7);
            BlockPos probe = center.add((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
            BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, probe);
            if (top.getY() > world.getBottomY() + 2 && world.isAir(top) && world.isAir(top.up())) return top;
        }
        return center.add(OUTPOST_RADIUS + 4, 2, 0);
    }

    private void cleanupDeadDefenseMobs(ServerWorld world, DefenseEvent event) {
        event.mobs.removeIf(uuid -> {
            Entity entity = world.getEntity(uuid);
            return !(entity instanceof LivingEntity living) || living.isDead() || living.getHealth() <= 0.0f;
        });
    }

    private void cleanupDefenseMobs(ServerWorld world, DefenseEvent event) {
        for (UUID uuid : event.mobs) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) entity.discard();
        }
        event.mobs.clear();
    }

    private void applyOwnerBuffs(MinecraftServer server) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (DataManager.ControlPointState state : mod.getDataManager().getControlPoints().values()) {
            UUID owner = state.ownerUuid();
            if (owner != null) counts.merge(owner, 1, Integer::sum);
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applyControlPointBuffs(player, counts.getOrDefault(player.getUuid(), 0));
        }
    }

    private void refreshOwnerBuffs(MinecraftServer server, UUID owner) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
        if (player != null) applyControlPointBuffs(player, getOwnedCount(owner));
    }

    private void applyControlPointBuffs(ServerPlayerEntity player, int ownedPoints) {
        int duration = ModConfig.get().puntosControl.minutosBuffRenovacion * 60 * 20 + 20 * 15;
        syncControlPointEffect(player, ownedPoints >= 1, StatusEffects.REGENERATION, REGEN_TAG, duration);
        syncControlPointEffect(player, ownedPoints >= 2, StatusEffects.RESISTANCE, RESISTANCE_TAG, duration);
        syncControlPointEffect(player, ownedPoints >= 3, StatusEffects.SPEED, SPEED_TAG, duration);
        syncControlPointEffect(player, ownedPoints >= 4, StatusEffects.ABSORPTION, ABSORPTION_TAG, duration);
    }

    private void syncControlPointEffect(ServerPlayerEntity player, boolean enabled,
                                        net.minecraft.entity.effect.StatusEffect effect, String tag, int duration) {
        if (enabled) {
            player.addCommandTag(tag);
            player.addStatusEffect(new StatusEffectInstance(effect, duration, 0, false, true));
        } else if (player.getCommandTags().contains(tag)) {
            player.removeCommandTag(tag);
            player.removeStatusEffect(effect);
        }
    }

    private void updateBeaconColor(ServerWorld world, DataManager.ControlPointState point) {
        UUID owner = point.ownerUuid();
        BlockPos glass = new BlockPos(point.x, point.y + 5, point.z);
        if (owner == null) {
            world.setBlockState(glass, Blocks.WHITE_STAINED_GLASS.getDefaultState());
            return;
        }
        int color = Math.floorMod(owner.hashCode(), 6);
        world.setBlockState(glass, switch (color) {
            case 0 -> Blocks.RED_STAINED_GLASS.getDefaultState();
            case 1 -> Blocks.BLUE_STAINED_GLASS.getDefaultState();
            case 2 -> Blocks.GREEN_STAINED_GLASS.getDefaultState();
            case 3 -> Blocks.PURPLE_STAINED_GLASS.getDefaultState();
            case 4 -> Blocks.YELLOW_STAINED_GLASS.getDefaultState();
            default -> Blocks.CYAN_STAINED_GLASS.getDefaultState();
        });
    }

    private String displayName(String id) {
        ModConfig.PuntosControl config = ModConfig.get().puntosControl;
        return switch (id) {
            case "norte" -> config.norte;
            case "sur" -> config.sur;
            case "este" -> config.este;
            case "oeste" -> config.oeste;
            default -> id;
        };
    }

    private void broadcast(MinecraftServer server, String message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(TextUtil.literal(message), false);
        }
    }

    private record OutpostBlock(BlockPos pos, BlockState state) {
    }

    private static class OutpostBuildTask {
        private final BlockPos center;
        private final Queue<OutpostBlock> blocks = new ArrayDeque<>();
        private int ticks;

        private OutpostBuildTask(BlockPos center) {
            this.center = center;
        }

        private void add(BlockPos pos, BlockState state) {
            blocks.add(new OutpostBlock(pos, state));
        }
    }

    private static class DefenseEvent {
        private final UUID owner;
        private final BlockPos center;
        private final Set<UUID> mobs = new HashSet<>();
        private int ticksRemaining;
        private int remainingToSpawn;
        private int spawnCooldown;

        private DefenseEvent(UUID owner, BlockPos center) {
            this.owner = owner;
            this.center = center;
        }
    }
}
