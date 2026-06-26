package com.egologic.mcextremo.manager;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

class ReviveTrialArenaBuilder {
    void generateIsland(ServerWorld world, BlockPos center, int radius) {
        clearIsland(world, center, radius);
        for (int dx = -radius - 3; dx <= radius + 3; dx++) {
            for (int dz = -radius - 3; dz <= radius + 3; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                double edgeNoise = ((Math.abs(dx * 31 + dz * 17) % 9) - 4) * 0.45;
                if (distance > radius + edgeNoise) continue;

                int thickness = Math.max(4, (int) Math.round(10 - distance / 5.0));
                for (int dy = 0; dy < thickness; dy++) {
                    world.setBlockState(center.add(dx, -dy, dz), Blocks.END_STONE.getDefaultState());
                }
                world.setBlockState(center.add(dx, 1, dz),
                    (distance > radius - 4 ? Blocks.END_STONE_BRICKS : Blocks.END_STONE).getDefaultState());
            }
        }

        world.setBlockState(center.down(4), Blocks.BEDROCK.getDefaultState());
        buildOuterRim(world, center, radius);
        buildRuinedTowers(world, center, radius);
        buildCombatPillars(world, center);
        buildCrackLines(world, center);
        buildCrossroads(world, center);
        buildCentralAltar(world, center);
        buildAltarRunes(world, center);
        buildSafetyLedges(world, center, radius);
        buildBarrierWall(world, center, radius);
    }

    private void buildOuterRim(ServerWorld world, BlockPos center, int radius) {
        int inner = Math.max(8, radius - 8);
        int outer = Math.max(inner + 1, radius - 2);
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < inner || distance > outer) continue;
                if (isCardinalGate(dx, dz, outer, 5)) continue;

                BlockPos base = center.add(dx, 2, dz);
                world.setBlockState(base, Blocks.END_STONE_BRICKS.getDefaultState());
                world.setBlockState(base.up(), Blocks.END_STONE_BRICKS.getDefaultState());
                if (Math.floorMod(dx * 13 + dz * 7, 5) == 0) {
                    world.setBlockState(base.up(2), Blocks.OBSIDIAN.getDefaultState());
                }
            }
        }
    }

    private void buildRuinedTowers(ServerWorld world, BlockPos center, int radius) {
        int towerRadius = Math.min(20, Math.max(12, radius - 10));
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 4.0 + i * Math.PI / 2.0;
            BlockPos base = center.add((int) Math.round(Math.cos(angle) * towerRadius), 2,
                (int) Math.round(Math.sin(angle) * towerRadius));
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.setBlockState(base.add(dx, 0, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                        for (int y = 1; y <= 4; y++) {
                            world.setBlockState(base.add(dx, y, dz), Blocks.OBSIDIAN.getDefaultState());
                        }
                    }
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        world.setBlockState(base.add(dx, 5, dz), Blocks.PURPUR_BLOCK.getDefaultState());
                    }
                }
            }
            world.setBlockState(base.add(0, 6, 0), Blocks.END_ROD.getDefaultState());
        }
    }

    private void buildCombatPillars(ServerWorld world, BlockPos center) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            int x = (int) Math.round(Math.cos(angle) * 14.0);
            int z = (int) Math.round(Math.sin(angle) * 14.0);
            BlockPos base = center.add(x, 2, z);
            for (int y = 0; y <= 2; y++) {
                world.setBlockState(base.up(y), Blocks.OBSIDIAN.getDefaultState());
            }
            world.setBlockState(base.up(3), Blocks.CRYING_OBSIDIAN.getDefaultState());
            world.setBlockState(base.up(4), Blocks.END_ROD.getDefaultState());

            int tangentX = (int) Math.round(-Math.sin(angle));
            int tangentZ = (int) Math.round(Math.cos(angle));
            if (tangentX == 0 && tangentZ == 0) tangentZ = 1;
            world.setBlockState(base.add(tangentX, 0, tangentZ), Blocks.END_STONE_BRICKS.getDefaultState());
            world.setBlockState(base.add(-tangentX, 0, -tangentZ), Blocks.END_STONE_BRICKS.getDefaultState());
        }
    }

    private void buildCrackLines(ServerWorld world, BlockPos center) {
        for (int line = 0; line < 6; line++) {
            double angle = Math.toRadians(line * 30.0);
            for (int distance = 8; distance <= 20; distance++) {
                int x = (int) Math.round(Math.cos(angle) * distance);
                int z = (int) Math.round(Math.sin(angle) * distance);
                BlockPos pos = center.add(x, 1, z);
                world.setBlockState(pos, Blocks.CRYING_OBSIDIAN.getDefaultState());
                if (distance % 4 == 0) {
                    world.setBlockState(pos.up(), Blocks.END_ROD.getDefaultState());
                }
            }
        }
    }

    private void buildCrossroads(ServerWorld world, BlockPos center) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            for (int distance = 8; distance <= 20; distance++) {
                for (int width = -1; width <= 1; width++) {
                    int x = dir[0] * distance + (dir[1] == 0 ? 0 : width);
                    int z = dir[1] * distance + (dir[0] == 0 ? 0 : width);
                    BlockPos pos = center.add(x, 2, z);
                    world.setBlockState(pos, Blocks.END_STONE_BRICKS.getDefaultState());
                    if (Math.abs(width) == 1 && distance % 3 == 0) {
                        world.setBlockState(pos.up(), Blocks.PURPUR_SLAB.getDefaultState());
                    }
                }
            }
        }
    }

    private void buildCentralAltar(ServerWorld world, BlockPos center) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance <= 7.2) {
                    boolean border = distance >= 5.8;
                    boolean accent = Math.floorMod(dx + dz, 4) < 2;
                    world.setBlockState(center.add(dx, 2, dz),
                        (border && accent ? Blocks.CRYING_OBSIDIAN : Blocks.PURPUR_BLOCK).getDefaultState());
                }
                if (distance <= 4.2) {
                    world.setBlockState(center.add(dx, 3, dz), Blocks.PURPUR_PILLAR.getDefaultState());
                }
                if (distance <= 2.2) {
                    world.setBlockState(center.add(dx, 4, dz), Blocks.OBSIDIAN.getDefaultState());
                }
            }
        }

        int[][] corners = {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
        for (int[] corner : corners) {
            for (int y = 3; y <= 5; y++) {
                world.setBlockState(center.add(corner[0], y, corner[1]), Blocks.OBSIDIAN.getDefaultState());
            }
            world.setBlockState(center.add(corner[0], 6, corner[1]), Blocks.CRYING_OBSIDIAN.getDefaultState());
        }

        for (int y = 5; y <= 7; y++) {
            world.setBlockState(center.add(0, y, 0), Blocks.CRYING_OBSIDIAN.getDefaultState());
        }
        world.setBlockState(center.add(0, 8, 0), Blocks.END_ROD.getDefaultState());

        buildAltarStairs(world, center, 1, 0);
        buildAltarStairs(world, center, -1, 0);
        buildAltarStairs(world, center, 0, 1);
        buildAltarStairs(world, center, 0, -1);
    }

    private void buildAltarStairs(ServerWorld world, BlockPos center, int dirX, int dirZ) {
        for (int width = -1; width <= 1; width++) {
            int xOuter = dirX * 7 + (dirZ == 0 ? 0 : width);
            int zOuter = dirZ * 7 + (dirX == 0 ? 0 : width);
            int xInner = dirX * 5 + (dirZ == 0 ? 0 : width);
            int zInner = dirZ * 5 + (dirX == 0 ? 0 : width);
            world.setBlockState(center.add(xOuter, 2, zOuter), Blocks.PURPUR_SLAB.getDefaultState());
            world.setBlockState(center.add(xInner, 3, zInner), Blocks.PURPUR_BLOCK.getDefaultState());
        }
    }

    private void buildAltarRunes(ServerWorld world, BlockPos center) {
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0 * i / 12.0;
            BlockPos pos = center.add((int) Math.round(Math.cos(angle) * 5.0), 2,
                (int) Math.round(Math.sin(angle) * 5.0));
            world.setBlockState(pos, Blocks.CRYING_OBSIDIAN.getDefaultState());
            if (i % 2 == 0) {
                world.setBlockState(pos.up(), Blocks.END_ROD.getDefaultState());
            }
        }
    }

    private void buildSafetyLedges(ServerWorld world, BlockPos center, int radius) {
        int ledgeRadius = Math.max(8, radius - 5);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            BlockPos ledge = center.add(dir[0] * ledgeRadius, 2, dir[1] * ledgeRadius);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlockState(ledge.add(dx, 0, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                }
            }
        }
    }

    private boolean isCardinalGate(int dx, int dz, int outer, int width) {
        int half = width / 2;
        boolean eastWest = Math.abs(Math.abs(dx) - outer) <= 1 && Math.abs(dz) <= half;
        boolean northSouth = Math.abs(Math.abs(dz) - outer) <= 1 && Math.abs(dx) <= half;
        return eastWest || northSouth;
    }

    private void buildBarrierWall(ServerWorld world, BlockPos center, int radius) {
        int barrierRadius = radius + 8;
        for (int dx = -barrierRadius; dx <= barrierRadius; dx++) {
            for (int dz = -barrierRadius; dz <= barrierRadius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(distance - barrierRadius) > 0.7) continue;
                for (int dy = -4; dy <= 20; dy++) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.BARRIER.getDefaultState());
                }
            }
        }
    }

    void clearIsland(ServerWorld world, BlockPos center, int radius) {
        int clear = radius + 16;
        for (int dx = -clear; dx <= clear; dx++) {
            for (int dz = -clear; dz <= clear; dz++) {
                if (dx * dx + dz * dz > clear * clear) continue;
                for (int dy = -10; dy <= 24; dy++) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.AIR.getDefaultState());
                }
            }
        }
    }
}
