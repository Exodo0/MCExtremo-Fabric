package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.network.TrialCinematicNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import com.egologic.mcextremo.util.TextUtil;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

class EventTrialCinematicController {
    void tickIntro(MinecraftServer server, ServerWorld world, EventTrial event) {
        ModConfig.EventTrial config = ModConfig.get().eventTrial;
        int total = Math.max(1, config.introDuracionTicks);
        event.introTicks = Math.max(0, event.introTicks - 1);
        float remaining = event.introTicks / (float) total;
        double height = Math.max(0.0, config.introAltura * remaining);

        event.bossBar.setPercent(Math.max(0.05f, 1.0f - remaining));
        event.bossBar.setName(Text.literal("\u00A75Event Trial \u00A77| \u00A7fEntrada \u00A78- \u00A7e"
            + Math.max(1, event.introTicks / 20) + "s"));

        for (UUID uuid : new HashSet<>(event.participants)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            BlockPos landing = event.landingPositions.getOrDefault(uuid, event.center.add(0, 2, 0));

            double y = landing.getY() + 1.0 + height;
            tickIntroAvatar(world, event, uuid, landing, y);
            player.sendMessage(Text.literal("\u00A7dDescendiendo a la arena..."), true);

            if (config.introHazLuz && world.getTime() % 2L == 0L) {
                spawnIntroBeam(world, landing, y);
            }

            if (height <= 2.0 && event.impactPrimedPlayers.add(uuid)) {
                spawnLandingShockwave(world, landing);
            }

            if (height <= 0.6 && event.landedPlayers.add(uuid)) {
                discardIntroAvatar(world, event, uuid);
                if (config.introCirculoFuego) {
                    spawnLandingFireCircle(world, landing);
                }
            }
        }

        if (event.introTicks <= 0 || event.landedPlayers.size() >= event.participants.size()) {
            for (UUID uuid : new HashSet<>(event.participants)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player == null) continue;
                BlockPos landing = event.landingPositions.getOrDefault(uuid, event.center.add(0, 2, 0));
                if (event.landedPlayers.add(uuid) && config.introCirculoFuego) {
                    spawnLandingFireCircle(world, landing);
                }
                player.setCameraEntity(player);
                teleportToLanding(player, world, landing);
                resetIntroPlayerState(player);
            }
            discardIntroActors(world, event);
            discardIntroCamera(world, event);
            event.phase = EventTrialPhase.PREPARATION;
            event.actionCooldown = Math.max(20, config.preparacionSegundos * 20);
            broadcast(server, "&5Event Trial &7- &eLa entrada termino. Preparate.");
        }
    }
    ArmorStandEntity spawnCentralCamera(ServerWorld world, BlockPos center) {
        Vec3d pos = Vec3d.ofCenter(center).add(0.0, 2.5, 0.0);
        ArmorStandEntity camera = new ArmorStandEntity(world, pos.x, pos.y, pos.z);
        camera.setInvisible(true);
        camera.setNoGravity(true);
        camera.setInvulnerable(true);
        setArmorStandMarker(camera);
        camera.setCustomName(Text.literal("mcextremo_camera"));
        camera.setCustomNameVisible(false);
        camera.addCommandTag("mcextremo_event_camera");
        camera.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, -85.0f);
        world.spawnEntity(camera);
        return camera;
    }
    void setArmorStandMarker(ArmorStandEntity armorStand) {
        NbtCompound nbt = new NbtCompound();
        armorStand.writeNbt(nbt);
        nbt.putBoolean("Marker", true);
        armorStand.readNbt(nbt);
    }
    void prepareIntroViewer(ServerPlayerEntity player, ServerWorld world, BlockPos center, ModConfig.EventTrial config) {
        BlockPos safe = eventSafeIntroPosition(center);
        player.teleport(world, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYaw(), player.getPitch());
        player.setVelocity(0.0, 0.0, 0.0);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
        player.setInvulnerable(true);
        player.setInvisible(true);
        player.setNoGravity(true);
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, config.introDuracionTicks + 40, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, config.introDuracionTicks + 40, 4, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 12, 0, false, false));
    }
    private BlockPos eventSafeIntroPosition(BlockPos center) {
        return center.add(0, 10, 0);
    }
    void createIntroAvatar(ServerWorld world, EventTrial event, ServerPlayerEntity player, BlockPos landing, ModConfig.EventTrial config) {
        ArmorStandEntity avatar = new ArmorStandEntity(world, landing.getX() + 0.5, landing.getY() + 1.0 + config.introAltura, landing.getZ() + 0.5);
        avatar.setInvulnerable(true);
        avatar.setNoGravity(true);
        avatar.setCustomName(player.getName());
        avatar.setCustomNameVisible(true);
        avatar.addCommandTag("mcextremo_event_avatar");
        avatar.equipStack(EquipmentSlot.HEAD, enchantedItem(Items.DIAMOND_HELMET, Enchantments.PROTECTION, 2));
        avatar.equipStack(EquipmentSlot.CHEST, enchantedItem(Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 2));
        avatar.equipStack(EquipmentSlot.LEGS, enchantedItem(Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 2));
        avatar.equipStack(EquipmentSlot.FEET, enchantedItem(Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 2));
        avatar.equipStack(EquipmentSlot.MAINHAND, enchantedItem(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 2));
        world.spawnEntity(avatar);
        event.introAvatars.put(player.getUuid(), avatar.getUuid());
    }
    void tickIntroAvatar(ServerWorld world, EventTrial event, UUID playerUuid, BlockPos landing, double y) {
        UUID avatarUuid = event.introAvatars.get(playerUuid);
        Entity entity = avatarUuid == null ? null : world.getEntity(avatarUuid);
        if (entity == null) return;
        double angle = Math.atan2(event.center.getZ() - landing.getZ(), event.center.getX() - landing.getX());
        entity.refreshPositionAndAngles(landing.getX() + 0.5, y, landing.getZ() + 0.5, (float) Math.toDegrees(angle) - 90.0f, 0.0f);
        entity.setVelocity(0.0, 0.0, 0.0);
    }
    void resetIntroPlayerState(ServerPlayerEntity player) {
        if (player.getCameraEntity() != player) {
            player.setCameraEntity(player);
        }
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setNoGravity(false);
        player.fallDistance = 0.0f;
    }
    void discardIntroAvatar(ServerWorld world, EventTrial event, UUID playerUuid) {
        UUID avatarUuid = event.introAvatars.remove(playerUuid);
        if (avatarUuid == null) return;
        Entity entity = world.getEntity(avatarUuid);
        if (entity != null) entity.discard();
    }
    void discardIntroActors(ServerWorld world, EventTrial event) {
        for (UUID uuid : new HashSet<>(event.introAvatars.values())) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) entity.discard();
        }
        event.introAvatars.clear();
    }
    void discardIntroCamera(ServerWorld world, EventTrial event) {
        if (event.cameraEntityId == null) return;
        Entity camera = world.getEntity(event.cameraEntityId);
        if (camera != null) camera.discard();
        event.cameraEntityId = null;
    }
    private void spawnIntroBeam(ServerWorld world, BlockPos landing, double playerY) {
        double x = landing.getX() + 0.5;
        double z = landing.getZ() + 0.5;
        double top = Math.max(playerY + 1.5, landing.getY() + 2.0);
        for (double y = landing.getY() + 0.3; y <= top; y += 1.35) {
            double distanceToTop = Math.max(0.0, top - y);
            double normalized = Math.max(0.0, 1.0 - distanceToTop / Math.max(1.0, top - landing.getY()));
            int count = 1 + (int) Math.round(normalized * 3.0);
            double spread = 0.04 + normalized * 0.12;
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, count, spread, 0.08, spread, 0.004 + normalized * 0.006);
            if (((int) y) % 4 == 0) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2 + (int) Math.round(normalized * 2.0), 0.16, 0.12, 0.16, 0.02);
            }
        }
        int crown = world.getTime() % 10L == 0L ? 18 : 8;
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, top, z, crown, 0.35, 0.2, 0.35, 0.03);
    }
    private void spawnLandingShockwave(ServerWorld world, BlockPos landing) {
        double x = landing.getX() + 0.5;
        double y = landing.getY() + 1.08;
        double z = landing.getZ() + 0.5;
        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2.0 * i / 48.0;
            double radius = 1.2 + (i % 3) * 0.65;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.CLOUD, px, y, pz, 1, 0.03, 0.02, 0.03, 0.02);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, y + 0.1, pz, 1, 0.02, 0.02, 0.02, 0.03);
        }
        world.spawnParticles(ParticleTypes.FLASH, x, y + 0.3, z, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, landing, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.35f, 1.6f);
    }
    private void spawnLandingFireCircle(ServerWorld world, BlockPos landing) {
        double x = landing.getX() + 0.5;
        double y = landing.getY() + 1.05;
        double z = landing.getZ() + 0.5;
        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2.0 * i / 36.0;
            double px = x + Math.cos(angle) * 2.35;
            double pz = z + Math.sin(angle) * 2.35;
            world.spawnParticles(ParticleTypes.FLAME, px, y, pz, 2, 0.04, 0.04, 0.04, 0.01);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, y + 0.1, pz, 1, 0.04, 0.04, 0.04, 0.01);
        }
        world.spawnParticles(ParticleTypes.LAVA, x, y, z, 10, 0.6, 0.05, 0.6, 0.02);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.3, z, 28, 1.2, 0.2, 1.2, 0.06);
        world.playSound(null, landing, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.3f);
        world.playSound(null, landing, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.6f, 0.8f);
    }
    BlockPos getLandingPosition(ServerWorld world, BlockPos center, int index, int totalPlayers) {
        ModConfig.EventTrial config = ModConfig.get().eventTrial;
        if (totalPlayers <= 1) {
            return findSafeSpawn(world, center.add(0, 3, 0), center);
        }
        double angle = (Math.PI * 2.0 * index) / totalPlayers;
        int radius = config.introRadioSpawn + (index % 2) * 4;
        BlockPos preferred = center.add((int) Math.round(Math.cos(angle) * radius), 4, (int) Math.round(Math.sin(angle) * radius));
        return findSafeSpawn(world, preferred, center);
    }
    void teleportToIntroStart(ServerPlayerEntity player, ServerWorld world, BlockPos landing, ModConfig.EventTrial config) {
        player.teleport(world, landing.getX() + 0.5, landing.getY() + 1.0 + config.introAltura, landing.getZ() + 0.5, player.getYaw(), -75.0f);
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
        player.fallDistance = 0.0f;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, config.introDuracionTicks + 40, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, config.introDuracionTicks + 40, 4, false, false));
    }
    void teleportToLanding(ServerPlayerEntity player, ServerWorld world, BlockPos landing) {
        player.teleport(world, landing.getX() + 0.5, landing.getY() + 1.0, landing.getZ() + 0.5, player.getYaw(), player.getPitch());
        player.setVelocity(0.0, 0.0, 0.0);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
    }


    private ItemStack enchantedItem(net.minecraft.item.Item item, Enchantment enchantment, int level) {
        ItemStack stack = new ItemStack(item);
        EnchantmentHelper.set(Map.of(enchantment, level), stack);
        return stack;
    }

    private BlockPos findSafeSpawn(ServerWorld world, BlockPos preferred, BlockPos center) {
        BlockPos ground = findGround(world, preferred, center);
        if (isSpawnSafe(world, ground)) return ground;
        for (int radius = 1; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos candidate = findGround(world, preferred.add(dx, 0, dz), center);
                    if (isSpawnSafe(world, candidate)) return candidate;
                }
            }
        }
        return center.add(0, 3, 0);
    }

    private BlockPos findGround(ServerWorld world, BlockPos pos, BlockPos center) {
        int minY = center.getY() + 1;
        int maxY = center.getY() + 12;
        BlockPos cursor = new BlockPos(pos.getX(), Math.min(maxY, Math.max(minY, pos.getY())), pos.getZ());
        for (int y = maxY; y >= minY; y--) {
            BlockPos feet = new BlockPos(cursor.getX(), y, cursor.getZ());
            if (!world.getBlockState(feet.down()).isAir() && isSpawnSafe(world, feet)) return feet;
        }
        return new BlockPos(pos.getX(), minY + 1, pos.getZ());
    }

    private boolean isSpawnSafe(ServerWorld world, BlockPos feet) {
        BlockState floor = world.getBlockState(feet.down());
        return !floor.isAir() && world.getBlockState(feet).isAir() && world.getBlockState(feet.up()).isAir();
    }

    private void broadcast(MinecraftServer server, String message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(TextUtil.literal(message), false);
        }
    }

}
