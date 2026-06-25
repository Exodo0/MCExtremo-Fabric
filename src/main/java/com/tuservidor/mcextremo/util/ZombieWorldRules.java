package com.tuservidor.mcextremo.util;

import com.tuservidor.mcextremo.config.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Set;

public final class ZombieWorldRules {
    private static final Set<Block> NEVER_BREAK = Set.of(
        Blocks.BEDROCK,
        Blocks.END_PORTAL,
        Blocks.END_PORTAL_FRAME,
        Blocks.BARRIER,
        Blocks.COMMAND_BLOCK,
        Blocks.REPEATING_COMMAND_BLOCK,
        Blocks.CHAIN_COMMAND_BLOCK,
        Blocks.TNT,
        Blocks.AIR,
        Blocks.CAVE_AIR,
        Blocks.VOID_AIR,
        Blocks.WATER,
        Blocks.LAVA,
        Blocks.POWDER_SNOW,
        Blocks.OBSIDIAN,
        Blocks.CRYING_OBSIDIAN,
        Blocks.REINFORCED_DEEPSLATE
    );

    private static final Set<Block> CONTAINERS = Set.of(
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.BARREL,
        Blocks.FURNACE,
        Blocks.BLAST_FURNACE,
        Blocks.SMOKER,
        Blocks.HOPPER,
        Blocks.DROPPER,
        Blocks.DISPENSER,
        Blocks.SHULKER_BOX
    );

    private static final Set<Block> SOFT_BREAKABLE = Set.of(
        Blocks.DIRT,
        Blocks.GRASS_BLOCK,
        Blocks.COARSE_DIRT,
        Blocks.ROOTED_DIRT,
        Blocks.PODZOL,
        Blocks.MYCELIUM,
        Blocks.SAND,
        Blocks.RED_SAND,
        Blocks.GRAVEL,
        Blocks.CLAY,
        Blocks.OAK_PLANKS,
        Blocks.SPRUCE_PLANKS,
        Blocks.BIRCH_PLANKS,
        Blocks.JUNGLE_PLANKS,
        Blocks.ACACIA_PLANKS,
        Blocks.DARK_OAK_PLANKS,
        Blocks.MANGROVE_PLANKS,
        Blocks.CHERRY_PLANKS,
        Blocks.BAMBOO_PLANKS,
        Blocks.CRIMSON_PLANKS,
        Blocks.WARPED_PLANKS,
        Blocks.GLASS,
        Blocks.GLASS_PANE
    );

    private ZombieWorldRules() {
    }

    public static boolean canBreak(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;

        Block block = state.getBlock();
        if (NEVER_BREAK.contains(block)) return false;

        ModConfig.Zombies.RomperBloques config = ModConfig.get().zombies.romperBloques;
        if (!config.permitirDestruirContenedores && CONTAINERS.contains(block)) return false;
        if (config.romperSoloBloquesBlandos && !SOFT_BREAKABLE.contains(block)) return false;

        return state.getHardness(world, pos) >= 0.0F;
    }

    public static boolean canPlace(World world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir()) return false;
        if (!world.getBlockState(pos.up()).isAir()) return false;

        ModConfig.Zombies.ConstruirBloques config = ModConfig.get().zombies.construirBloques;
        if (!config.colocarSoloSiHaySoporte) return true;

        BlockState below = world.getBlockState(pos.down());
        return !below.isAir() && below.isSolidBlock(world, pos.down());
    }

    public static BlockState getBuildBlock() {
        String configured = ModConfig.get().zombies.construirBloques.bloqueConstruccion.toLowerCase();
        return switch (configured) {
            case "cobblestone", "piedra" -> Blocks.COBBLESTONE.getDefaultState();
            case "oak_planks", "madera" -> Blocks.OAK_PLANKS.getDefaultState();
            case "gravel", "grava" -> Blocks.GRAVEL.getDefaultState();
            default -> Blocks.DIRT.getDefaultState();
        };
    }
}
