package com.gillodaby.betterscoreboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BetterScoreBoardConfig {

    private static final int HARD_MAX_LINES = 12;

    private final String title;
    private final String logoTexturePath;
    private final int maxLines;
    private final long refreshMillis;
    private final List<String> lines;
    private final Path dataDir;
    private final int offsetRight;
    private final int offsetTop;
    private final boolean dividerVisible;

    private BetterScoreBoardConfig(String title, String logoTexturePath, int maxLines, long refreshMillis, List<String> lines, Path dataDir, int offsetRight, int offsetTop, boolean dividerVisible) {
        this.title = title;
        this.logoTexturePath = logoTexturePath;
        this.maxLines = maxLines;
        this.refreshMillis = refreshMillis;
        this.lines = lines;
        this.dataDir = dataDir;
        this.offsetRight = offsetRight;
        this.offsetTop = offsetTop;
        this.dividerVisible = dividerVisible;
    }

    String title() {
        return title;
    }

    String logoTexturePath() {
        return logoTexturePath;
    }

    int maxLines() {
        return maxLines;
    }

    long refreshMillis() {
        return refreshMillis;
    }

    List<String> lines() {
        return lines;
    }

    Path dataDir() {
        return dataDir;
    }

    int offsetRight() {
        return offsetRight;
    }

    int offsetTop() {
        return offsetTop;
    }

    boolean showDivider() {
        return dividerVisible;
    }

    static BetterScoreBoardConfig load(Path dataDir) {
        if (dataDir == null) {
            dataDir = Path.of("BetterScoreBoard");
        }
        Path configPath = dataDir.resolve("config.yaml");
        BetterScoreBoardConfig defaults = defaults(dataDir);
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException ignored) {
        }

        if (!Files.exists(configPath)) {
            writeDefault(configPath, defaults);
            return defaults;
        }

        String title = defaults.title;
        String logoPath = defaults.logoTexturePath;
        int maxLines = defaults.maxLines;
        long refreshMillis = defaults.refreshMillis;
        List<String> parsedLines = new ArrayList<>();
        int offsetRight = defaults.offsetRight;
        int offsetTop = defaults.offsetTop;
        boolean dividerVisible = defaults.dividerVisible;

        boolean inLines = false;
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("lines:")) {
                    inLines = true;
                    continue;
                }
                if (inLines && line.startsWith("-")) {
                    String value = line.substring(1).trim();
                    value = trimQuotes(value);
                    if (!value.isEmpty()) {
                        parsedLines.add(value);
                    }
                    continue;
                }
                inLines = false;

                int sep = line.indexOf(':');
                if (sep < 0) {
                    continue;
                }

                String key = line.substring(0, sep).trim();
                String value = trimQuotes(line.substring(sep + 1).trim());
                switch (key) {
                    case "title" -> title = value;
                    case "logoTexturePath" -> {
                        if (!value.isEmpty()) {
                            logoPath = value;
                        }
                    }
                    case "refreshSeconds" -> {
                        try {
                            double seconds = Double.parseDouble(value);
                            refreshMillis = (long) Math.max(0.1 * 1000, seconds * 1000);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    case "maxLines" -> {
                        try {
                            maxLines = Integer.parseInt(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    case "offsetRight" -> {
                        try {
                            offsetRight = Integer.parseInt(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    case "offsetTop" -> {
                        try {
                            offsetTop = Integer.parseInt(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    case "dividerVisible", "showDivider" -> {
                        if (!value.isEmpty()) {
                            dividerVisible = Boolean.parseBoolean(value);
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[BetterScoreBoard] Failed to read config.yaml, using defaults: " + e.getMessage());
            return defaults;
        }

        if (parsedLines.isEmpty()) {
            parsedLines.addAll(defaults.lines);
        }

        int cappedLines = Math.max(1, Math.min(HARD_MAX_LINES, maxLines));
        long cappedRefresh = Math.max(250L, refreshMillis);

        return new BetterScoreBoardConfig(
            title,
            logoPath,
            cappedLines,
            cappedRefresh,
            Collections.unmodifiableList(parsedLines),
            dataDir,
            offsetRight,
            offsetTop,
            dividerVisible
        );
    }

    private static BetterScoreBoardConfig defaults(Path dataDir) {
        List<String> defaults = new ArrayList<>();
        defaults.add("[#aaffff]       *Welcome to : {server}*");
        defaults.add("[#0bec00]               *Current world: {world}*");
        defaults.add(" ");
        defaults.add("[#ffa500]                     *Online: {online}/{max_players}*");
        defaults.add("[#ff00ff]      *{player} | Playtime: {playtime}*");
        defaults.add(" ");
        defaults.add("[#cfe900]      * Coords: {pos_x}  {pos_y}  {pos_z}*");
        defaults.add("[#cfe900]       *         Money: {money}$ / TPS: {tps}*");
        defaults.add(" ");
        defaults.add("      *Join the Discord: discord.gg/hytale*");

        return new BetterScoreBoardConfig(
            "Better ScoreBoard",
            "Custom/Textures/BetterScoreBoard/logo.png",
            10,
            1000L,
            Collections.unmodifiableList(defaults),
            dataDir,
            24,
            140,
            true
        );
    }

    private static String trimQuotes(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        value = value.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            if (value.length() >= 2) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static void writeDefault(Path path, BetterScoreBoardConfig defaults) {
        List<String> lines = new ArrayList<>();
        lines.add("# Better ScoreBoard configuration");
        lines.add("title: \"" + defaults.title + "\"");
        lines.add("# Optional custom logo path (unused by default to avoid client restrictions)");
        lines.add("# HUD refresh rate (seconds). Use decimals for sub-second updates.");
        lines.add("refreshSeconds: 1.0");
        lines.add("# Maximum lines rendered (capped by the HUD layout)");
        lines.add("maxLines: " + defaults.maxLines);
        lines.add("# Show the divider line below the title");
        lines.add("dividerVisible: " + defaults.dividerVisible);
        lines.add("# Lines to render from top to bottom. Available placeholders:");
        lines.add("# {server} -> server name");
        lines.add("# {world} -> current world name");
        lines.add("# {online} -> number of tracked players");
        lines.add("# {max_players} -> server max players");
        lines.add("# {player} -> player display name");
        lines.add("# {playtime} -> time since join");
        lines.add("# {tps} -> server TPS (approx)");
        lines.add("# {money} -> uses EconomyPlugin when available");
        lines.add("# {balance} -> mirrors {money} when EconomyPlugin is installed");
        lines.add("lines:");
        for (String line : defaults.lines) {
            lines.add("  - \"" + line.replace("\"", "\\\"") + "\"");
        }

        persist(path, lines);
    }

    BetterScoreBoardConfig withLines(List<String> newLines) {
        return new BetterScoreBoardConfig(
                title,
                logoTexturePath,
                maxLines,
                refreshMillis,
                Collections.unmodifiableList(new ArrayList<>(newLines)),
                dataDir,
                offsetRight,
            offsetTop,
            dividerVisible
        );
    }

    BetterScoreBoardConfig withTitleAndLines(String newTitle, List<String> newLines) {
        String updatedTitle = newTitle != null && !newTitle.isEmpty() ? newTitle : title;
        return new BetterScoreBoardConfig(
                updatedTitle,
                logoTexturePath,
                maxLines,
                refreshMillis,
                Collections.unmodifiableList(new ArrayList<>(newLines)),
                dataDir,
                offsetRight,
            offsetTop,
            dividerVisible
        );
    }

    BetterScoreBoardConfig withOffsets(int newOffsetRight, int newOffsetTop) {
        return new BetterScoreBoardConfig(
                title,
                logoTexturePath,
                maxLines,
                refreshMillis,
                lines,
                dataDir,
                Math.max(0, newOffsetRight),
            Math.max(0, newOffsetTop),
            dividerVisible
        );
    }

    BetterScoreBoardConfig withDividerVisible(boolean visible) {
        return new BetterScoreBoardConfig(
                title,
                logoTexturePath,
                maxLines,
                refreshMillis,
                lines,
                dataDir,
                offsetRight,
                offsetTop,
                visible
        );
    }

    static void persist(BetterScoreBoardConfig cfg) {
        Path path = cfg.dataDir().resolve("config.yaml");
        List<String> lines = new ArrayList<>();
        lines.add("# Better ScoreBoard configuration");
        lines.add("title: \"" + cfg.title + "\"");
        lines.add("# Optional custom logo path (unused by default to avoid client restrictions)");
        lines.add("logoTexturePath: \"" + cfg.logoTexturePath + "\"");
        lines.add("# HUD refresh rate (seconds). Use decimals for sub-second updates.");
        lines.add("refreshSeconds: " + (cfg.refreshMillis / 1000.0));
        lines.add("# Maximum lines rendered (capped by the HUD layout)");
        lines.add("maxLines: " + cfg.maxLines);
        lines.add("# HUD offsets in pixels");
        lines.add("offsetRight: " + cfg.offsetRight);
        lines.add("offsetTop: " + cfg.offsetTop);
        lines.add("dividerVisible: " + cfg.dividerVisible);
        lines.add("# Lines to render from top to bottom. Available placeholders:");
        lines.add("# {server}, {world}, {online}, {max_players}, {player}, {playtime}, {tps}, {money}, {balance}");
        lines.add("lines:");
        for (String line : cfg.lines) {
            lines.add("  - \"" + line.replace("\"", "\\\"") + "\"");
        }
        persist(path, lines);
    }

    private static void persist(Path path, List<String> lines) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[BetterScoreBoard] Could not write config.yaml: " + e.getMessage());
        }
    }
}
