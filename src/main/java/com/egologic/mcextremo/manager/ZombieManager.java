package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.util.DifficultyPhase;
import com.egologic.mcextremo.util.TextUtil;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class ZombieManager {
    private final MCExtremo mod;
    private int announceTickCounter = 0;
    private DifficultyPhase lastAnnouncedPhase = null;

    public ZombieManager(MCExtremo mod) {
        this.mod = mod;
    }

    public int getDay(ServerWorld world) {
        DataManager dm = mod.getDataManager();
        int rawDay = Math.max(0, (int) (world.getTimeOfDay() / 24000L));
        int lastRawDay = dm.getLastObservedWorldDay();

        if (lastRawDay < 0) {
            if (dm.getRealDay() == 0 && rawDay > 0) {
                dm.setRealDay(rawDay);
            }
            dm.setLastObservedWorldDay(rawDay);
            return dm.getRealDay();
        }

        if (rawDay > lastRawDay) {
            dm.setRealDay(dm.getRealDay() + (rawDay - lastRawDay));
        }
        dm.setLastObservedWorldDay(rawDay);

        return dm.getRealDay();
    }

    public void skipDays(ServerWorld world, int days) {
        DataManager dm = mod.getDataManager();
        dm.setRealDay(dm.getRealDay() + days);
        dm.save();
    }

    public void reduceDays(ServerWorld world, int days) {
        DataManager dm = mod.getDataManager();
        dm.setRealDay(Math.max(0, dm.getRealDay() - days));
        dm.save();
    }

    public void tick(ServerWorld world) {
        announceTickCounter++;
        if (announceTickCounter < 200) return;
        announceTickCounter = 0;

        int day = getDay(world);
        DifficultyPhase phase = getPhase(day);
        if (lastAnnouncedPhase == null) {
            lastAnnouncedPhase = phase;
            return;
        }

        if (phase != lastAnnouncedPhase) {
            lastAnnouncedPhase = phase;
            broadcastThreatChange(phase);
        }
    }

    public DifficultyPhase getPhase(int day) {
        return DifficultyPhase.fromDay(day);
    }

    public double getMultiplier(int day) {
        if (day <= 3) return 1.0;
        if (day <= 10) return roundMultiplier(1.0 + (day - 3) * 0.06);
        if (day <= 25) return roundMultiplier(1.45 + (day - 10) * 0.05);
        if (day <= 45) return roundMultiplier(2.20 + (day - 25) * 0.05);
        if (day <= 70) return roundMultiplier(3.20 + (day - 45) * 0.045);
        if (day <= 99) return roundMultiplier(4.35 + (day - 70) * 0.04);
        return roundMultiplier(Math.min(10.0, 5.60 + (day - 100) * 0.01));
    }

    private double getSpeedBonus(int day) {
        double multiplier = getMultiplier(day);
        return Math.min(0.12, Math.max(0.0, (multiplier - 1.0) * 0.018));
    }

    public void applyScaling(ZombieEntity zombie, int day) {
        if (!ModConfig.get().zombies.agresividad.activado) return;
        if (day < 1) return;

        double multiplier = getMultiplier(day);

        var healthAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(20.0 + multiplier * 2.5);
            zombie.setHealth(zombie.getMaxHealth());
        }

        var damageAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(3.0 + multiplier * 0.8);
        }

        var speedAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.23 + getSpeedBonus(day));
        }

        var followAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.setBaseValue(16.0 + multiplier * 2.2);
        }

        var armorAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(multiplier * 0.25);
        }
    }

    public void applyHordeSpeedBoost(ZombieEntity zombie) {
        var speedAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            double current = speedAttr.getBaseValue();
            speedAttr.setBaseValue(current * 1.15);
        }
    }

    public int getIntelligenceLevel(int day) {
        return getPhase(day).getIntelligenceLevel();
    }

    public int getBreakTimeForLevel(int level) {
        if (level <= 2) return 50;
        if (level <= 4) return 35;
        return 20;
    }

    public boolean canBreakBlocks(int day) {
        return ModConfig.get().zombies.romperBloques.activado
            && day >= ModConfig.get().zombies.romperBloques.diaInicio
            && getPhase(day).hasBlockBreaking();
    }

    public boolean canBuild(int day) {
        return ModConfig.get().zombies.construirBloques.activado
            && day >= ModConfig.get().zombies.construirBloques.diaInicio
            && getPhase(day).hasBuilding();
    }

    public boolean canUseHordes(int day) {
        return ModConfig.get().zombies.horda.activado
            && day >= ModConfig.get().zombies.horda.diaInicio
            && getPhase(day).hasHordes();
    }

    public String getThreatSummary(int day) {
        DifficultyPhase phase = getPhase(day);
        StringBuilder builder = new StringBuilder();
        builder.append(phase.getDisplayName()).append(" - ").append(phase.getDescription());
        if (canBreakBlocks(day)) builder.append(", rompe bloques");
        if (canBuild(day)) builder.append(", construye");
        if (canUseHordes(day)) builder.append(", hordas");
        return builder.toString();
    }

    public String getIntelligenceName(int level) {
        return switch (level) {
            case 0 -> "Ninguno (Dia 1-3)";
            case 1 -> "Basico (Dia 4-10) - Puertas y mini hordas";
            case 2 -> "Avanzado (Dia 11-25) - Coordinacion";
            case 4 -> "Asedio (Dia 26-45) - Romper bloques blandos";
            case 5 -> "Constructor (Dia 46-70) - Escaleras coherentes";
            case 6 -> "Extremo (Dia 71+) - Todo activo";
            default -> "Desconocido";
        };
    }

    private double roundMultiplier(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private void broadcastThreatChange(DifficultyPhase phase) {
        if (mod.getDataManager().getServer() == null) return;

        String message = "&4\u2620 &cLos zombies han evolucionado: &e"
            + phase.getDisplayName() + " &7- " + phase.getDescription();
        for (ServerPlayerEntity player : mod.getDataManager().getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(TextUtil.literal(message), false);
        }
    }
}
