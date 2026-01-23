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

    static final int MAX_PAGES = 12;
    private static final int HARD_MAX_LINES = 12;
    static final long MIN_REFRESH_MS = 1_000L;

    private final String title;
    private final String logoTexturePath;
    private final int maxLines;
    private final long refreshMillis;
    private final List<String> lines;
    private final List<PageConfig> pages;
    private final boolean rotationEnabled;
    private final int activePage;
    private final Path dataDir;
    private final int offsetRight;
    private final int offsetTop;
    private final boolean dividerVisible;
    private final boolean logoVisible;

    private BetterScoreBoardConfig(String title, String logoTexturePath, int maxLines, long refreshMillis, List<String> lines, List<PageConfig> pages, boolean rotationEnabled, int activePage, Path dataDir, int offsetRight, int offsetTop, boolean dividerVisible, boolean logoVisible) {
        this.title = title;
        this.logoTexturePath = logoTexturePath;
        this.maxLines = maxLines;
        this.refreshMillis = refreshMillis;
        this.lines = lines;
        this.pages = pages;
        this.rotationEnabled = rotationEnabled;
        this.activePage = activePage;
        this.dataDir = dataDir;
        this.offsetRight = offsetRight;
        this.offsetTop = offsetTop;
        this.dividerVisible = dividerVisible;
        this.logoVisible = logoVisible;
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

    List<PageConfig> pages() {
        return pages;
    }

    boolean rotationEnabled() {
        return rotationEnabled;
    }

    int activePage() {
        return activePage;
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

    boolean logoVisible() {
        return logoVisible;
    }

    PageConfig page(int index) {
        if (pages == null || pages.isEmpty()) {
            return null;
        }
        int safe = Math.max(0, Math.min(pages.size() - 1, index));
        return pages.get(safe);
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
        List<String> legacyLines = new ArrayList<>();
        int offsetRight = defaults.offsetRight;
        int offsetTop = defaults.offsetTop;
        boolean dividerVisible = defaults.dividerVisible;
        boolean logoVisible = defaults.logoVisible;
        boolean rotationEnabled = defaults.rotationEnabled;
        int activePage = defaults.activePage;

        @SuppressWarnings("unchecked")
        List<String>[] pageLines = new List[MAX_PAGES];
        @SuppressWarnings("unchecked")
        List<String>[] pageWorlds = new List[MAX_PAGES];
        String[] pageTitles = new String[MAX_PAGES];
        long[] pageDurations = new long[MAX_PAGES];
        long[] pageRefreshes = new long[MAX_PAGES];
        boolean[] pageTitleSet = new boolean[MAX_PAGES];
        for (int i = 0; i < MAX_PAGES; i++) {
            PageConfig page = defaults.pages.get(i);
            pageLines[i] = new ArrayList<>();
            pageWorlds[i] = new ArrayList<>(page.worlds());
            pageTitles[i] = page.title();
            pageDurations[i] = page.durationMillis();
            pageRefreshes[i] = page.refreshMillis();
        }

        boolean inLines = false;
        int inPageLines = -1;
        int inPageWorlds = -1;
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("lines:")) {
                    inLines = true;
                    inPageLines = -1;
                    inPageWorlds = -1;
                    continue;
                }
                int pageLinesIndex = parsePageLinesIndex(line);
                if (pageLinesIndex >= 0) {
                    inLines = false;
                    inPageLines = pageLinesIndex;
                    inPageWorlds = -1;
                    continue;
                }
                int pageWorldsIndex = parsePageWorldsIndex(line);
                if (pageWorldsIndex >= 0) {
                    inLines = false;
                    inPageLines = -1;
                    inPageWorlds = pageWorldsIndex;
                    continue;
                }
                if (line.startsWith("-") && (inLines || inPageLines >= 0)) {
                    String value = line.substring(1).trim();
                    value = trimQuotes(value);
                    if (value == null) {
                        value = "";
                    }
                    if (inLines) {
                        legacyLines.add(value);
                    } else if (inPageLines >= 0 && inPageLines < MAX_PAGES) {
                        pageLines[inPageLines].add(value);
                    }
                    continue;
                }
                if (line.startsWith("-") && inPageWorlds >= 0) {
                    String value = line.substring(1).trim();
                    value = trimQuotes(value);
                    if (!value.isEmpty() && inPageWorlds < MAX_PAGES) {
                        pageWorlds[inPageWorlds].add(value);
                    }
                    continue;
                }
                inLines = false;
                inPageLines = -1;
                inPageWorlds = -1;

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
                            if (seconds <= 0) {
                                refreshMillis = 0L;
                            } else {
                                long millis = (long) (seconds * 1000);
                                refreshMillis = Math.max(MIN_REFRESH_MS, millis);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    case "rotationEnabled" -> {
                        if (!value.isEmpty()) {
                            rotationEnabled = Boolean.parseBoolean(value);
                        }
                    }
                    case "activePage" -> {
                        try {
                            activePage = Integer.parseInt(value);
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
                    case "logoVisible", "showLogo" -> {
                        if (!value.isEmpty()) {
                            logoVisible = Boolean.parseBoolean(value);
                        }
                    }
                    default -> {
                        int pageTitleIndex = parsePageTitleIndex(key);
                        if (pageTitleIndex >= 0 && pageTitleIndex < MAX_PAGES) {
                            pageTitles[pageTitleIndex] = value;
                            pageTitleSet[pageTitleIndex] = true;
                            continue;
                        }
                        int pageDurationIndex = parsePageDurationIndex(key);
                        if (pageDurationIndex >= 0 && pageDurationIndex < MAX_PAGES) {
                            try {
                                double seconds = Double.parseDouble(value);
                                pageDurations[pageDurationIndex] = (long) Math.max(1_000, seconds * 1000);
                            } catch (NumberFormatException ignored) {
                            }
                            continue;
                        }
                        int pageRefreshIndex = parsePageRefreshIndex(key);
                        if (pageRefreshIndex >= 0 && pageRefreshIndex < MAX_PAGES) {
                            try {
                                double seconds = Double.parseDouble(value);
                                if (seconds <= 0) {
                                    pageRefreshes[pageRefreshIndex] = 0L;
                                } else {
                                    long millis = (long) (seconds * 1000);
                                    pageRefreshes[pageRefreshIndex] = Math.max(MIN_REFRESH_MS, millis);
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[BetterScoreBoard] Failed to read config.yaml, using defaults: " + e.getMessage());
            return defaults;
        }

        if (!legacyLines.isEmpty()) {
            pageLines[0].clear();
            pageLines[0].addAll(legacyLines);
        }
        if (!pageTitleSet[0] && title != null && !title.isEmpty()) {
            pageTitles[0] = title;
        }

        int cappedLines = Math.max(1, Math.min(HARD_MAX_LINES, maxLines));
        long cappedRefresh = refreshMillis <= 0 ? 0L : Math.max(MIN_REFRESH_MS, refreshMillis);
        int cappedActivePage = Math.max(1, Math.min(MAX_PAGES, activePage));

        List<PageConfig> resolvedPages = new ArrayList<>();
        for (int i = 0; i < MAX_PAGES; i++) {
            List<String> lines = new ArrayList<>(pageLines[i]);
            List<String> worlds = normalizeWorldList(pageWorlds[i]);
            if (lines.isEmpty() && i == 0) {
                lines.addAll(defaults.pages.get(0).lines());
            }
            resolvedPages.add(new PageConfig(
                pageTitles[i],
                Collections.unmodifiableList(lines),
                Math.max(1_000, pageDurations[i]),
                pageRefreshes[i] <= 0 ? 0L : Math.max(MIN_REFRESH_MS, pageRefreshes[i]),
                Collections.unmodifiableList(worlds)
            ));
        }

        List<String> legacyPageLines = resolvedPages.get(0).lines();

        return new BetterScoreBoardConfig(
            title,
            logoPath,
            cappedLines,
            cappedRefresh,
            Collections.unmodifiableList(new ArrayList<>(legacyPageLines)),
            Collections.unmodifiableList(resolvedPages),
            rotationEnabled,
            cappedActivePage,
            dataDir,
            offsetRight,
            offsetTop,
            dividerVisible,
            logoVisible
        );
    }

    private static BetterScoreBoardConfig defaults(Path dataDir) {
        List<String> defaultLines = new ArrayList<>();
        defaultLines.add("[#aaffff]       *Welcome to : {server}*");
        defaultLines.add("[#0bec00]               *Current world: {world}*");
        defaultLines.add(" ");
        defaultLines.add("[#ffa500]                     *Online: {online}/{max_players}*");
        defaultLines.add("[#ff00ff]      *{player} | Playtime: {playtime}*");
        defaultLines.add(" ");
        defaultLines.add("[#cfe900]      * Coords: {pos_x}  {pos_y}  {pos_z}*");
        defaultLines.add("[#cfe900]       *         Money: {money}$ / TPS: {tps}*");
        defaultLines.add(" ");
        defaultLines.add("      *Join the Discord: discord.gg/hytale*");

        List<PageConfig> pages = new ArrayList<>();
        pages.add(new PageConfig("Better ScoreBoard", Collections.unmodifiableList(defaultLines), 8_000L, 2500L, Collections.emptyList()));
        for (int i = 1; i < MAX_PAGES; i++) {
            pages.add(new PageConfig("Page " + (i + 1), Collections.unmodifiableList(new ArrayList<>()), 8_000L, 2500L, Collections.emptyList()));
        }

        return new BetterScoreBoardConfig(
            "Better ScoreBoard",
            "Custom/Textures/BetterScoreBoard/logo.png",
            10,
            2500L,
            pages.get(0).lines(),
            Collections.unmodifiableList(pages),
            false,
            1,
            dataDir,
            24,
            140,
            true,
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
        lines.add("# HUD refresh rate (seconds). Minimum 1.0 (set 0 to disable updates).");
        lines.add("refreshSeconds: 2.5");
        lines.add("# Maximum lines rendered (capped by the HUD layout)");
        lines.add("maxLines: " + defaults.maxLines);
        lines.add("# Show the divider line below the title");
        lines.add("dividerVisible: " + defaults.dividerVisible);
        lines.add("# Show the logo image above the title");
        lines.add("logoVisible: " + defaults.logoVisible);
        lines.add("# Rotation of multiple pages");
        lines.add("rotationEnabled: " + defaults.rotationEnabled);
        lines.add("activePage: " + defaults.activePage);
        lines.add("# Lines to render from top to bottom. Available placeholders:");
        lines.add("# {server} -> server name");
        lines.add("# {world} -> current world name");
        lines.add("# {online} -> number of tracked players");
        lines.add("# {max_players} -> server max players");
        lines.add("# {player} -> player display name");
        lines.add("# {rank} -> LuckPerms primary group (optional)");
        lines.add("# {level} -> RPGLeveling level (optional)");
        lines.add("# {xp} -> RPGLeveling XP (optional)");
        lines.add("# {playtime} -> time since join");
        lines.add("# {totalplaytime} -> cumulative playtime stored across sessions");
        lines.add("# {tps} -> server TPS (approx)");
        lines.add("# {money} -> uses EconomyPlugin when available");
        lines.add("# {balance} -> mirrors {money} when EconomyPlugin is installed");
        lines.add("# {faction} -> HyFactions faction name (optional)");
        lines.add("# {faction_rank} -> HyFactions faction rank (optional)");
        lines.add("# {faction_tag} -> HyFactions faction tag (optional)");
        lines.add("# {power} -> HyFactions player power (optional)");
        lines.add("# {powermax} -> HyFactions max player power (optional)");
        lines.add("# {factionpower} -> HyFactions faction power (optional)");
        lines.add("# {factionpowermax} -> HyFactions faction max power (optional)");
        lines.add("# {claim} -> HyFactions faction claims used (optional)");
        lines.add("# {maxclaim} -> HyFactions faction max claims (optional)");
        lines.add("# Page 1");
        lines.add("page1Title: \"" + defaults.pages.get(0).title().replace("\"", "\\\"") + "\"");
        lines.add("page1DurationSeconds: " + (defaults.pages.get(0).durationMillis() / 1000.0));
        lines.add("page1RefreshSeconds: " + (defaults.pages.get(0).refreshMillis() / 1000.0));
        lines.add("page1Worlds:");
        lines.add("page1Lines:");
        for (String line : defaults.pages.get(0).lines()) {
            lines.add("  - \"" + line.replace("\"", "\\\"") + "\"");
        }
        for (int i = 1; i < MAX_PAGES; i++) {
            PageConfig page = defaults.pages.get(i);
            lines.add("# Page " + (i + 1));
            lines.add("page" + (i + 1) + "Title: \"" + page.title().replace("\"", "\\\"") + "\"");
            lines.add("page" + (i + 1) + "DurationSeconds: " + (page.durationMillis() / 1000.0));
            lines.add("page" + (i + 1) + "RefreshSeconds: " + (page.refreshMillis() / 1000.0));
            lines.add("page" + (i + 1) + "Worlds:");
            lines.add("page" + (i + 1) + "Lines:");
        }

        persist(path, lines);
    }

    BetterScoreBoardConfig withLines(List<String> newLines) {
        List<PageConfig> updated = new ArrayList<>(pages);
        PageConfig page = updated.get(0).withLines(newLines);
        updated.set(0, page);
        return new BetterScoreBoardConfig(
                title,
                logoTexturePath,
                maxLines,
                refreshMillis,
                Collections.unmodifiableList(new ArrayList<>(newLines)),
                Collections.unmodifiableList(updated),
                rotationEnabled,
                activePage,
                dataDir,
                offsetRight,
            offsetTop,
            dividerVisible,
            logoVisible
        );
    }

    BetterScoreBoardConfig withTitleAndLines(String newTitle, List<String> newLines) {
        String updatedTitle = newTitle != null && !newTitle.isEmpty() ? newTitle : title;
        List<PageConfig> updated = new ArrayList<>(pages);
        PageConfig page = updated.get(0).withTitleAndLines(updatedTitle, newLines);
        updated.set(0, page);
        return new BetterScoreBoardConfig(
                updatedTitle,
                logoTexturePath,
                maxLines,
                refreshMillis,
                Collections.unmodifiableList(new ArrayList<>(newLines)),
                Collections.unmodifiableList(updated),
                rotationEnabled,
                activePage,
                dataDir,
                offsetRight,
            offsetTop,
            dividerVisible,
            logoVisible
        );
    }

    BetterScoreBoardConfig withOffsets(int newOffsetRight, int newOffsetTop) {
        return new BetterScoreBoardConfig(
                title,
                logoTexturePath,
                maxLines,
                refreshMillis,
                lines,
                pages,
                rotationEnabled,
                activePage,
                dataDir,
                Math.max(0, newOffsetRight),
            Math.max(0, newOffsetTop),
            dividerVisible,
            logoVisible
        );
    }

    BetterScoreBoardConfig withDividerVisible(boolean visible) {
        return new BetterScoreBoardConfig(
                title,
                logoTexturePath,
                maxLines,
                refreshMillis,
                lines,
                pages,
                rotationEnabled,
                activePage,
                dataDir,
                offsetRight,
                offsetTop,
                visible,
                logoVisible
        );
    }

    BetterScoreBoardConfig withLogoVisible(boolean visible) {
        return new BetterScoreBoardConfig(
                title,
                logoTexturePath,
                maxLines,
                refreshMillis,
                lines,
                pages,
                rotationEnabled,
                activePage,
                dataDir,
                offsetRight,
                offsetTop,
                dividerVisible,
                visible
        );
    }

    BetterScoreBoardConfig withPages(List<PageConfig> updatedPages, int updatedActivePage, boolean updatedRotationEnabled) {
        List<PageConfig> resolved = new ArrayList<>(updatedPages);
        while (resolved.size() < MAX_PAGES) {
            resolved.add(new PageConfig("Page " + (resolved.size() + 1), Collections.unmodifiableList(new ArrayList<>()), 8_000L, 2500L, Collections.emptyList()));
        }
        int cappedActive = Math.max(1, Math.min(MAX_PAGES, updatedActivePage));
        PageConfig first = resolved.get(0);
        return new BetterScoreBoardConfig(
                first.title(),
                logoTexturePath,
                maxLines,
                refreshMillis,
                Collections.unmodifiableList(new ArrayList<>(first.lines())),
                Collections.unmodifiableList(resolved),
                updatedRotationEnabled,
                cappedActive,
                dataDir,
                offsetRight,
                offsetTop,
            dividerVisible,
            logoVisible
        );
    }

    static void persist(BetterScoreBoardConfig cfg) {
        Path path = cfg.dataDir().resolve("config.yaml");
        List<String> lines = new ArrayList<>();
        lines.add("# Better ScoreBoard configuration");
        lines.add("title: \"" + cfg.title + "\"");
        lines.add("# HUD refresh rate (seconds). Minimum 1.0 (set 0 to disable updates).");
        lines.add("refreshSeconds: " + (cfg.refreshMillis / 1000.0));
        lines.add("# Maximum lines rendered (capped by the HUD layout)");
        lines.add("maxLines: " + cfg.maxLines);
        lines.add("# HUD offsets in pixels");
        lines.add("offsetRight: " + cfg.offsetRight);
        lines.add("offsetTop: " + cfg.offsetTop);
        lines.add("dividerVisible: " + cfg.dividerVisible);
        lines.add("logoVisible: " + cfg.logoVisible);
        lines.add("# Rotation of multiple pages");
        lines.add("rotationEnabled: " + cfg.rotationEnabled);
        lines.add("activePage: " + cfg.activePage);
        lines.add("# Lines to render from top to bottom. Available placeholders:");
        lines.add("# {server}, {world}, {online}, {max_players}, {player}, {rank}, {level}, {xp}, {playtime}, {totalplaytime}, {tps}, {money}, {balance}, {faction}, {faction_rank}, {faction_tag}, {power}, {powermax}, {factionpower}, {factionpowermax}, {claim}, {maxclaim}");
        for (int i = 0; i < cfg.pages.size(); i++) {
            PageConfig page = cfg.pages.get(i);
            int pageNumber = i + 1;
            lines.add("# Page " + pageNumber);
            lines.add("page" + pageNumber + "Title: \"" + page.title().replace("\"", "\\\"") + "\"");
            lines.add("page" + pageNumber + "DurationSeconds: " + (page.durationMillis() / 1000.0));
            lines.add("page" + pageNumber + "RefreshSeconds: " + (page.refreshMillis() / 1000.0));
            lines.add("page" + pageNumber + "Worlds:");
            for (String world : page.worlds()) {
                lines.add("  - \"" + world.replace("\"", "\\\"") + "\"");
            }
            lines.add("page" + pageNumber + "Lines:");
            for (String line : page.lines()) {
                lines.add("  - \"" + line.replace("\"", "\\\"") + "\"");
            }
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

    static int parsePageLinesIndex(String line) {
        if (line == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (line.startsWith("page" + i + "Lines:")) {
                return i - 1;
            }
        }
        return -1;
    }

    static int parsePageTitleIndex(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (key.equals("page" + i + "Title")) {
                return i - 1;
            }
        }
        return -1;
    }

    static int parsePageDurationIndex(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (key.equals("page" + i + "DurationSeconds")) {
                return i - 1;
            }
        }
        return -1;
    }

    static int parsePageRefreshIndex(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (key.equals("page" + i + "RefreshSeconds")) {
                return i - 1;
            }
        }
        return -1;
    }

    static int parsePageWorldsIndex(String line) {
        if (line == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (line.startsWith("page" + i + "Worlds:")) {
                return i - 1;
            }
        }
        return -1;
    }

    private static List<String> normalizeWorldList(List<String> worlds) {
        if (worlds == null || worlds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String raw : worlds) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase();
            if (!normalized.contains(lower)) {
                normalized.add(lower);
            }
        }
        return normalized;
    }

    static final class PageConfig {
        private final String title;
        private final List<String> lines;
        private final long durationMillis;
        private final long refreshMillis;
        private final List<String> worlds;

        PageConfig(String title, List<String> lines, long durationMillis, long refreshMillis, List<String> worlds) {
            this.title = title != null && !title.isEmpty() ? title : "";
            this.lines = lines != null ? lines : Collections.emptyList();
            this.durationMillis = Math.max(1_000L, durationMillis);
            this.refreshMillis = refreshMillis <= 0 ? 0L : Math.max(MIN_REFRESH_MS, refreshMillis);
            this.worlds = worlds != null ? worlds : Collections.emptyList();
        }

        String title() {
            return title;
        }

        List<String> lines() {
            return lines;
        }

        long durationMillis() {
            return durationMillis;
        }

        long refreshMillis() {
            return refreshMillis;
        }

        List<String> worlds() {
            return worlds;
        }

        PageConfig withLines(List<String> newLines) {
            return new PageConfig(title, Collections.unmodifiableList(new ArrayList<>(newLines)), durationMillis, refreshMillis, worlds);
        }

        PageConfig withTitleAndLines(String newTitle, List<String> newLines) {
            String updatedTitle = newTitle != null && !newTitle.isEmpty() ? newTitle : title;
            return new PageConfig(updatedTitle, Collections.unmodifiableList(new ArrayList<>(newLines)), durationMillis, refreshMillis, worlds);
        }

        PageConfig withDuration(long durationMillis) {
            return new PageConfig(title, lines, durationMillis, refreshMillis, worlds);
        }

        PageConfig withRefresh(long refreshMillis) {
            return new PageConfig(title, lines, durationMillis, refreshMillis, worlds);
        }

        PageConfig withWorlds(List<String> newWorlds) {
            List<String> normalized = normalizeWorldList(newWorlds);
            return new PageConfig(title, lines, durationMillis, refreshMillis, Collections.unmodifiableList(normalized));
        }
    }
}
