package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.util.TextUtil;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

class EventTrialArenaBuilder {
    boolean processArenaBuild(ServerWorld world, EventArenaBuildTask task, int columnBudget) {
        if (task == null) return true;
        int processed = 0;
        while (processed < columnBudget && task.stage < 2) {
            if (task.stage == 0) {
                clearArenaColumn(world, task.center, task.radius, task.dx, task.dz);
                if (advanceArenaCursor(task, task.radius + 10)) {
                    task.stage = 1;
                    task.dx = -task.radius;
                    task.dz = -task.radius;
                }
            } else if (task.stage == 1) {
                buildArenaBaseColumn(world, task.center, task.radius, task.dx, task.dz);
                if (advanceArenaCursor(task, task.radius)) {
                    task.stage = 2;
                }
            }
            processed++;
        }
        if (task.stage < 2) return false;
        if (!task.structuresBuilt) {
            buildArenaStructures(world, task.center, task.radius);
            task.structuresBuilt = true;
        }
        return true;
    }
    private boolean advanceArenaCursor(EventArenaBuildTask task, int limit) {
        task.dz++;
        if (task.dz <= limit) return false;
        task.dz = -limit;
        task.dx++;
        return task.dx > limit;
    }
    void updateGenerationBar(EventTrial event) {
        if (event.arenaBuild == null) return;
        int radius = event.arenaBuild.radius;
        int clearSide = radius + 10;
        int clearTotal = (clearSide * 2 + 1) * (clearSide * 2 + 1);
        int baseTotal = (radius * 2 + 1) * (radius * 2 + 1);
        int done;
        if (event.arenaBuild.stage == 0) {
            done = (event.arenaBuild.dx + clearSide) * (clearSide * 2 + 1) + (event.arenaBuild.dz + clearSide);
        } else if (event.arenaBuild.stage == 1) {
            done = clearTotal + (event.arenaBuild.dx + radius) * (radius * 2 + 1) + (event.arenaBuild.dz + radius);
        } else {
            done = clearTotal + baseTotal;
        }
        float percent = Math.max(0.03f, Math.min(1.0f, done / (float) (clearTotal + baseTotal)));
        event.bossBar.setPercent(percent);
        event.bossBar.setName(Text.literal("\u00A75Event Trial \u00A77| \u00A7fGenerando arena \u00A78- \u00A7e" + Math.round(percent * 100.0f) + "%"));
    }
    void generateArena(ServerWorld world, BlockPos center, int radius) {
        clearArena(world, center, radius);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                buildArenaBaseColumn(world, center, radius, dx, dz);
            }
        }
        buildArenaStructures(world, center, radius);
    }
    void clearArenaColumn(ServerWorld world, BlockPos center, int radius, int dx, int dz) {
        int r = radius + 10;
        if (dx * dx + dz * dz > r * r) return;
        for (int dy = -10; dy <= 28; dy++) {
            world.setBlockState(center.add(dx, dy, dz), Blocks.AIR.getDefaultState());
        }
    }
    private void buildArenaBaseColumn(ServerWorld world, BlockPos center, int radius, int dx, int dz) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > radius) return;
        int thickness = Math.max(2, (int) Math.round(8 - distance / 14.0));
        for (int dy = 0; dy < thickness; dy++) {
            world.setBlockState(center.add(dx, -dy, dz), Blocks.END_STONE.getDefaultState());
        }
        if (distance <= radius - 5) {
            world.setBlockState(center.add(dx, 1, dz),
                (Math.abs(dx * 13 + dz * 7) % 11 == 0 ? Blocks.PURPUR_BLOCK : Blocks.END_STONE_BRICKS).getDefaultState());
        }
        if (distance > radius - 4) {
            world.setBlockState(center.add(dx, 1, dz), Blocks.END_STONE_BRICKS.getDefaultState());
        }
    }
    private void buildArenaStructures(ServerWorld world, BlockPos center, int radius) {
        buildCentralPlatform(world, center);
        buildRunicRings(world, center, radius);
        buildLandingPads(world, center, radius);
        buildTower(world, center.add(radius - 18, 2, 0), 7, 16);
        buildTower(world, center.add(-radius + 18, 2, 0), 7, 16);
        buildTower(world, center.add(0, 2, radius - 18), 7, 16);
        buildTower(world, center.add(0, 2, -radius + 18), 7, 16);
        buildTower(world, center.add(radius - 24, 2, radius - 24), 5, 10);
        buildTower(world, center.add(-radius + 24, 2, radius - 24), 5, 10);
        buildTower(world, center.add(radius - 24, 2, -radius + 24), 5, 10);
        buildTower(world, center.add(-radius + 24, 2, -radius + 24), 5, 10);
        buildBridge(world, center, radius, 1, 0);
        buildBridge(world, center, radius, -1, 0);
        buildBridge(world, center, radius, 0, 1);
        buildBridge(world, center, radius, 0, -1);
        buildRitualObelisks(world, center, radius);
        buildOuterCrystals(world, center, radius);
        buildLightPylons(world, center, radius);
        buildBrokenArches(world, center, radius);
        buildLowRuins(world, center, radius);
        buildCorruptedVeins(world, center, radius);
        buildMinorAltars(world, center, radius);
        buildCover(world, center, radius);
        buildBarrierWall(world, center, radius);
    }
    private void buildCentralPlatform(ServerWorld world, BlockPos center) {
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > 11) continue;
                world.setBlockState(center.add(dx, 2, dz), Blocks.PURPUR_BLOCK.getDefaultState());
            }
        }
        for (int i = 0; i < 4; i++) {
            int x = i < 2 ? (i == 0 ? 8 : -8) : 0;
            int z = i >= 2 ? (i == 2 ? 8 : -8) : 0;
            world.setBlockState(center.add(x, 3, z), Blocks.END_ROD.getDefaultState());
        }
    }
    private void buildTower(ServerWorld world, BlockPos base, int half, int height) {
        for (int y = 0; y <= height; y++) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean wall = Math.abs(dx) == half || Math.abs(dz) == half;
                    boolean floor = y % 5 == 0;
                    boolean doorway = y <= 3 && (Math.abs(dx) <= 2 || Math.abs(dz) <= 2);
                    if ((floor || wall) && !doorway) {
                        world.setBlockState(base.add(dx, y, dz), (y % 10 == 0 ? Blocks.PURPUR_BLOCK : Blocks.END_STONE_BRICKS).getDefaultState());
                    }
                }
            }
        }
        for (int y = 0; y <= height; y++) {
            world.setBlockState(base.add(0, y, 0), Blocks.LADDER.getDefaultState());
        }
        world.setBlockState(base.add(0, height + 1, 0), Blocks.END_ROD.getDefaultState());
    }
    private void buildBridge(ServerWorld world, BlockPos center, int radius, int dirX, int dirZ) {
        for (int i = 10; i < radius - 12; i++) {
            for (int w = -3; w <= 3; w++) {
                int x = dirX * i + (dirZ == 0 ? 0 : w);
                int z = dirZ * i + (dirX == 0 ? 0 : w);
                world.setBlockState(center.add(x, 2, z), Blocks.END_STONE_BRICKS.getDefaultState());
            }
        }
    }
    private void buildCover(ServerWorld world, BlockPos center, int radius) {
        int[][] points = {{22, 18}, {-22, 18}, {22, -18}, {-22, -18}, {36, 0}, {-36, 0}, {0, 36}, {0, -36}};
        for (int[] point : points) {
            BlockPos base = center.add(point[0], 2, point[1]);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.setBlockState(base.add(dx, 0, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                }
            }
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(base.add(-4, y, 0), Blocks.OBSIDIAN.getDefaultState());
                world.setBlockState(base.add(4, y, 0), Blocks.OBSIDIAN.getDefaultState());
            }
        }
    }
    private void buildRunicRings(ServerWorld world, BlockPos center, int radius) {
        int[] rings = {16, 28, 44};
        for (int ring : rings) {
            int points = ring < 20 ? 48 : 72;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points;
                int x = (int) Math.round(Math.cos(angle) * ring);
                int z = (int) Math.round(Math.sin(angle) * ring);
                BlockPos pos = center.add(x, 2, z);
                if (x * x + z * z >= (radius - 7) * (radius - 7)) continue;
                boolean accent = i % 6 == 0;
                world.setBlockState(pos, (accent ? Blocks.CRYING_OBSIDIAN : Blocks.PURPUR_BLOCK).getDefaultState());
                if (accent && ring != 44) {
                    world.setBlockState(pos.up(), Blocks.END_ROD.getDefaultState());
                }
            }
        }
    }
    private void buildLandingPads(ServerWorld world, BlockPos center, int radius) {
        int padRadius = Math.min(24, Math.max(12, radius / 4));
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0 * i / 12.0;
            BlockPos pad = center.add((int) Math.round(Math.cos(angle) * padRadius), 2, (int) Math.round(Math.sin(angle) * padRadius));
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > 3.2) continue;
                    world.setBlockState(pad.add(dx, 0, dz), (distance > 2.4 ? Blocks.CRYING_OBSIDIAN : Blocks.END_STONE_BRICKS).getDefaultState());
                }
            }
            world.setBlockState(pad.up(), Blocks.END_ROD.getDefaultState());
        }
    }
    private void buildRitualObelisks(ServerWorld world, BlockPos center, int radius) {
        int obeliskRadius = Math.min(radius - 28, 46);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            BlockPos base = center.add((int) Math.round(Math.cos(angle) * obeliskRadius), 2, (int) Math.round(Math.sin(angle) * obeliskRadius));
            int height = i % 2 == 0 ? 9 : 7;
            for (int y = 0; y < height; y++) {
                world.setBlockState(base.add(0, y, 0), (y % 3 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState());
            }
            world.setBlockState(base.add(0, height, 0), Blocks.END_ROD.getDefaultState());
            world.setBlockState(base.add(1, 1, 0), Blocks.PURPUR_BLOCK.getDefaultState());
            world.setBlockState(base.add(-1, 1, 0), Blocks.PURPUR_BLOCK.getDefaultState());
            world.setBlockState(base.add(0, 1, 1), Blocks.PURPUR_BLOCK.getDefaultState());
            world.setBlockState(base.add(0, 1, -1), Blocks.PURPUR_BLOCK.getDefaultState());
        }
    }
    private void buildOuterCrystals(ServerWorld world, BlockPos center, int radius) {
        int crystalRadius = radius - 9;
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2.0 * i / 16.0;
            BlockPos base = center.add((int) Math.round(Math.cos(angle) * crystalRadius), 2, (int) Math.round(Math.sin(angle) * crystalRadius));
            int height = 3 + i % 4;
            for (int y = 0; y < height; y++) {
                world.setBlockState(base.add(0, y, 0), (y == height - 1 ? Blocks.PURPUR_BLOCK : Blocks.OBSIDIAN).getDefaultState());
            }
            if (i % 2 == 0) {
                world.setBlockState(base.add(1, 0, 0), Blocks.CRYING_OBSIDIAN.getDefaultState());
                world.setBlockState(base.add(-1, 0, 0), Blocks.CRYING_OBSIDIAN.getDefaultState());
            }
        }
    }
    private void buildLightPylons(ServerWorld world, BlockPos center, int radius) {
        int pylonRadius = Math.min(radius - 18, 58);
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0 * i / 12.0 + Math.PI / 12.0;
            BlockPos base = center.add((int) Math.round(Math.cos(angle) * pylonRadius), 2, (int) Math.round(Math.sin(angle) * pylonRadius));
            for (int y = 0; y <= 5; y++) {
                world.setBlockState(base.add(0, y, 0), (y == 5 ? Blocks.END_ROD : Blocks.PURPUR_PILLAR).getDefaultState());
            }
            world.setBlockState(base.add(1, 0, 0), Blocks.END_STONE_BRICKS.getDefaultState());
            world.setBlockState(base.add(-1, 0, 0), Blocks.END_STONE_BRICKS.getDefaultState());
            world.setBlockState(base.add(0, 0, 1), Blocks.END_STONE_BRICKS.getDefaultState());
            world.setBlockState(base.add(0, 0, -1), Blocks.END_STONE_BRICKS.getDefaultState());
        }
    }
    private void buildBrokenArches(ServerWorld world, BlockPos center, int radius) {
        int archRadius = Math.min(radius - 30, 38);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0 + Math.PI / 8.0;
            BlockPos base = center.add((int) Math.round(Math.cos(angle) * archRadius), 2, (int) Math.round(Math.sin(angle) * archRadius));
            boolean alongX = Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle));
            for (int side = -1; side <= 1; side += 2) {
                for (int y = 0; y <= 4; y++) {
                    BlockPos pillar = alongX ? base.add(side * 3, y, 0) : base.add(0, y, side * 3);
                    world.setBlockState(pillar, (y % 2 == 0 ? Blocks.END_STONE_BRICKS : Blocks.PURPUR_PILLAR).getDefaultState());
                }
            }
            for (int w = -2; w <= 2; w++) {
                if (i % 2 == 0 && Math.abs(w) == 2) continue;
                BlockPos top = alongX ? base.add(w, 5, 0) : base.add(0, 5, w);
                world.setBlockState(top, Blocks.PURPUR_BLOCK.getDefaultState());
            }
            world.setBlockState(base, Blocks.CRYING_OBSIDIAN.getDefaultState());
        }
    }
    private void buildLowRuins(ServerWorld world, BlockPos center, int radius) {
        int[][] points = {
            {16, 34}, {-16, 34}, {16, -34}, {-16, -34},
            {42, 16}, {-42, 16}, {42, -16}, {-42, -16},
            {30, 30}, {-30, 30}, {30, -30}, {-30, -30}
        };
        for (int i = 0; i < points.length; i++) {
            BlockPos base = center.add(points[i][0], 2, points[i][1]);
            boolean alongX = i % 2 == 0;
            for (int l = -5; l <= 5; l++) {
                if (Math.abs(l) <= 1) continue;
                int height = 1 + Math.floorMod(i + l, 3);
                for (int y = 0; y < height; y++) {
                    BlockPos pos = alongX ? base.add(l, y, -2) : base.add(-2, y, l);
                    world.setBlockState(pos, (y == height - 1 ? Blocks.END_STONE_BRICKS : Blocks.OBSIDIAN).getDefaultState());
                }
            }
            world.setBlockState(base.add(0, 0, 0), Blocks.PURPUR_BLOCK.getDefaultState());
            world.setBlockState(base.add(1, 0, 1), Blocks.CRYING_OBSIDIAN.getDefaultState());
        }
    }
    private void buildCorruptedVeins(ServerWorld world, BlockPos center, int radius) {
        for (int line = 0; line < 14; line++) {
            double angle = Math.PI * 2.0 * line / 14.0;
            int start = 18 + (line % 3) * 4;
            int end = Math.min(radius - 12, start + 24);
            for (int d = start; d < end; d += 3) {
                int x = (int) Math.round(Math.cos(angle) * d + Math.sin(angle) * ((d + line) % 5 - 2));
                int z = (int) Math.round(Math.sin(angle) * d - Math.cos(angle) * ((d + line) % 5 - 2));
                BlockPos pos = center.add(x, 2, z);
                if (x * x + z * z > (radius - 8) * (radius - 8)) continue;
                world.setBlockState(pos, (d % 2 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState());
                if (d % 9 == 0) {
                    world.setBlockState(pos.up(), Blocks.END_ROD.getDefaultState());
                }
            }
        }
    }
    private void buildMinorAltars(ServerWorld world, BlockPos center, int radius) {
        int altarRadius = Math.min(radius - 24, 52);
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI * 2.0 * i / 10.0 + Math.PI / 10.0;
            BlockPos base = center.add((int) Math.round(Math.cos(angle) * altarRadius), 2, (int) Math.round(Math.sin(angle) * altarRadius));
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 3) continue;
                    world.setBlockState(base.add(dx, 0, dz), (Math.abs(dx) + Math.abs(dz) == 3 ? Blocks.OBSIDIAN : Blocks.PURPUR_BLOCK).getDefaultState());
                }
            }
            world.setBlockState(base.up(), Blocks.CRYING_OBSIDIAN.getDefaultState());
            world.setBlockState(base.up(2), Blocks.END_ROD.getDefaultState());
        }
    }
    private void buildBarrierWall(ServerWorld world, BlockPos center, int radius) {
        int barrierRadius = radius + 3;
        for (int dx = -barrierRadius; dx <= barrierRadius; dx++) {
            for (int dz = -barrierRadius; dz <= barrierRadius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < barrierRadius - 1 || distance > barrierRadius + 1) continue;
                for (int y = 1; y <= 12; y++) {
                    world.setBlockState(center.add(dx, y, dz), Blocks.BARRIER.getDefaultState());
                }
            }
        }
    }
    void clearArena(ServerWorld world, BlockPos center, int radius) {
        int r = radius + 10;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int dy = -10; dy <= 28; dy++) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.AIR.getDefaultState());
                }
            }
        }
    }
}
