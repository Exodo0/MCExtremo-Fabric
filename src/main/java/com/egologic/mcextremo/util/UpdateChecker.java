package com.egologic.mcextremo.util;

import com.egologic.mcextremo.MCExtremo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateChecker {
    private static final String RELEASE_API = "https://api.github.com/repos/Exodo0/MCExtremo-Fabric/releases/latest";
    private static final String RELEASE_PAGE = "https://github.com/Exodo0/MCExtremo-Fabric/releases/latest";
    private static final String JAR_SUFFIX = ".jar";
    private static final String SOURCES_MARKER = "sources";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MCExtremo Update Checker");
        thread.setDaemon(true);
        return thread;
    });

    private volatile UpdateInfo cached;
    private volatile long lastCheckMs;
    private volatile boolean checking;

    public CompletableFuture<UpdateInfo> checkAsync(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && cached != null && now - lastCheckMs < Duration.ofMinutes(30).toMillis()) {
            return CompletableFuture.completedFuture(cached);
        }
        if (checking && !force) {
            return CompletableFuture.completedFuture(cached != null ? cached : UpdateInfo.unknown(currentVersion()));
        }

        checking = true;
        return CompletableFuture.supplyAsync(this::fetchLatest, EXECUTOR)
            .whenComplete((info, error) -> {
                checking = false;
                lastCheckMs = System.currentTimeMillis();
                if (error != null) {
                    MCExtremo.LOGGER.warn("No se pudo revisar actualizaciones de MCExtremo: " + error.getMessage());
                    cached = UpdateInfo.failed(currentVersion());
                } else {
                    cached = info;
                }
            });
    }

    public UpdateInfo getCached() {
        return cached;
    }

    public void notifyIfUpdateAvailable(ServerPlayerEntity player) {
        UpdateInfo info = cached;
        if (info == null || !info.updateAvailable() || !player.hasPermissionLevel(2)) return;
        player.sendMessage(TextUtil.literal("&6MCExtremo &7tiene una actualizacion disponible: &ev" + info.latestVersion()), false);
        player.sendMessage(downloadMessage(info), false);
    }

    public void sendStatus(ServerCommandSource source, UpdateInfo info) {
        if (info.failed()) {
            source.sendFeedback(() -> TextUtil.literal("&cNo se pudo revisar GitHub Releases. Revisa la consola o tu conexion."), false);
            return;
        }
        if (!info.updateAvailable()) {
            source.sendFeedback(() -> TextUtil.literal("&aMCExtremo esta actualizado. &7Version local: &e" + info.currentVersion()), false);
            return;
        }
        source.sendFeedback(() -> TextUtil.literal("&6MCExtremo &7local: &e" + info.currentVersion() + " &7| latest: &av" + info.latestVersion()), false);
        source.sendFeedback(() -> downloadMessage(info), false);
    }

    private UpdateInfo fetchLatest() {
        String current = currentVersion();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASE_API))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MCExtremo/" + current)
                .GET()
                .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                MCExtremo.LOGGER.warn("GitHub Releases respondio HTTP " + response.statusCode());
                return UpdateInfo.failed(current);
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String latest = stripVersionPrefix(json.get("tag_name").getAsString());
            String page = json.has("html_url") ? json.get("html_url").getAsString() : RELEASE_PAGE;
            String download = findPrimaryJar(json.getAsJsonArray("assets")).orElse(page);
            boolean updateAvailable = compareVersions(latest, current) > 0;
            return new UpdateInfo(current, latest, page, download, updateAvailable, false);
        } catch (Exception e) {
            MCExtremo.LOGGER.warn("Error revisando actualizaciones de MCExtremo", e);
            return UpdateInfo.failed(current);
        }
    }

    private Optional<String> findPrimaryJar(JsonArray assets) {
        if (assets == null) return Optional.empty();
        for (JsonElement element : assets) {
            if (!element.isJsonObject()) continue;
            JsonObject asset = element.getAsJsonObject();
            String name = asset.has("name") ? asset.get("name").getAsString() : "";
            if (!name.endsWith(JAR_SUFFIX) || name.contains(SOURCES_MARKER)) continue;
            if (asset.has("browser_download_url")) {
                return Optional.of(asset.get("browser_download_url").getAsString());
            }
        }
        return Optional.empty();
    }

    private MutableText downloadMessage(UpdateInfo info) {
        return Text.literal("Descargar actualizacion")
            .formatted(Formatting.GREEN, Formatting.UNDERLINE)
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, info.downloadUrl()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(info.downloadUrl()))));
    }

    private String currentVersion() {
        return FabricLoader.getInstance()
            .getModContainer(MCExtremo.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("0.0.0");
    }

    private static String stripVersionPrefix(String version) {
        String clean = version == null ? "0.0.0" : version.trim();
        return clean.startsWith("v") || clean.startsWith("V") ? clean.substring(1) : clean;
    }

    private static int compareVersions(String left, String right) {
        int[] a = numericParts(stripVersionPrefix(left));
        int[] b = numericParts(stripVersionPrefix(right));
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int[] numericParts(String version) {
        String[] pieces = version.split("[^0-9]+");
        int[] result = new int[Math.max(1, pieces.length)];
        int index = 0;
        for (String piece : pieces) {
            if (piece.isBlank()) continue;
            try {
                result[index++] = Integer.parseInt(piece);
            } catch (NumberFormatException ignored) {
                result[index++] = 0;
            }
        }
        if (index == result.length) return result;
        int[] trimmed = new int[Math.max(1, index)];
        System.arraycopy(result, 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    public record UpdateInfo(
        String currentVersion,
        String latestVersion,
        String releaseUrl,
        String downloadUrl,
        boolean updateAvailable,
        boolean failed
    ) {
        static UpdateInfo unknown(String currentVersion) {
            return new UpdateInfo(currentVersion, currentVersion, RELEASE_PAGE, RELEASE_PAGE, false, false);
        }

        static UpdateInfo failed(String currentVersion) {
            return new UpdateInfo(currentVersion, currentVersion, RELEASE_PAGE, RELEASE_PAGE, false, true);
        }
    }
}
