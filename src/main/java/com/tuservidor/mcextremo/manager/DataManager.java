package com.tuservidor.mcextremo.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tuservidor.mcextremo.MCExtremo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DataManager {
    private final MCExtremo mod;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFile;
    private Map<UUID, Integer> vidas = new HashMap<>();
    private Map<UUID, Integer> corazones = new HashMap<>();
    private Map<UUID, String> trialStates = new HashMap<>();
    private Map<UUID, List<String>> trialInventories = new HashMap<>();
    private Map<UUID, Long> voluntaryTrialCooldowns = new HashMap<>();
    private boolean hardcoreConfigured = false;
    private int realDay = 0;
    private int lastObservedWorldDay = -1;
    private MinecraftServer server;

    public DataManager(MCExtremo mod) {
        this.mod = mod;
        this.dataFile = FabricLoader.getInstance().getConfigDir().resolve("mcextremo").resolve("data.json");
    }

    public void load() {
        try {
            Files.createDirectories(dataFile.getParent());
            if (Files.exists(dataFile)) {
                String json = Files.readString(dataFile);
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> raw = gson.fromJson(json, type);
                if (raw == null) {
                    MCExtremo.LOGGER.warn("data.json vacio o invalido; usando datos por defecto.");
                    return;
                }

                if (raw.get("vidas") instanceof Map<?, ?> vidasRawAny) {
                    vidas.clear();
                    for (Map.Entry<?, ?> entry : vidasRawAny.entrySet()) {
                        if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Number value)) {
                            continue;
                        }
                        try {
                            vidas.put(UUID.fromString(key), value.intValue());
                        } catch (IllegalArgumentException e) {
                            MCExtremo.LOGGER.warn("UUID invalido en data.json: " + key);
                        }
                    }
                }
                if (raw.get("corazones") instanceof Map<?, ?> heartsRawAny) {
                    corazones.clear();
                    for (Map.Entry<?, ?> entry : heartsRawAny.entrySet()) {
                        if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Number value)) {
                            continue;
                        }
                        try {
                            corazones.put(UUID.fromString(key), Math.max(0, value.intValue()));
                        } catch (IllegalArgumentException e) {
                            MCExtremo.LOGGER.warn("UUID invalido en corazones de data.json: " + key);
                        }
                    }
                }
                if (raw.get("trialStates") instanceof Map<?, ?> statesRawAny) {
                    trialStates.clear();
                    for (Map.Entry<?, ?> entry : statesRawAny.entrySet()) {
                        if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String value)) {
                            continue;
                        }
                        try {
                            trialStates.put(UUID.fromString(key), value);
                        } catch (IllegalArgumentException e) {
                            MCExtremo.LOGGER.warn("UUID invalido en trialStates de data.json: " + key);
                        }
                    }
                }
                if (raw.get("trialInventories") instanceof Map<?, ?> inventoriesRawAny) {
                    trialInventories.clear();
                    for (Map.Entry<?, ?> entry : inventoriesRawAny.entrySet()) {
                        if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof List<?> value)) {
                            continue;
                        }
                        try {
                            List<String> slots = new ArrayList<>();
                            for (Object slot : value) {
                                slots.add(slot instanceof String text ? text : "");
                            }
                            trialInventories.put(UUID.fromString(key), slots);
                        } catch (IllegalArgumentException e) {
                            MCExtremo.LOGGER.warn("UUID invalido en trialInventories de data.json: " + key);
                        }
                    }
                }
                if (raw.get("voluntaryTrialCooldowns") instanceof Map<?, ?> cooldownsRawAny) {
                    voluntaryTrialCooldowns.clear();
                    for (Map.Entry<?, ?> entry : cooldownsRawAny.entrySet()) {
                        if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Number value)) {
                            continue;
                        }
                        try {
                            voluntaryTrialCooldowns.put(UUID.fromString(key), value.longValue());
                        } catch (IllegalArgumentException e) {
                            MCExtremo.LOGGER.warn("UUID invalido en voluntaryTrialCooldowns de data.json: " + key);
                        }
                    }
                }
                if (raw.get("hardcoreConfigured") instanceof Boolean configured) {
                    hardcoreConfigured = configured;
                }
                if (raw.get("realDay") instanceof Number day) {
                    realDay = Math.max(0, day.intValue());
                }
            }
            MCExtremo.LOGGER.info("Datos cargados: " + vidas.size() + " jugador(es).");
        } catch (Exception e) {
            MCExtremo.LOGGER.error("Error cargando datos", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            Map<String, Object> data = new HashMap<>();

            Map<String, Integer> vidasStr = new HashMap<>();
            for (Map.Entry<UUID, Integer> entry : vidas.entrySet()) {
                vidasStr.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("vidas", vidasStr);
            Map<String, Integer> corazonesStr = new HashMap<>();
            for (Map.Entry<UUID, Integer> entry : corazones.entrySet()) {
                corazonesStr.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("corazones", corazonesStr);

            Map<String, String> statesStr = new HashMap<>();
            for (Map.Entry<UUID, String> entry : trialStates.entrySet()) {
                statesStr.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("trialStates", statesStr);
            Map<String, List<String>> inventoriesStr = new HashMap<>();
            for (Map.Entry<UUID, List<String>> entry : trialInventories.entrySet()) {
                inventoriesStr.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
            }
            data.put("trialInventories", inventoriesStr);
            Map<String, Long> cooldownsStr = new HashMap<>();
            for (Map.Entry<UUID, Long> entry : voluntaryTrialCooldowns.entrySet()) {
                cooldownsStr.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("voluntaryTrialCooldowns", cooldownsStr);
            data.put("hardcoreConfigured", hardcoreConfigured);
            data.put("realDay", realDay);

            Files.writeString(dataFile, gson.toJson(data));
        } catch (Exception e) {
            MCExtremo.LOGGER.error("Error guardando datos", e);
        }
    }

    public int getVidas(UUID uuid) {
        return vidas.getOrDefault(uuid, mod.getLivesManager().getDefaultLives());
    }

    public Map<UUID, Integer> getAllVidas() {
        return new HashMap<>(vidas);
    }

    public int getCorazones(UUID uuid) {
        return corazones.getOrDefault(uuid, 0);
    }

    public void setCorazones(UUID uuid, int count) {
        corazones.put(uuid, Math.max(0, count));
        save();
    }

    public String getTrialState(UUID uuid) {
        return trialStates.getOrDefault(uuid, "ALIVE");
    }

    public void setTrialState(UUID uuid, String state) {
        trialStates.put(uuid, state);
        save();
    }

    public boolean hasTrialInventory(UUID uuid) {
        return trialInventories.containsKey(uuid);
    }

    public void saveTrialInventory(UUID uuid, List<ItemStack> slots) {
        List<String> serialized = new ArrayList<>();
        for (ItemStack stack : slots) {
            if (stack == null || stack.isEmpty()) {
                serialized.add("");
            } else {
                try {
                    serialized.add(serializeStack(stack));
                } catch (Exception e) {
                    MCExtremo.LOGGER.warn("No se pudo guardar un slot de inventario del trial para " + uuid, e);
                    serialized.add("");
                }
            }
        }
        trialInventories.put(uuid, serialized);
        save();
    }

    public List<ItemStack> loadTrialInventory(UUID uuid, int expectedSize) {
        List<String> serialized = trialInventories.get(uuid);
        if (serialized == null) return null;

        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < expectedSize; i++) {
            String value = i < serialized.size() ? serialized.get(i) : "";
            if (value == null || value.isBlank()) {
                slots.add(ItemStack.EMPTY);
                continue;
            }
            try {
                slots.add(ItemStack.fromNbt(deserializeStack(value)));
            } catch (Exception e) {
                MCExtremo.LOGGER.warn("No se pudo restaurar un slot de inventario del trial para " + uuid, e);
                slots.add(ItemStack.EMPTY);
            }
        }
        return slots;
    }

    public void clearTrialInventory(UUID uuid) {
        if (trialInventories.remove(uuid) != null) {
            save();
        }
    }

    public long getVoluntaryTrialCooldownUntil(UUID uuid) {
        return voluntaryTrialCooldowns.getOrDefault(uuid, 0L);
    }

    public void setVoluntaryTrialCooldownUntil(UUID uuid, long cooldownUntilMs) {
        voluntaryTrialCooldowns.put(uuid, Math.max(0L, cooldownUntilMs));
        save();
    }

    public void setVidas(UUID uuid, int count) {
        vidas.put(uuid, Math.max(0, count));
        save();
    }

    public boolean isHardcoreConfigured() {
        return hardcoreConfigured;
    }

    public void setHardcoreConfigured(boolean configured) {
        this.hardcoreConfigured = configured;
        save();
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public int getRealDay() {
        return realDay;
    }

    public void setRealDay(int day) {
        this.realDay = day;
    }

    public int getLastObservedWorldDay() {
        return lastObservedWorldDay;
    }

    public void setLastObservedWorldDay(int day) {
        this.lastObservedWorldDay = day;
    }

    private String serializeStack(ItemStack stack) throws Exception {
        NbtCompound nbt = stack.writeNbt(new NbtCompound());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, output);
        return "base64:" + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private NbtCompound deserializeStack(String value) throws Exception {
        if (value.startsWith("base64:")) {
            byte[] bytes = Base64.getDecoder().decode(value.substring("base64:".length()));
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtSizeTracker.ofUnlimitedBytes());
        }
        return StringNbtReader.parse(value);
    }
}
