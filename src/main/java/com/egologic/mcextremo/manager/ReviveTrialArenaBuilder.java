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

                int thickness = Math.max(2, (int) Math.round(6 - distance / 6.0));
                for (int dy = 0; dy < thickness; dy++) {
                    BlockPos pos = center.add(dx, -dy, dz);
                    world.setBlockState(pos, Blocks.END_STONE.getDefaultState());
                }
                if (distance > radius - 4 && distance < radius + 1) {
                    world.setBlockState(center.add(dx, 1, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                }
            }
        }

        world.setBlockState(center.down(4), Blocks.BEDROCK.getDefaultState());
        buildCentralPlatform(world, center);
        buildVoidAltar(world, center);
        buildFortressRing(world, center, radius);
        buildFortressTowers(world, center, radius);

        buildBrokenBridge(world, center, radius, 1, 0);
        buildBrokenBridge(world, center, radius, -1, 0);
        buildBrokenBridge(world, center, radius, 0, 1);
        buildBrokenBridge(world, center, radius, 0, -1);

        buildSidePlatform(world, center.add(radius - 9, 2, 0), 4);
        buildSidePlatform(world, center.add(-radius + 9, 2, 0), 4);
        buildSidePlatform(world, center.add(0, 2, radius - 9), 4);
        buildSidePlatform(world, center.add(0, 2, -radius + 9), 4);

        buildRuinPillar(world, center.add(radius - 8, 1, radius - 8), 6);
        buildRuinPillar(world, center.add(-radius + 8, 1, radius - 8), 6);
        buildRuinPillar(world, center.add(radius - 8, 1, -radius + 8), 5);
        buildRuinPillar(world, center.add(-radius + 8, 1, -radius + 8), 5);

        buildObsidianArch(world, center.add(0, 1, -radius + 8), true);
        buildObsidianArch(world, center.add(0, 1, radius - 8), true);
        buildObsidianArch(world, center.add(-radius + 8, 1, 0), false);
        buildObsidianArch(world, center.add(radius - 8, 1, 0), false);

        buildHealingCover(world, center.add(radius / 2, 1, radius / 4));
        buildHealingCover(world, center.add(-radius / 2, 1, radius / 4));
        buildHealingCover(world, center.add(radius / 3, 1, -radius / 2));
        buildHealingCover(world, center.add(-radius / 3, 1, -radius / 2));
        buildHealingCover(world, center.add(radius / 4, 1, 0));
        buildHealingCover(world, center.add(-radius / 4, 1, 0));
        buildOpenCover(world, center, radius);
        buildVoidDecorations(world, center, radius);

        buildInnerSafetyWall(world, center, radius);
        buildBarrierWall(world, center, radius);
    }
    private void buildCentralPlatform(ServerWorld world, BlockPos center) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (Math.abs(dx) == 5 && Math.abs(dz) == 5) continue;
                world.setBlockState(center.add(dx, 1, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) {
                    world.setBlockState(center.add(dx, 2, dz), Blocks.PURPUR_BLOCK.getDefaultState());
                }
            }
        }
        world.setBlockState(center.add(3, 3, 0), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(-3, 3, 0), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(0, 3, 3), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(0, 3, -3), Blocks.END_ROD.getDefaultState());
    }
    private void buildVoidAltar(ServerWorld world, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                if (dx == 0 && dz == 0) continue;
                BlockPos pos = center.add(dx, 3, dz);
                world.setBlockState(pos, (Math.abs(dx) + Math.abs(dz) <= 1 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState());
            }
        }
        world.setBlockState(center.add(0, 4, 2), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(0, 4, -2), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(2, 4, 0), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(-2, 4, 0), Blocks.END_ROD.getDefaultState());
    }
    private void buildBrokenBridge(ServerWorld world, BlockPos center, int radius, int dirX, int dirZ) {
        int start = 5;
        int end = radius - 10;
        for (int i = start; i <= end; i++) {
            for (int width = -1; width <= 1; width++) {
                int x = dirX * i + (dirZ == 0 ? 0 : width);
                int z = dirZ * i + (dirX == 0 ? 0 : width);
                BlockPos pos = center.add(x, 2, z);
                world.setBlockState(pos, (i % 6 == 0 && width != 0 ? Blocks.PURPUR_BLOCK : Blocks.END_STONE_BRICKS).getDefaultState());
                if (width != 0 && i % 5 == 0) {
                    world.setBlockState(pos.up(), Blocks.PURPUR_SLAB.getDefaultState());
                }
            }
        }
    }
    private void buildFortressRing(ServerWorld world, BlockPos center, int radius) {
        int inner = radius - 13;
        int outer = radius - 6;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < inner || distance > outer) continue;
                BlockPos pos = center.add(dx, 2, dz);
                world.setBlockState(pos, (Math.abs(dx + dz) % 5 == 0 ? Blocks.PURPUR_BLOCK : Blocks.END_STONE_BRICKS).getDefaultState());
                if (distance > outer - 1.5 && Math.abs(dx + dz) % 6 == 0) {
                    world.setBlockState(pos.up(), Blocks.OBSIDIAN.getDefaultState());
                }
            }
        }

        buildWideRamp(world, center, radius, 1, 0);
        buildWideRamp(world, center, radius, -1, 0);
        buildWideRamp(world, center, radius, 0, 1);
        buildWideRamp(world, center, radius, 0, -1);
    }
    private void buildFortressTowers(ServerWorld world, BlockPos center, int radius) {
        int offset = radius - 12;
        buildOpenTower(world, center.add(offset, 2, offset), 4);
        buildOpenTower(world, center.add(-offset, 2, offset), 4);
        buildOpenTower(world, center.add(offset, 2, -offset), 4);
        buildOpenTower(world, center.add(-offset, 2, -offset), 4);
    }
    private void buildOpenTower(ServerWorld world, BlockPos base, int halfSize) {
        for (int dx = -halfSize; dx <= halfSize; dx++) {
            for (int dz = -halfSize; dz <= halfSize; dz++) {
                if (Math.abs(dx) == halfSize && Math.abs(dz) == halfSize) continue;
                BlockPos floor = base.add(dx, 0, dz);
                world.setBlockState(floor, Blocks.END_STONE_BRICKS.getDefaultState());
                if ((Math.abs(dx) == halfSize || Math.abs(dz) == halfSize) && Math.abs(dx + dz) % 3 == 0) {
                    world.setBlockState(floor.up(), Blocks.PURPUR_SLAB.getDefaultState());
                }
            }
        }

        int[][] pillars = {
            {-halfSize, -halfSize}, {-halfSize, halfSize}, {halfSize, -halfSize}, {halfSize, halfSize}
        };
        for (int[] pillar : pillars) {
            for (int y = 1; y <= 5; y++) {
                world.setBlockState(base.add(pillar[0], y, pillar[1]), (y == 5 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState());
            }
            world.setBlockState(base.add(pillar[0], 6, pillar[1]), Blocks.END_ROD.getDefaultState());
        }
    }
    private void buildWideRamp(ServerWorld world, BlockPos center, int radius, int dirX, int dirZ) {
        int start = 7;
        int end = radius - 8;
        for (int i = start; i <= end; i++) {
            for (int width = -2; width <= 2; width++) {
                int x = dirX * i + (dirZ == 0 ? 0 : width);
                int z = dirZ * i + (dirX == 0 ? 0 : width);
                world.setBlockState(center.add(x, 2, z), Blocks.END_STONE_BRICKS.getDefaultState());
            }
        }
    }
    private void buildSidePlatform(ServerWorld world, BlockPos base, int halfSize) {
        for (int dx = -halfSize; dx <= halfSize; dx++) {
            for (int dz = -halfSize; dz <= halfSize; dz++) {
                if (Math.abs(dx) == halfSize && Math.abs(dz) == halfSize) continue;
                BlockPos pos = base.add(dx, 0, dz);
                world.setBlockState(pos, Blocks.END_STONE_BRICKS.getDefaultState());
                if ((Math.abs(dx) == halfSize || Math.abs(dz) == halfSize) && Math.abs(dx + dz) % 4 == 0) {
                    world.setBlockState(pos.up(), Blocks.PURPUR_BLOCK.getDefaultState());
                }
            }
        }
        world.setBlockState(base.up(), Blocks.END_ROD.getDefaultState());
    }
    private void buildRuinPillar(ServerWorld world, BlockPos base, int height) {
        for (int y = 0; y < height; y++) {
            world.setBlockState(base.up(y), Blocks.OBSIDIAN.getDefaultState());
            if (y < height - 1) {
                world.setBlockState(base.add(1, y, 0), Blocks.PURPUR_BLOCK.getDefaultState());
                if (y % 2 == 0) {
                    world.setBlockState(base.add(0, y, 1), Blocks.END_STONE_BRICKS.getDefaultState());
                }
            }
        }
        world.setBlockState(base.up(height), Blocks.CRYING_OBSIDIAN.getDefaultState());
        world.setBlockState(base.up(height + 1), Blocks.END_ROD.getDefaultState());
        world.setBlockState(base.add(-1, 0, 0), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.add(-1, 1, 0), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.add(-2, 1, 0), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.add(-2, 2, 0), Blocks.END_STONE_BRICKS.getDefaultState());
    }
    private void buildObsidianArch(ServerWorld world, BlockPos base, boolean eastWest) {
        for (int side = -2; side <= 2; side += 4) {
            for (int y = 0; y <= 4; y++) {
                BlockPos pos = eastWest ? base.add(side, y, 0) : base.add(0, y, side);
                world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
            }
        }
        for (int i = -2; i <= 2; i++) {
            BlockPos pos = eastWest ? base.add(i, 4, 0) : base.add(0, 4, i);
            world.setBlockState(pos, i == 0 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.OBSIDIAN.getDefaultState());
        }
        world.setBlockState(base.up(5), Blocks.END_ROD.getDefaultState());
    }
    private void buildHealingCover(ServerWorld world, BlockPos base) {
        world.setBlockState(base, Blocks.PURPUR_BLOCK.getDefaultState());
        world.setBlockState(base.up(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.east(), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.west(), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.north(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.south(), Blocks.PURPUR_SLAB.getDefaultState());
    }
    private void buildOpenCover(ServerWorld world, BlockPos center, int radius) {
        int[][] anchors = {
            {radius / 2, radius / 2}, {-radius / 2, radius / 2},
            {radius / 2, -radius / 2}, {-radius / 2, -radius / 2},
            {radius / 2, -radius / 4}, {-radius / 2, radius / 4}
        };
        for (int[] anchor : anchors) {
            buildLowBarricade(world, center.add(anchor[0], 2, anchor[1]));
        }
    }
    private void buildLowBarricade(ServerWorld world, BlockPos base) {
        for (int i = -2; i <= 2; i++) {
            BlockPos pos = base.add(i, 0, 0);
            world.setBlockState(pos, Blocks.END_STONE_BRICKS.getDefaultState());
            if (Math.abs(i) % 2 == 0) {
                world.setBlockState(pos.up(), Blocks.PURPUR_SLAB.getDefaultState());
            }
        }
        world.setBlockState(base.add(0, 1, 1), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.add(0, 1, -1), Blocks.PURPUR_SLAB.getDefaultState());
    }
    private void buildVoidDecorations(ServerWorld world, BlockPos center, int radius) {
        int offset = radius - 16;
        buildBeaconSpire(world, center.add(offset, 3, 0));
        buildBeaconSpire(world, center.add(-offset, 3, 0));
        buildBeaconSpire(world, center.add(0, 3, offset));
        buildBeaconSpire(world, center.add(0, 3, -offset));
    }
    private void buildBeaconSpire(ServerWorld world, BlockPos base) {
        world.setBlockState(base, Blocks.OBSIDIAN.getDefaultState());
        world.setBlockState(base.up(), Blocks.CRYING_OBSIDIAN.getDefaultState());
        world.setBlockState(base.up(2), Blocks.END_ROD.getDefaultState());
        world.setBlockState(base.north(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.south(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.east(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.west(), Blocks.PURPUR_SLAB.getDefaultState());
    }
    private void buildBarrierWall(ServerWorld world, BlockPos center, int radius) {
        int barrierRadius = radius + 8;
        for (int dx = -barrierRadius; dx <= barrierRadius; dx++) {
            for (int dz = -barrierRadius; dz <= barrierRadius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < barrierRadius - 1.3 || distance > barrierRadius + 1.3) continue;
                for (int dy = -4; dy <= 20; dy++) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.BARRIER.getDefaultState());
                }
            }
        }
    }
    private void buildInnerSafetyWall(ServerWorld world, BlockPos center, int radius) {
        int wallRadius = radius - 2;
        for (int dx = -wallRadius; dx <= wallRadius; dx++) {
            for (int dz = -wallRadius; dz <= wallRadius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < wallRadius - 1.5 || distance > wallRadius + 1.5) continue;
                BlockPos base = center.add(dx, 2, dz);
                world.setBlockState(base, Blocks.OBSIDIAN.getDefaultState());
                world.setBlockState(base.up(), Blocks.END_STONE_BRICKS.getDefaultState());
            }
        }
    }
    void clearIsland(ServerWorld world, BlockPos center, int radius) {
        int clearRadius = radius + 16;
        for (int dx = -clearRadius; dx <= clearRadius; dx++) {
            for (int dz = -clearRadius; dz <= clearRadius; dz++) {
                for (int dy = -10; dy <= 24; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }
}
