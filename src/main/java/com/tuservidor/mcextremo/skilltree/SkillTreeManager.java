package com.tuservidor.mcextremo.skilltree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.network.SkillTreeNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SkillTreeManager {
    private final MCExtremo mod;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFile;
    private final Map<UUID, Set<String>> unlockedSkills = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static final Type MAP_TYPE = new com.google.gson.reflect.TypeToken<Map<String, List<String>>>(){}.getType();

    public SkillTreeManager(MCExtremo mod) {
        this.mod = mod;
        this.dataFile = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("mcextremo").resolve("skills.json");
    }

    public void load() {
        try {
            Files.createDirectories(dataFile.getParent());
            if (Files.exists(dataFile)) {
                String json = Files.readString(dataFile);
                Map<String, List<String>> raw = gson.fromJson(json, MAP_TYPE);
                unlockedSkills.clear();
                if (raw != null) {
                    for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
                        UUID uuid = UUID.fromString(entry.getKey());
                        unlockedSkills.put(uuid, new HashSet<>(entry.getValue()));
                    }
                }
            }
            MCExtremo.LOGGER.info("Skills cargados: " + unlockedSkills.size() + " jugador(es).");
        } catch (Exception e) {
            MCExtremo.LOGGER.error("Error cargando skills", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            Map<String, List<String>> data = new HashMap<>();
            for (Map.Entry<UUID, Set<String>> entry : unlockedSkills.entrySet()) {
                data.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
            }
            Files.writeString(dataFile, gson.toJson(data));
        } catch (IOException e) {
            MCExtremo.LOGGER.error("Error guardando skills", e);
        }
    }

    public boolean hasSkill(UUID uuid, Skill skill) {
        Set<String> skills = unlockedSkills.get(uuid);
        return skills != null && skills.contains(skill.getId());
    }

    public boolean canUnlock(UUID uuid, Skill skill) {
        if (hasSkill(uuid, skill)) return false;
        if (skill.getPrerequisite() != null && !hasSkill(uuid, skill.getPrerequisite())) return false;
        return true;
    }

    public boolean unlockSkill(ServerPlayerEntity player, Skill skill) {
        return tryUnlockSkill(player, skill);
    }

    public boolean tryUnlockSkill(ServerPlayerEntity player, Skill skill) {
        if (hasSkill(player.getUuid(), skill)) {
            player.sendMessage(Text.literal("\u00A7e\u2139 \u00A77Ya tienes desbloqueada: " + skill.getDisplayName()), false);
            openSkillTree(player);
            return false;
        }
        if (!canUnlock(player.getUuid(), skill)) {
            player.sendMessage(Text.literal("\u00A7c\u2716 \u00A77Requisito no cumplido para: " + skill.getDisplayName()), false);
            openSkillTree(player);
            return false;
        }
        if (player.experienceLevel < skill.getCost()) {
            player.sendMessage(Text.literal("\u00A7c\u2716 \u00A77Necesitas " + skill.getCost() + " niveles (tienes " + player.experienceLevel + ")"), false);
            openSkillTree(player);
            return false;
        }

        player.addExperienceLevels(-skill.getCost());

        unlockedSkills.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(skill.getId());
        save();

        SkillEffectApplier.applySkill(player, skill);

        player.sendMessage(Text.literal("\u00A7a\u2714 \u00A7a\u00A7l\u00A1Habilidad desbloqueada! \u00A7r" + skill.getDisplayName()), false);
        player.sendMessage(Text.literal("\u00A77  " + skill.getDescription() + " \u00A7e(-" + skill.getCost() + " niveles)"), false);

        reopenInventory(player);
        return true;
    }

    public void resetSkills(ServerPlayerEntity player) {
        unlockedSkills.remove(player.getUuid());
        save();
        SkillEffectApplier.removeAllModifiers(player);
        player.sendMessage(Text.literal("\u00A7c\u2716 Todas las habilidades han sido reseteadas."), false);
    }

    public Set<String> getUnlockedSkills(UUID uuid) {
        return unlockedSkills.getOrDefault(uuid, Collections.emptySet());
    }

    public void applyAllEffects(ServerPlayerEntity player) {
        Set<String> skills = unlockedSkills.get(player.getUuid());
        if (skills == null) return;
        for (String skillId : skills) {
            Skill skill = Skill.fromId(skillId);
            if (skill != null) {
                SkillEffectApplier.applySkill(player, skill);
            }
        }
    }

    public void openSkillTree(ServerPlayerEntity player) {
        if (SkillTreeNetworking.sendSkillTree(player, this)) {
            return;
        }
        openLegacySkillTree(player);
    }

    private void openLegacySkillTree(ServerPlayerEntity player) {
        Inventory inventory = buildInventory(player);
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("\u00A7c\u00A7l\u00C1rbol de Habilidades");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity playerEntity) {
                return new SkillTreeScreenHandler(syncId, playerInv, inventory, SkillTreeManager.this, playerEntity);
            }
        });
    }

    public Inventory buildInventory(ServerPlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(54);
        UUID uuid = player.getUuid();

        for (Skill skill : Skill.values()) {
            int slot = getSlotForSkill(skill);
            if (slot >= 0 && slot < 54) {
                inv.setStack(slot, createSkillStack(player, skill));
            }
        }

        for (int i = 0; i < 54; i++) {
            if (inv.getStack(i).isEmpty()) {
                if (i == 45) {
                    inv.setStack(i, createInfoStack(player));
                } else if (i == 53) {
                    inv.setStack(i, createProgressStack(player));
                } else {
                    inv.setStack(i, createPane("\u00A70"));
                }
            }
        }

        return inv;
    }

    private ItemStack createSkillStack(ServerPlayerEntity player, Skill skill) {
        boolean unlocked = hasSkill(player.getUuid(), skill);
        boolean canUnlock = canUnlock(player.getUuid(), skill);
        boolean canAfford = player.experienceLevel >= skill.getCost();

        MutableText name;
        if (unlocked) {
            name = Text.literal("\u00A7a\u2714 " + skill.getDisplayName());
        } else if (canUnlock && canAfford) {
            name = Text.literal("\u00A7e\u25B6 " + skill.getDisplayName());
        } else if (canUnlock) {
            name = Text.literal("\u00A7c\u2716 " + skill.getDisplayName());
        } else {
            name = Text.literal("\u00A78\u2716 " + skill.getDisplayName());
        }

        ItemStack stack = new ItemStack(skill.getIcon());
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = new NbtCompound();
        display.putString("Name", Text.Serialization.toJsonString(name));

        NbtList loreList = new NbtList();
        if (unlocked) {
            loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A7a\u2714 Desbloqueado"))));
            loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(""))));
            loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(skill.getDescription()))));
        } else {
            loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(skill.getDescription()))));
            loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(""))));

            if (skill.getPrerequisite() != null) {
                boolean prereqMet = hasSkill(player.getUuid(), skill.getPrerequisite());
                String prereqColor = prereqMet ? "\u00A7a" : "\u00A7c";
                loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(
                    prereqColor + "Requisito: " + skill.getPrerequisite().getDisplayName()))));
            }

            loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(""))));

            if (skill.getCost() > 0) {
                String costColor = canAfford ? "\u00A7a" : "\u00A7c";
                loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(
                    costColor + "Costo: " + skill.getCost() + " niveles de EXP"))));
            } else {
                loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(
                    "\u00A7aCosto: GRATIS"))));
            }

            if (!canUnlock) {
                loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(""))));
                loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(
                    "\u00A7c\u2716 Requisito no cumplido"))));
            } else if (!canAfford) {
                loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(""))));
                loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(
                    "\u00A7cNecesitas " + skill.getCost() + " niveles (tienes " + player.experienceLevel + ")"))));
            }
        }

        display.put("Lore", loreList);
        nbt.put("display", display);

        return stack;
    }

    private ItemStack createInfoStack(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(Items.PAPER);
        int totalUnlocked = 0;
        for (Skill s : Skill.values()) {
            if (hasSkill(player.getUuid(), s)) totalUnlocked++;
        }

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = new NbtCompound();
        display.putString("Name", Text.Serialization.toJsonString(Text.literal("\u00A7e\u00A7lTu Progreso")));

        NbtList loreList = new NbtList();
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A77Habilidades: \u00A7e" + totalUnlocked + "/" + Skill.values().length))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A77Niveles: \u00A7e" + player.experienceLevel))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal(""))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A77Click en una habilidad"))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A77para desbloquearla."))));

        display.put("Lore", loreList);
        nbt.put("display", display);

        return stack;
    }

    private ItemStack createProgressStack(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int vidaCount = 0, fuerzaCount = 0, resistCount = 0, agilidadCount = 0, tenacidadCount = 0, cazadorCount = 0, supervivenciaCount = 0;
        for (Skill s : Skill.values()) {
            if (!hasSkill(uuid, s) || s.isRoot()) continue;
            String id = s.getId();
            if (id.startsWith("vida_")) vidaCount++;
            else if (id.startsWith("fuerza_")) fuerzaCount++;
            else if (id.startsWith("resistencia_")) resistCount++;
            else if (id.startsWith("agilidad_")) agilidadCount++;
            else if (id.startsWith("tenacidad_")) tenacidadCount++;
            else if (id.startsWith("cazador_")) cazadorCount++;
            else if (id.startsWith("supervivencia_")) supervivenciaCount++;
        }

        ItemStack stack = new ItemStack(Items.BOOK);
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = new NbtCompound();
        display.putString("Name", Text.Serialization.toJsonString(Text.literal("\u00A7e\u00A7lProgreso por Rama")));

        NbtList loreList = new NbtList();
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A7c\u2764 Vida: \u00A7f" + vidaCount + "/5"))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A79\u2694 Fuerza: \u00A7f" + fuerzaCount + "/5"))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A7b\u26E8 Resistencia: \u00A7f" + resistCount + "/5"))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A7a\u27A4 Agilidad: \u00A7f" + agilidadCount + "/5"))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A7d\u25C6 Tenacidad: \u00A7f" + tenacidadCount + "/5"))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A76\u2691 Cazador: \u00A7f" + cazadorCount + "/5"))));
        loreList.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("\u00A72\u2726 Supervivencia: \u00A7f" + supervivenciaCount + "/5"))));

        display.put("Lore", loreList);
        nbt.put("display", display);

        return stack;
    }

    private ItemStack createPane(String color) {
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        NbtCompound nbt = pane.getOrCreateNbt();
        NbtCompound display = new NbtCompound();
        display.putString("Name", Text.Serialization.toJsonString(Text.literal(color + " ")));
        nbt.put("display", display);
        return pane;
    }

    public int getSlotForSkill(Skill skill) {
        switch (skill) {
            case BASE: return 49;
            case VIDA_T1: return 37;
            case VIDA_T2: return 28;
            case VIDA_T3: return 19;
            case VIDA_T4: return 10;
            case VIDA_T5: return 1;
            case FUERZA_T1: return 40;
            case FUERZA_T2: return 31;
            case FUERZA_T3: return 22;
            case FUERZA_T4: return 13;
            case FUERZA_T5: return 4;
            case RESISTENCIA_T1: return 43;
            case RESISTENCIA_T2: return 34;
            case RESISTENCIA_T3: return 25;
            case RESISTENCIA_T4: return 16;
            case RESISTENCIA_T5: return 7;
            case AGILIDAD_T1: return 39;
            case AGILIDAD_T2: return 30;
            case AGILIDAD_T3: return 21;
            case AGILIDAD_T4: return 12;
            case AGILIDAD_T5: return 3;
            case TENACIDAD_T1: return 41;
            case TENACIDAD_T2: return 32;
            case TENACIDAD_T3: return 23;
            case TENACIDAD_T4: return 14;
            case TENACIDAD_T5: return 5;
            case CAZADOR_T1: return 38;
            case CAZADOR_T2: return 29;
            case CAZADOR_T3: return 20;
            case CAZADOR_T4: return 11;
            case CAZADOR_T5: return 2;
            case SUPERVIVENCIA_T1: return 42;
            case SUPERVIVENCIA_T2: return 33;
            case SUPERVIVENCIA_T3: return 24;
            case SUPERVIVENCIA_T4: return 15;
            case SUPERVIVENCIA_T5: return 6;
            default: return -1;
        }
    }

    public Skill getSkillForSlot(int slot) {
        for (Skill skill : Skill.values()) {
            if (getSlotForSkill(skill) == slot) return skill;
        }
        return null;
    }

    public void reopenInventory(ServerPlayerEntity player) {
        openSkillTree(player);
    }

    public MCExtremo getMod() { return mod; }
}
