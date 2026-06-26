package com.egologic.mcextremo.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

@me.shedaniel.autoconfig.annotation.Config(name = "mcextremo")
public class ModConfig implements ConfigData {

    public Vidas vidas = new Vidas();
    public Baneo baneo = new Baneo();
    public Mensajes mensajes = new Mensajes();
    public PvPProgramado pvpProgramado = new PvPProgramado();
    public Hardcore hardcore = new Hardcore();
    public Scoreboard scoreboard = new Scoreboard();
    public Zombies zombies = new Zombies();
    public SkillTree skillTree = new SkillTree();
    public Corazones corazones = new Corazones();
    public Mejoras mejoras = new Mejoras();
    public ReviveTrial reviveTrial = new ReviveTrial();
    public EventTrial eventTrial = new EventTrial();

    public static class Vidas {
        public int defaultLives = 3;
        public boolean perderEnMuerteNatural = true;
    }

    public static class Baneo {
        public boolean activado = true;
        public String mensaje = "&cHas perdido tus vidas y has sido expulsado del servidor.\n&7Pide a un admin que te revivan con /mce revive <tu_nombre>";
    }

    public static class Mensajes {
        public String perdidaVida = "&e{jugador} &7ha perdido una vida. Le quedan &c{vidas} &7vida(s).";
        public String sinVidas = "&4{jugador} &7se ha quedado sin vidas y ha sido eliminado del servidor.";
        public String vidasRestantes = "&7Tienes &c{vidas} &7vida(s) restantes.";
    }

    public static class PvPProgramado {
        public boolean activado = true;
        public int minutosPvp = 20;
        public int minutosNoPvp = 20;
        public String mensajePvpOn = "&c&lEL PVP SE HA ACTIVADO! &7Cuidado con los demas jugadores.";
        public String mensajePvpOff = "&a&lEL PVP SE HA DESACTIVADO! &7Ya puedes jugar tranquilo.";
    }

    public static class Hardcore {
        public boolean activado = true;
        public boolean primeraEjecucion = true;
        public String difficulty = "HARD";
        public boolean pvp = true;
        public String gamemode = "survival";
        public boolean keepInventory = false;
        public boolean doDaylightCycle = true;
        public boolean doWeatherCycle = true;
    }

    public static class Scoreboard {
        public boolean activado = true;
        public String titulo = "&c&lMCExtremo";
        public String lineaSeparador = "&7&m                           ";
    }

    public static class Zombies {
        public Inteligencia inteligencia = new Inteligencia();
        public Agresividad agresividad = new Agresividad();
        public RomperBloques romperBloques = new RomperBloques();
        public ConstruirBloques construirBloques = new ConstruirBloques();
        public Horda horda = new Horda();

        public static class Inteligencia {
            public boolean activado = true;
            public boolean abrirPuertas = true;
            public boolean romperPuertas = true;
            public boolean saltarAlJugador = true;
            public boolean coordinarAtaque = true;
            public int rangoCoordinacion = 15;
        }

        public static class Agresividad {
            public boolean activado = true;
            public boolean escalarPorDias = true;
        }

        public static class RomperBloques {
            public boolean activado = true;
            public int diaInicio = 26;
            public int radio = 3;
            public int tiempoBase = 50;
            public boolean romperSoloBloquesBlandos = true;
            public boolean permitirDestruirContenedores = false;
        }

        public static class ConstruirBloques {
            public boolean activado = true;
            public int diaInicio = 46;
            public int alturaMaxima = 5;
            public int radioBusqueda = 10;
            public int distanciaMinima = 2;
            public int distanciaMaxima = 12;
            public int pausaEntreBloquesTicks = 10;
            public int ticksAtascado = 35;
            public int maxIntentosAtascado = 3;
            public int cooldownFracasoTicks = 120;
            public boolean colocarSoloSiHaySoporte = true;
            public String bloqueConstruccion = "dirt";
        }

        public static class Horda {
            public boolean activado = true;
            public int diaInicio = 4;
            public int radioActivacion = 20;
            public int radioMaximo = 96;
            public int maxGlobal = 35;
            public int cooldownSegundos = 45;

            public int miniMin = 2;
            public int miniMax = 4;
            public int miniDuracion = 30;

            public int hordaMin = 3;
            public int hordaMax = 7;
            public int hordaDuracion = 45;

            public int grandeMin = 6;
            public int grandeMax = 12;
            public int grandeDuracion = 60;

            public int masivaMin = 10;
            public int masivaMax = 18;
            public int masivaDuracion = 75;

            public int extremaMin = 16;
            public int extremaMax = 24;
            public int extremaDuracion = 90;

            public int expMini = 5;
            public int expHorda = 15;
            public int expGrande = 50;
            public int expMasiva = 80;
            public int expExtrema = 120;

            public boolean recompensasActivado = true;
            public int materialesMini = 1;
            public int materialesHorda = 2;
            public int materialesGrande = 4;
            public int materialesMasiva = 5;
            public int materialesExtrema = 7;
            public double corazonChanceMini = 0.08;
            public double corazonChanceHorda = 0.14;
            public double corazonChanceGrande = 0.24;
            public double corazonChanceMasiva = 0.34;
            public double corazonChanceExtrema = 0.45;
        }
    }

    public static class SkillTree {
        public boolean activado = true;
        public int costoTier1 = 3;
        public int costoTier2 = 7;
        public int costoTier3 = 14;
        public int costoTier4 = 22;
        public int costoTier5 = 35;
    }

    public static class Corazones {
        public boolean activado = true;
        public double hpPorCorazon = 2.0;
        public int maxCorazonesPorJugador = 10;
    }

    public static class Mejoras {
        public boolean activado = true;
        public int materialesPorNivel = 4;
        public int nivelMaxProteccion = 4;
        public int nivelMaxIrrompibilidad = 3;
        public int nivelMaxFilo = 5;
        public int nivelMaxEficiencia = 5;
        public int nivelMaxPoder = 5;
        public int nivelMaxCargaRapida = 3;
    }

    public static class ReviveTrial {
        public boolean activado = true;
        public boolean trialVoluntarioActivado = true;
        public int trialVoluntarioCooldownMinutos = 30;
        public int vidasAlGanarVoluntario = 1;
        public int vidasAlGanar = 2;
        public boolean banAlFallar = true;
        public String world = "minecraft:the_end";
        public double x = 0.5;
        public double y = 100.0;
        public double z = 0.5;
        public float yaw = 0.0f;
        public float pitch = 0.0f;
        public int radioArena = 42;
        public int oleadas = 5;
        public int maxIntentos = 2;
        public int distanciaEntreIslas = 512;
        public boolean limpiarIslaAlTerminar = true;
        public int preparacionSegundos = 5;
        public int segundosAntesDeRevivir = 8;
        public int zombiesOleada1 = 4;
        public int zombiesOleada2 = 5;
        public int zombiesOleada3 = 7;
        public int zombiesOleada4 = 8;
        public double jefeOleada5VidaExtra = 60.0;
        public double jefeOleada5DanoExtra = 2.5;
        public double jefeOleada5ArmaduraExtra = 6.0;
    }

    public static class EventTrial {
        public boolean activado = true;
        public int radioArena = 80;
        public int oleadas = 6;
        public int vidasAlGanar = 1;
        public int tokensPorJugador = 3;
        public int maxMobsActivos = 120;
        public boolean limpiarArenaAlTerminar = true;
        public int preparacionSegundos = 10;
        public int segundosAntesDeSalir = 8;
        public boolean introActivada = true;
        public int introAltura = 34;
        public int introDuracionTicks = 120;
        public int introRadioSpawn = 12;
        public boolean introHazLuz = true;
        public boolean introCirculoFuego = true;
        public int distanciaEntreArenas = 4096;
        public double x = 0.5;
        public double y = 120.0;
        public double z = 0.5;
    }

    private static ModConfig instance;

    public static void register() {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        instance = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        validate();
    }

    public static ModConfig get() {
        if (instance == null) {
            instance = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        }
        return instance;
    }

    public static void reload() {
        instance = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        validate();
    }

    public static void validate() {
        ModConfig config = get();
        config.vidas.defaultLives = Math.max(1, config.vidas.defaultLives);

        config.pvpProgramado.minutosPvp = Math.max(1, config.pvpProgramado.minutosPvp);
        config.pvpProgramado.minutosNoPvp = Math.max(1, config.pvpProgramado.minutosNoPvp);

        config.zombies.romperBloques.diaInicio = Math.max(1, config.zombies.romperBloques.diaInicio);
        config.zombies.romperBloques.radio = clamp(config.zombies.romperBloques.radio, 1, 8);
        config.zombies.romperBloques.tiempoBase = clamp(config.zombies.romperBloques.tiempoBase, 10, 200);

        ModConfig.Zombies.ConstruirBloques build = config.zombies.construirBloques;
        build.diaInicio = Math.max(1, build.diaInicio);
        build.alturaMaxima = clamp(build.alturaMaxima, 1, 8);
        build.radioBusqueda = clamp(build.radioBusqueda, 4, 24);
        build.distanciaMinima = clamp(build.distanciaMinima, 1, 6);
        build.distanciaMaxima = clamp(build.distanciaMaxima, build.distanciaMinima + 1, 24);
        build.pausaEntreBloquesTicks = clamp(build.pausaEntreBloquesTicks, 4, 40);
        build.ticksAtascado = clamp(build.ticksAtascado, 10, 120);
        build.maxIntentosAtascado = clamp(build.maxIntentosAtascado, 1, 8);
        build.cooldownFracasoTicks = clamp(build.cooldownFracasoTicks, 20, 600);

        ModConfig.Zombies.Horda horda = config.zombies.horda;
        horda.diaInicio = Math.max(1, horda.diaInicio);
        horda.radioActivacion = clamp(horda.radioActivacion, 8, 64);
        horda.radioMaximo = clamp(horda.radioMaximo, horda.radioActivacion, 192);
        horda.maxGlobal = clamp(horda.maxGlobal, 1, 150);
        horda.cooldownSegundos = clamp(horda.cooldownSegundos, 5, 600);
        horda.miniMax = Math.max(horda.miniMin, horda.miniMax);
        horda.hordaMax = Math.max(horda.hordaMin, horda.hordaMax);
        horda.grandeMax = Math.max(horda.grandeMin, horda.grandeMax);
        horda.masivaMax = Math.max(horda.masivaMin, horda.masivaMax);
        horda.extremaMax = Math.max(horda.extremaMin, horda.extremaMax);
        horda.materialesMini = Math.max(0, horda.materialesMini);
        horda.materialesHorda = Math.max(0, horda.materialesHorda);
        horda.materialesGrande = Math.max(0, horda.materialesGrande);
        horda.materialesMasiva = Math.max(0, horda.materialesMasiva);
        horda.materialesExtrema = Math.max(0, horda.materialesExtrema);
        if (isSame(horda.corazonChanceMini, 0.02)) horda.corazonChanceMini = 0.08;
        if (isSame(horda.corazonChanceHorda, 0.05)) horda.corazonChanceHorda = 0.14;
        if (isSame(horda.corazonChanceGrande, 0.10)) horda.corazonChanceGrande = 0.24;
        if (isSame(horda.corazonChanceMasiva, 0.16)) horda.corazonChanceMasiva = 0.34;
        if (isSame(horda.corazonChanceExtrema, 0.25)) horda.corazonChanceExtrema = 0.45;
        horda.corazonChanceMini = clampDouble(horda.corazonChanceMini, 0.0, 1.0);
        horda.corazonChanceHorda = clampDouble(horda.corazonChanceHorda, 0.0, 1.0);
        horda.corazonChanceGrande = clampDouble(horda.corazonChanceGrande, 0.0, 1.0);
        horda.corazonChanceMasiva = clampDouble(horda.corazonChanceMasiva, 0.0, 1.0);
        horda.corazonChanceExtrema = clampDouble(horda.corazonChanceExtrema, 0.0, 1.0);

        config.corazones.hpPorCorazon = clampDouble(config.corazones.hpPorCorazon, 1.0, 20.0);
        config.corazones.maxCorazonesPorJugador = clamp(config.corazones.maxCorazonesPorJugador, 0, 50);

        config.mejoras.materialesPorNivel = clamp(config.mejoras.materialesPorNivel, 1, 64);
        config.mejoras.nivelMaxProteccion = clamp(config.mejoras.nivelMaxProteccion, 1, 10);
        config.mejoras.nivelMaxIrrompibilidad = clamp(config.mejoras.nivelMaxIrrompibilidad, 1, 10);
        config.mejoras.nivelMaxFilo = clamp(config.mejoras.nivelMaxFilo, 1, 10);
        config.mejoras.nivelMaxEficiencia = clamp(config.mejoras.nivelMaxEficiencia, 1, 10);
        config.mejoras.nivelMaxPoder = clamp(config.mejoras.nivelMaxPoder, 1, 10);
        config.mejoras.nivelMaxCargaRapida = clamp(config.mejoras.nivelMaxCargaRapida, 1, 5);

        config.reviveTrial.trialVoluntarioCooldownMinutos = clamp(config.reviveTrial.trialVoluntarioCooldownMinutos, 1, 1440);
        config.reviveTrial.vidasAlGanarVoluntario = clamp(config.reviveTrial.vidasAlGanarVoluntario, 1, config.vidas.defaultLives);
        config.reviveTrial.vidasAlGanar = clamp(config.reviveTrial.vidasAlGanar, Math.min(2, config.vidas.defaultLives), config.vidas.defaultLives);
        config.reviveTrial.radioArena = clamp(config.reviveTrial.radioArena, 40, 72);
        config.reviveTrial.oleadas = 5;
        config.reviveTrial.maxIntentos = clamp(config.reviveTrial.maxIntentos, 1, 5);
        config.reviveTrial.distanciaEntreIslas = clamp(config.reviveTrial.distanciaEntreIslas, 128, 4096);
        config.reviveTrial.preparacionSegundos = clamp(config.reviveTrial.preparacionSegundos, 1, 30);
        config.reviveTrial.segundosAntesDeRevivir = clamp(config.reviveTrial.segundosAntesDeRevivir, 1, 60);
        if (config.reviveTrial.zombiesOleada1 == 5
            && config.reviveTrial.zombiesOleada2 == 7
            && config.reviveTrial.zombiesOleada3 == 9
            && config.reviveTrial.zombiesOleada4 == 11) {
            config.reviveTrial.zombiesOleada1 = 4;
            config.reviveTrial.zombiesOleada2 = 5;
            config.reviveTrial.zombiesOleada3 = 7;
            config.reviveTrial.zombiesOleada4 = 8;
        }
        if (isSame(config.reviveTrial.jefeOleada5VidaExtra, 80.0)) {
            config.reviveTrial.jefeOleada5VidaExtra = 60.0;
        }
        if (isSame(config.reviveTrial.jefeOleada5DanoExtra, 4.0)) {
            config.reviveTrial.jefeOleada5DanoExtra = 2.5;
        }
        if (isSame(config.reviveTrial.jefeOleada5ArmaduraExtra, 8.0)) {
            config.reviveTrial.jefeOleada5ArmaduraExtra = 6.0;
        }
        config.reviveTrial.zombiesOleada1 = clamp(config.reviveTrial.zombiesOleada1, 1, 30);
        config.reviveTrial.zombiesOleada2 = clamp(config.reviveTrial.zombiesOleada2, 1, 40);
        config.reviveTrial.zombiesOleada3 = clamp(config.reviveTrial.zombiesOleada3, 1, 50);
        config.reviveTrial.zombiesOleada4 = clamp(config.reviveTrial.zombiesOleada4, 1, 60);
        config.reviveTrial.jefeOleada5VidaExtra = clampDouble(config.reviveTrial.jefeOleada5VidaExtra, 20.0, 400.0);
        config.reviveTrial.jefeOleada5DanoExtra = clampDouble(config.reviveTrial.jefeOleada5DanoExtra, 0.0, 30.0);
        config.reviveTrial.jefeOleada5ArmaduraExtra = clampDouble(config.reviveTrial.jefeOleada5ArmaduraExtra, 0.0, 30.0);

        config.eventTrial.radioArena = clamp(config.eventTrial.radioArena, 60, 120);
        config.eventTrial.oleadas = 6;
        config.eventTrial.vidasAlGanar = clamp(config.eventTrial.vidasAlGanar, 1, config.vidas.defaultLives);
        config.eventTrial.tokensPorJugador = clamp(config.eventTrial.tokensPorJugador, 1, 8);
        if (config.eventTrial.maxMobsActivos == 90) {
            config.eventTrial.maxMobsActivos = 120;
        }
        config.eventTrial.maxMobsActivos = clamp(config.eventTrial.maxMobsActivos, 30, 160);
        config.eventTrial.preparacionSegundos = clamp(config.eventTrial.preparacionSegundos, 3, 60);
        config.eventTrial.segundosAntesDeSalir = clamp(config.eventTrial.segundosAntesDeSalir, 3, 60);
        config.eventTrial.introAltura = clamp(config.eventTrial.introAltura, 12, 80);
        config.eventTrial.introDuracionTicks = clamp(config.eventTrial.introDuracionTicks, 40, 240);
        config.eventTrial.introRadioSpawn = clamp(config.eventTrial.introRadioSpawn, 6, 32);
        config.eventTrial.distanciaEntreArenas = clamp(config.eventTrial.distanciaEntreArenas, 512, 20000);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isSame(double value, double expected) {
        return Math.abs(value - expected) < 0.0001;
    }

    public static Map<String, String> getAllConfig() {
        Map<String, String> result = new LinkedHashMap<>();
        flattenObject("", get(), result);
        return result;
    }

    private static void flattenObject(String prefix, Object obj, Map<String, String> result) {
        for (Field field : obj.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                String key = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
                if (value == null || isPrimitiveOrString(value)) {
                    result.put(key, String.valueOf(value));
                } else if (isConfigClass(value)) {
                    flattenObject(key, value, result);
                }
            } catch (IllegalAccessException ignored) {}
        }
    }

    public static String getConfigValue(String path) {
        String[] parts = path.split("\\.");
        Object current = get();
        for (String part : parts) {
            Field field = findField(current.getClass(), part);
            if (field == null) return null;
            try {
                field.setAccessible(true);
                current = field.get(current);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return current != null ? String.valueOf(current) : null;
    }

    public static boolean setConfigValue(String path, String value) {
        String[] parts = path.split("\\.");
        Object current = get();
        for (int i = 0; i < parts.length - 1; i++) {
            Field field = findField(current.getClass(), parts[i]);
            if (field == null) return false;
            try {
                field.setAccessible(true);
                current = field.get(current);
            } catch (IllegalAccessException e) {
                return false;
            }
        }
        Field field = findField(current.getClass(), parts[parts.length - 1]);
        if (field == null) return false;
        try {
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (type == boolean.class || type == Boolean.class) {
                field.set(current, Boolean.parseBoolean(value));
            } else if (type == int.class || type == Integer.class) {
                field.set(current, Integer.parseInt(value));
            } else if (type == double.class || type == Double.class) {
                field.set(current, Double.parseDouble(value));
            } else if (type == float.class || type == Float.class) {
                field.set(current, Float.parseFloat(value));
            } else if (type == long.class || type == Long.class) {
                field.set(current, Long.parseLong(value));
            } else if (type == String.class) {
                field.set(current, value);
            } else {
                return false;
            }
            validate();
            AutoConfig.getConfigHolder(ModConfig.class).save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static boolean isPrimitiveOrString(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean;
    }

    private static boolean isConfigClass(Object obj) {
        String name = obj.getClass().getName();
        return name.contains("ModConfig$");
    }
}
