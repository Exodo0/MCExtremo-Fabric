package com.egologic.mcextremo.manager;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

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
        world.setBlockState(center.add(dx, 1, dz),
            (distance <= radius - 6 ? Blocks.END_STONE_BRICKS : Blocks.OBSIDIAN).getDefaultState());
    }

    private void buildArenaStructures(ServerWorld world, BlockPos center, int radius) {
        List<BlockPos> occupied = new ArrayList<>();
        buildRunicFloor(world, center);
        buildLandingPads(world, center, radius);
        buildCentralArena(world, center);
        buildCentralMonuments(world, center, occupied);
        buildObeliskRing(world, center, occupied);
        buildCardinalTowers(world, center, radius, occupied);
        buildDiagonalArches(world, center, radius, occupied);
        buildCoverScatter(world, center, occupied);
        buildEntryRamps(world, center, radius);
        buildOuterWall(world, center, radius);
        buildBarrierWall(world, center, radius);
    }

    private void buildRunicFloor(ServerWorld world, BlockPos center) {
        buildRunicRing(world, center, 20, 1);
        buildRunicRing(world, center, 38, 3);
        buildRunicRing(world, center, 56, 5);
    }

    private void buildRunicRing(ServerWorld world, BlockPos center, int ring, int spacing) {
        int points = Math.max(48, (int) Math.round(Math.PI * 2.0 * ring));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            int x = (int) Math.round(Math.cos(angle) * ring);
            int z = (int) Math.round(Math.sin(angle) * ring);
            boolean purpur = spacing > 1 && (i / spacing) % 2 == 1;
            BlockPos pos = center.add(x, 1, z);
            world.setBlockState(pos, (purpur ? Blocks.PURPUR_BLOCK : Blocks.CRYING_OBSIDIAN).getDefaultState());
            if (purpur) {
                world.setBlockState(pos.up(), Blocks.END_ROD.getDefaultState());
            }
        }
    }

    private void buildLandingPads(ServerWorld world, BlockPos center, int radius) {
        int padRadius = Math.max(18, radius / 3);
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0 * i / 12.0;
            BlockPos pad = center.add((int) Math.round(Math.cos(angle) * padRadius), 2,
                (int) Math.round(Math.sin(angle) * padRadius));
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > 3.2) continue;
                    world.setBlockState(pad.add(dx, 0, dz),
                        (distance > 2.4 ? Blocks.CRYING_OBSIDIAN : Blocks.END_STONE_BRICKS).getDefaultState());
                }
            }
            world.setBlockState(pad.up(), Blocks.END_ROD.getDefaultState());
        }
    }

    private void buildCentralArena(ServerWorld world, BlockPos center) {
        for (int dx = -18; dx <= 18; dx++) {
            for (int dz = -18; dz <= 18; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 18.2) continue;

                BlockPos floor = center.add(dx, 2, dz);
                if (distance <= 4.2) {
                    world.setBlockState(floor, Blocks.OBSIDIAN.getDefaultState());
                } else if (distance <= 14.2) {
                    world.setBlockState(floor,
                        (Math.floorMod(dx + dz, 4) < 2 ? Blocks.PURPUR_PILLAR : Blocks.PURPUR_BLOCK).getDefaultState());
                } else {
                    world.setBlockState(floor, Blocks.OBSIDIAN.getDefaultState());
                    if (distance >= 16.0) {
                        world.setBlockState(floor.up(), Blocks.OBSIDIAN.getDefaultState());
                    }
                }
            }
        }

        int points = 96;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            int x = (int) Math.round(Math.cos(angle) * 14.0);
            int z = (int) Math.round(Math.sin(angle) * 14.0);
            BlockPos pos = center.add(x, 2, z);
            world.setBlockState(pos, Blocks.CRYING_OBSIDIAN.getDefaultState());
            if (i % 4 == 0) {
                world.setBlockState(pos.up(), Blocks.END_ROD.getDefaultState());
            }
        }

        int[][] cardinal = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] point : cardinal) {
            world.setBlockState(center.add(point[0], 2, point[1]), Blocks.CRYING_OBSIDIAN.getDefaultState());
        }
    }

    private void buildCentralMonuments(ServerWorld world, BlockPos center, List<BlockPos> occupied) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            BlockPos base = center.add(dir[0] * 12, 2, dir[1] * 12);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlockState(base.add(dx, 0, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                }
            }
            for (int y = 1; y <= 6; y++) {
                world.setBlockState(base.up(y), Blocks.PURPUR_PILLAR.getDefaultState());
                if (y == 2 || y == 4) {
                    int sideX = dir[1] == 0 ? 0 : 1;
                    int sideZ = dir[0] == 0 ? 0 : 1;
                    world.setBlockState(base.add(sideX, y, sideZ), Blocks.PURPUR_BLOCK.getDefaultState());
                    world.setBlockState(base.add(-sideX, y, -sideZ), Blocks.PURPUR_BLOCK.getDefaultState());
                }
            }
            world.setBlockState(base.add(0, 7, 0), Blocks.CRYING_OBSIDIAN.getDefaultState());
            world.setBlockState(base.add(0, 8, 0), Blocks.END_ROD.getDefaultState());
            occupied.add(base);
        }
    }

    private void buildObeliskRing(ServerWorld world, BlockPos center, List<BlockPos> occupied) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            BlockPos base = center.add((int) Math.round(Math.cos(angle) * 38.0), 2,
                (int) Math.round(Math.sin(angle) * 38.0));
            int height = i % 2 == 0 ? 12 : 9;
            for (int y = 0; y < height; y++) {
                if (y == height - 1) {
                    world.setBlockState(base.up(y), Blocks.END_ROD.getDefaultState());
                } else if (y == height - 2) {
                    world.setBlockState(base.up(y), Blocks.CRYING_OBSIDIAN.getDefaultState());
                } else {
                    world.setBlockState(base.up(y), Blocks.OBSIDIAN.getDefaultState());
                }
                if (y == 1 || y == 4) {
                    world.setBlockState(base.add(1, y, 0), Blocks.PURPUR_BLOCK.getDefaultState());
                    world.setBlockState(base.add(-1, y, 0), Blocks.PURPUR_BLOCK.getDefaultState());
                    world.setBlockState(base.add(0, y, 1), Blocks.PURPUR_BLOCK.getDefaultState());
                    world.setBlockState(base.add(0, y, -1), Blocks.PURPUR_BLOCK.getDefaultState());
                }
            }
            world.setBlockState(base.add(2, 0, 0), Blocks.END_STONE_BRICKS.getDefaultState());
            world.setBlockState(base.add(-2, 0, 0), Blocks.END_STONE_BRICKS.getDefaultState());
            world.setBlockState(base.add(0, 0, 2), Blocks.END_STONE_BRICKS.getDefaultState());
            world.setBlockState(base.add(0, 0, -2), Blocks.END_STONE_BRICKS.getDefaultState());
            occupied.add(base);
        }
    }

    private void buildCardinalTowers(ServerWorld world, BlockPos center, int radius, List<BlockPos> occupied) {
        int towerRadius = Math.min(50, Math.max(30, radius - 30));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            BlockPos base = center.add(dir[0] * towerRadius, 2, dir[1] * towerRadius);
            int half = 4;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    world.setBlockState(base.add(dx, 0, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                    boolean wall = Math.abs(dx) == half || Math.abs(dz) == half;
                    boolean entrance = isTowerEntrance(dx, dz, dir[0], dir[1], half);
                    if (wall && !entrance) {
                        for (int y = 1; y <= 8; y++) {
                            world.setBlockState(base.add(dx, y, dz), Blocks.OBSIDIAN.getDefaultState());
                        }
                    }
                    if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                        world.setBlockState(base.add(dx, 9, dz), Blocks.PURPUR_BLOCK.getDefaultState());
                        world.setBlockState(base.add(dx, 10, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                    }
                }
            }
            int[][] interior = {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
            for (int[] pillar : interior) {
                for (int y = 1; y <= 8; y++) {
                    world.setBlockState(base.add(pillar[0], y, pillar[1]), Blocks.PURPUR_PILLAR.getDefaultState());
                }
            }
            for (int y = 1; y <= 10; y++) {
                world.setBlockState(base.add(1, y, 1), Blocks.LADDER.getDefaultState());
            }
            world.setBlockState(base.add(0, 12, 0), Blocks.END_ROD.getDefaultState());
            occupied.add(base);
        }
    }

    private boolean isTowerEntrance(int dx, int dz, int dirX, int dirZ, int half) {
        if (dirX != 0) {
            return dx == -dirX * half && Math.abs(dz) <= 1;
        }
        return dz == -dirZ * half && Math.abs(dx) <= 1;
    }

    private void buildDiagonalArches(ServerWorld world, BlockPos center, int radius, List<BlockPos> occupied) {
        int archRadius = Math.min(48, Math.max(28, radius - 32));
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 4.0 + i * Math.PI / 2.0;
            BlockPos base = center.add((int) Math.round(Math.cos(angle) * archRadius), 2,
                (int) Math.round(Math.sin(angle) * archRadius));
            int tangentX = (int) Math.round(-Math.sin(angle));
            int tangentZ = (int) Math.round(Math.cos(angle));
            BlockPos left = base.add(tangentX * 4, 0, tangentZ * 4);
            BlockPos right = base.add(-tangentX * 4, 0, -tangentZ * 4);
            buildArchPillar(world, left);
            buildArchPillar(world, right);
            for (int w = -4; w <= 4; w++) {
                BlockPos top = base.add(tangentX * w, 7, tangentZ * w);
                world.setBlockState(top, Blocks.PURPUR_BLOCK.getDefaultState());
            }
            world.setBlockState(base.add(0, 7, 0), Blocks.CRYING_OBSIDIAN.getDefaultState());
            world.setBlockState(base.add(0, 8, 0), Blocks.END_ROD.getDefaultState());
            occupied.add(base);
        }
    }

    private void buildArchPillar(ServerWorld world, BlockPos base) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                world.setBlockState(base.add(dx, 0, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                for (int y = 1; y <= 6; y++) {
                    world.setBlockState(base.add(dx, y, dz), Blocks.OBSIDIAN.getDefaultState());
                }
            }
        }
    }

    private void buildCoverScatter(ServerWorld world, BlockPos center, List<BlockPos> occupied) {
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 22.5);
            int distance = 38 + (i % 4) * 7;
            BlockPos base = center.add((int) Math.round(Math.cos(angle) * distance), 2,
                (int) Math.round(Math.sin(angle) * distance));
            if (isNearOccupied(base, occupied, 8.0)) continue;
            if (i % 2 == 0) {
                buildCoverL(world, base, angle);
            } else {
                buildCoverLine(world, base, angle);
            }
            occupied.add(base);
        }
    }

    private void buildCoverL(ServerWorld world, BlockPos base, double angle) {
        int dirX = (int) Math.round(Math.cos(angle));
        int dirZ = (int) Math.round(Math.sin(angle));
        int sideX = -dirZ;
        int sideZ = dirX;
        if (dirX == 0 && dirZ == 0) dirX = 1;
        for (int i = 0; i < 4; i++) {
            BlockPos pos = base.add(dirX * i, 0, dirZ * i);
            world.setBlockState(pos, Blocks.END_STONE_BRICKS.getDefaultState());
            if (i <= 1) {
                world.setBlockState(pos.up(), Blocks.OBSIDIAN.getDefaultState());
            }
        }
        for (int i = 1; i <= 2; i++) {
            world.setBlockState(base.add(sideX * i, 0, sideZ * i), Blocks.END_STONE_BRICKS.getDefaultState());
        }
    }

    private void buildCoverLine(ServerWorld world, BlockPos base, double angle) {
        int sideX = (int) Math.round(-Math.sin(angle));
        int sideZ = (int) Math.round(Math.cos(angle));
        if (sideX == 0 && sideZ == 0) sideZ = 1;
        for (int i = -2; i <= 1; i++) {
            BlockPos pos = base.add(sideX * i, 0, sideZ * i);
            world.setBlockState(pos, Blocks.END_STONE_BRICKS.getDefaultState());
            if (i == -2 || i == 1) {
                world.setBlockState(pos.up(), Blocks.PURPUR_SLAB.getDefaultState());
            }
        }
    }

    private boolean isNearOccupied(BlockPos pos, List<BlockPos> occupied, double minDistance) {
        double minSq = minDistance * minDistance;
        for (BlockPos other : occupied) {
            if (pos.getSquaredDistance(other) < minSq) return true;
        }
        return false;
    }

    private void buildEntryRamps(ServerWorld world, BlockPos center, int radius) {
        int start = Math.max(20, radius - 15);
        int end = Math.max(start, radius - 10);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            for (int distance = start; distance <= end; distance++) {
                for (int width = -3; width <= 3; width++) {
                    int x = dir[0] * distance + (dir[1] == 0 ? 0 : width);
                    int z = dir[1] * distance + (dir[0] == 0 ? 0 : width);
                    BlockPos pos = center.add(x, 2, z);
                    if (Math.abs(width) == 3) {
                        world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
                        world.setBlockState(pos.up(), Blocks.OBSIDIAN.getDefaultState());
                    } else {
                        world.setBlockState(pos, Blocks.END_STONE_BRICKS.getDefaultState());
                    }
                }
            }
        }
    }

    private void buildOuterWall(ServerWorld world, BlockPos center, int radius) {
        int inner = Math.max(20, radius - 10);
        int outer = radius;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < inner || distance > outer) continue;
                if (isOuterGate(dx, dz, outer, 7)) continue;

                BlockPos base = center.add(dx, 2, dz);
                world.setBlockState(base, Blocks.END_STONE_BRICKS.getDefaultState());
                world.setBlockState(base.up(), Blocks.END_STONE_BRICKS.getDefaultState());

                double angle = Math.atan2(dz, dx);
                int segment = Math.floorMod((int) Math.round(angle * outer), 5);
                if (segment == 0) {
                    for (int y = 2; y <= 4; y++) {
                        world.setBlockState(base.up(y), Blocks.OBSIDIAN.getDefaultState());
                    }
                    world.setBlockState(base.up(5), Blocks.CRYING_OBSIDIAN.getDefaultState());
                    world.setBlockState(base.up(6), Blocks.END_ROD.getDefaultState());
                } else {
                    world.setBlockState(base.up(2), Blocks.PURPUR_SLAB.getDefaultState());
                }
            }
        }
    }

    private boolean isOuterGate(int dx, int dz, int outer, int width) {
        int half = width / 2;
        boolean eastWest = Math.abs(Math.abs(dx) - outer) <= 2 && Math.abs(dz) <= half;
        boolean northSouth = Math.abs(Math.abs(dz) - outer) <= 2 && Math.abs(dx) <= half;
        return eastWest || northSouth;
    }

    private void buildBarrierWall(ServerWorld world, BlockPos center, int radius) {
        int barrierRadius = radius + 3;
        for (int dx = -barrierRadius; dx <= barrierRadius; dx++) {
            for (int dz = -barrierRadius; dz <= barrierRadius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(distance - barrierRadius) > 0.8) continue;
                for (int dy = 1; dy <= 12; dy++) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.BARRIER.getDefaultState());
                }
            }
        }
    }

    void clearArena(ServerWorld world, BlockPos center, int radius) {
        int clear = radius + 10;
        for (int dx = -clear; dx <= clear; dx++) {
            for (int dz = -clear; dz <= clear; dz++) {
                if (dx * dx + dz * dz > clear * clear) continue;
                for (int dy = -10; dy <= 28; dy++) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.AIR.getDefaultState());
                }
            }
        }
    }
}
