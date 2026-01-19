package com.gillodaby.betterscoreboard;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

final class BetterScoreBoardService {

    private static final String PLACEHOLDERS = "{server}, {world}, {online}, {max_players}, {player}, {playtime}, {tps}, {balance}, {pos_x}, {pos_y}, {pos_z}, {gamemode}, {world_tick}, {chunk_x}, {chunk_z}, {uuid}";
    private static final int DEFAULT_OFFSET_RIGHT = 1;
    private static final int DEFAULT_OFFSET_TOP = 300;
    private final Map<UUID, TrackedHud> huds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService refresher;
    private java.util.concurrent.ScheduledFuture<?> refreshTask;
    private BetterScoreBoardConfig config;
    private final String serverName;
    private final int configuredMaxPlayers;
    private final List<PageState> pages;
    private int activePageIndex;
    private boolean rotationEnabled;
    private long nextRotationAtMs;
    private final EconomyBalanceSource economyBalanceSource;

    BetterScoreBoardService(BetterScoreBoardConfig config) {
        this.config = config;
        this.pages = new ArrayList<>();
        for (BetterScoreBoardConfig.PageConfig pageConfig : config.pages()) {
            this.pages.add(PageState.from(pageConfig));
        }
        while (this.pages.size() < BetterScoreBoardConfig.MAX_PAGES) {
            this.pages.add(PageState.emptyPage(this.pages.size() + 1));
        }
        this.activePageIndex = clampPageIndex(config.activePage() - 1);
        this.rotationEnabled = config.rotationEnabled();
        this.nextRotationAtMs = System.currentTimeMillis() + currentPage().durationMs;
        this.economyBalanceSource = new EconomyBalanceSource();
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "BetterScoreBoard-Refresher");
            t.setDaemon(true);
            return t;
        };
        this.refresher = Executors.newSingleThreadScheduledExecutor(factory);

        String resolvedName = "Server";
        int resolvedMaxPlayers = 0;
        try {
            if (HytaleServer.get().getConfig() != null) {
                if (HytaleServer.get().getConfig().getServerName() != null) {
                    resolvedName = HytaleServer.get().getConfig().getServerName();
                }
                resolvedMaxPlayers = HytaleServer.get().getConfig().getMaxPlayers();
            }
        } catch (Throwable ignored) {
        }
        this.serverName = resolvedName;
        this.configuredMaxPlayers = resolvedMaxPlayers;
    }

    void start() {
        // Periodic, but self-contained: only refresh our own HUD instances via MultipleHUD.
        scheduleRefresh();
    }

    void stop() {
        for (TrackedHud tracked : huds.values()) {
            if (tracked != null && tracked.player != null && tracked.ref != null) {
                MultipleHUD.getInstance().hideCustomHud(tracked.player, tracked.ref, "BetterScoreBoard");
            }
        }
        huds.clear();
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        refresher.shutdownNow();
    }

    void handlePlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        ensurePages();
        PlayerRef ref = player.getPlayerRef();
        if (ref == null) {
            return;
        }
        UUID id = ref.getUuid();
        if (id == null) {
            return;
        }

        if (huds.containsKey(id)) {
            refresher.execute(() -> refreshSingle(id));
            return;
        }

        // Delay to let the client finish loading HUD assets (and to let MultipleHUD hook in)
        refresher.schedule(() -> openHud(player, ref), 500, TimeUnit.MILLISECONDS);
    }

    void openEditor(Player player) {
        if (player == null) {
            return;
        }
        ensurePages();
        PlayerRef ref = player.getPlayerRef();
        if (ref == null || ref.getUuid() == null || ref.getReference() == null || ref.getReference().getStore() == null) {
            return;
        }
        PageManager pageManager = player.getPageManager();
        if (pageManager == null) {
            return;
        }
        ScoreboardEditorPage page = new ScoreboardEditorPage(ref, this, config, snapshotPages(), activePageIndex, rotationEnabled);
        pageManager.openCustomPage(ref.getReference(), ref.getReference().getStore(), page);
    }

    void handlePlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef ref = event.getPlayerRef();
        if (ref == null || ref.getUuid() == null) {
            return;
        }
        UUID id = ref.getUuid();
        TrackedHud tracked = huds.remove(id);
        if (tracked != null) {
            MultipleHUD.getInstance().hideCustomHud(tracked.player, ref, "BetterScoreBoard");
            System.out.println("[BetterScoreBoard] Removed HUD for " + safePlayerName(tracked.player));
        }
    }

    void showHud(Player player) {
        if (player == null || player.getPlayerRef() == null) {
            return;
        }
        openHud(player, player.getPlayerRef());
    }

    void hideHud(Player player) {
        if (player == null || player.getPlayerRef() == null || player.getPlayerRef().getUuid() == null) {
            return;
        }
        UUID id = player.getPlayerRef().getUuid();
        TrackedHud tracked = huds.remove(id);
        if (tracked != null) {
            MultipleHUD.getInstance().hideCustomHud(player, tracked.ref, "BetterScoreBoard");
            System.out.println("[BetterScoreBoard] HUD hidden for " + safePlayerName(player));
        }
    }

    private void openHud(Player player, PlayerRef ref) {
        if (player == null || ref == null || ref.getUuid() == null) {
            return;
        }
        UUID id = ref.getUuid();
        if (huds.containsKey(id)) {
            refreshSingle(id);
            return;
        }

        BetterScoreBoardHud hud = new BetterScoreBoardHud(ref, config);
        ScoreboardView view = buildView(player, huds.size() + 1);
        hud.refresh(player, ref, view);
        MultipleHUD.getInstance().setCustomHud(player, ref, "BetterScoreBoard", hud);
        huds.put(id, new TrackedHud(player, ref, hud));
        refreshSingle(id);
        // Re-arm a few delayed refreshes after join to ensure the HUD stays visible
        System.out.println("[BetterScoreBoard] HUD overlay shown for " + safePlayerName(player));
    }

    private void scheduleRearm(UUID id, long delayMs) {
        refresher.schedule(() -> refreshSingle(id), delayMs, TimeUnit.MILLISECONDS);
    }

    private void refreshAll() {
        maybeRotatePages();
        for (Map.Entry<UUID, TrackedHud> entry : huds.entrySet()) {
            UUID id = entry.getKey();
            TrackedHud tracked = entry.getValue();
            if (tracked == null) {
                huds.remove(id);
                continue;
            }
            Player player = tracked.player;
            if (player == null || player.wasRemoved()) {
                huds.remove(id);
                continue;
            }
            PlayerRef ref = tracked.ref;
            BetterScoreBoardHud hud = tracked.hud;
            executeOnWorldThread(player, () -> {
                try {
                    hud.refresh(player, ref, buildView(player, huds.size()));
                } catch (Throwable ignored) {
                }
            });
        }
    }

    private void refreshSingle(UUID id) {
        TrackedHud tracked = huds.get(id);
        if (tracked == null) {
            return;
        }
        Player player = tracked.player;
        if (player == null || player.wasRemoved()) {
            huds.remove(id);
            return;
        }
        PlayerRef ref = tracked.ref;
        BetterScoreBoardHud hud = tracked.hud;
        executeOnWorldThread(player, () -> {
            try {
                hud.refresh(player, ref, buildView(player, huds.size()));
            } catch (Throwable ignored) {
            }
        });
    }

    private void executeOnWorldThread(Player player, Runnable action) {
        if (player == null || action == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        world.execute(action);
    }

    private ScoreboardView buildView(Player player, int onlineCount) {
        PageState page = pageForPlayer(player);
        if (page == null) {
            return null;
        }
        List<ScoreboardView.LineRender> formatted = formatLines(page, player, onlineCount);
        LineParts titleParts = decodeLine(page.title);
        return new ScoreboardView(titleParts.text(), titleParts.color(), "", DEFAULT_OFFSET_RIGHT, DEFAULT_OFFSET_TOP, List.copyOf(formatted), config.showDivider());
    }

    private List<ScoreboardView.LineRender> formatLines(PageState page, Player player, int onlineCount) {
        List<ScoreboardView.LineRender> formatted = new ArrayList<>();
        List<String> currentLines = page != null ? page.lines : Collections.emptyList();
        if (currentLines.isEmpty()) {
            return formatted;
        }

        int max = Math.min(config.maxLines(), BetterScoreBoardHud.MAX_LINES);
        for (String template : currentLines) {
            if (template == null) {
                continue;
            }
            String lineWithPlaceholders = applyPlaceholders(template, player, onlineCount);
            BoldResult boldResult = stripBoldMarkers(lineWithPlaceholders);
            List<ScoreboardView.LineSegment> segments = parseSegments(boldResult.text());
            formatted.add(new ScoreboardView.LineRender(List.copyOf(segments), boldResult.bold()));
            if (formatted.size() >= max) {
                break;
            }
        }
        return formatted;
    }

    private PageState pageForPlayer(Player player) {
        if (player == null) {
            return currentPage();
        }
        PageState current = currentPage();
        String world = normalizedWorld(player);
        if (pageVisibleInWorld(current, world)) {
            return current;
        }
        PageState match = firstPageForWorld(world, true);
        if (match != null) {
            return match;
        }
        return firstPageForWorld(world, false);
    }

    private PageState firstPageForWorld(String world, boolean requireContent) {
        for (PageState page : pages) {
            if (!pageVisibleInWorld(page, world)) {
                continue;
            }
            if (requireContent && !pageHasContent(page)) {
                continue;
            }
            return page;
        }
        return null;
    }

    private boolean pageVisibleInWorld(PageState page, String world) {
        if (page == null) {
            return false;
        }
        if (page.worlds == null || page.worlds.isEmpty()) {
            return true;
        }
        if (world == null || world.isEmpty()) {
            return false;
        }
        return page.worlds.contains(world);
    }

    private String applyPlaceholders(String template, Player player, int onlineCount) {
        String result = template;
        result = result.replace("{server}", serverName);
        result = result.replace("{player}", safePlayerName(player));
        result = result.replace("{world}", safeWorld(player));
        result = result.replace("{online}", Integer.toString(Math.max(onlineCount, 0)));
        result = result.replace("{max_players}", Integer.toString(resolveMaxPlayers(onlineCount)));
        result = result.replace("{playtime}", formatPlaytime(player));
        result = result.replace("{tps}", formatTps(player));
        String balanceValue = formatBalance(player);
        result = result.replace("{money}", balanceValue);
        result = result.replace("{balance}", balanceValue);
        result = result.replace("{pos_x}", formatPos(player, Axis.X));
        result = result.replace("{pos_y}", formatPos(player, Axis.Y));
        result = result.replace("{pos_z}", formatPos(player, Axis.Z));
        result = result.replace("{gamemode}", formatGameMode(player));
        result = result.replace("{world_tick}", formatWorldTick(player));
        result = result.replace("{chunk_x}", formatChunk(player, Axis.X));
        result = result.replace("{chunk_z}", formatChunk(player, Axis.Z));
        result = result.replace("{uuid}", formatUuid(player));
        return result;
    }

    private int resolveMaxPlayers(int onlineCount) {
        if (configuredMaxPlayers > 0) {
            return configuredMaxPlayers;
        }
        try {
            if (HytaleServer.get().getConfig() != null) {
                int maxPlayers = HytaleServer.get().getConfig().getMaxPlayers();
                if (maxPlayers > 0) {
                    return maxPlayers;
                }
            }
        } catch (Throwable ignored) {
        }
        return Math.max(onlineCount, 1);
    }

    // --- mutable lines API for commands ---
    List<String> lines() {
        return Collections.unmodifiableList(currentPage().lines);
    }

    void setLine(int index, String text) {
        ensureSize(index + 1);
        currentPage().lines.set(index, text);
        refreshAll();
    }

    boolean addLine(String text) {
        if (currentPage().lines.size() >= BetterScoreBoardHud.MAX_LINES) {
            return false;
        }
        currentPage().lines.add(text);
        refreshAll();
        return true;
    }

    boolean removeLine(int index) {
        if (index < 0 || index >= currentPage().lines.size()) {
            return false;
        }
        currentPage().lines.remove(index);
        refreshAll();
        return true;
    }

    void saveConfig() {
        config = config.withPages(snapshotPages(), activePageIndex + 1, rotationEnabled);
        BetterScoreBoardConfig.persist(config);
        refreshAll();
    }

    void reloadConfig() {
        this.config = BetterScoreBoardConfig.load(config.dataDir());
        pages.clear();
        for (BetterScoreBoardConfig.PageConfig pageConfig : config.pages()) {
            pages.add(PageState.from(pageConfig));
        }
        while (pages.size() < BetterScoreBoardConfig.MAX_PAGES) {
            pages.add(PageState.emptyPage(pages.size() + 1));
        }
        activePageIndex = clampPageIndex(config.activePage() - 1);
        rotationEnabled = config.rotationEnabled();
        nextRotationAtMs = System.currentTimeMillis() + currentPage().durationMs;
        scheduleRefresh();
        refreshAll();
    }

    void applyEditorUpdate(int pageIndex, List<BetterScoreBoardConfig.PageConfig> updatedPages, boolean updatedRotationEnabled, boolean persist) {
        if (updatedPages == null || updatedPages.isEmpty()) {
            return;
        }
        pages.clear();
        for (BetterScoreBoardConfig.PageConfig pageConfig : updatedPages) {
            pages.add(PageState.from(pageConfig));
        }
        while (pages.size() < BetterScoreBoardConfig.MAX_PAGES) {
            pages.add(PageState.emptyPage(pages.size() + 1));
        }
        activePageIndex = clampPageIndex(pageIndex);
        rotationEnabled = updatedRotationEnabled;
        nextRotationAtMs = System.currentTimeMillis() + currentPage().durationMs;
        config = config.withPages(snapshotPages(), activePageIndex + 1, rotationEnabled);
        if (persist) {
            BetterScoreBoardConfig.persist(config);
        }
        scheduleRefresh();
        refreshAll();
    }

    boolean dividerVisible() {
        return config.showDivider();
    }

    boolean setDividerVisible(boolean visible, boolean persist) {
        if (config.showDivider() == visible) {
            return false;
        }
        config = config.withDividerVisible(visible);
        if (persist) {
            BetterScoreBoardConfig.persist(config);
        }
        refreshAll();
        return true;
    }

    String placeholdersLine() {
        return PLACEHOLDERS;
    }

    private void ensureSize(int size) {
        while (currentPage().lines.size() < size) {
            currentPage().lines.add("");
        }
    }

    private void ensurePages() {
        if (pages.isEmpty()) {
            for (BetterScoreBoardConfig.PageConfig pageConfig : config.pages()) {
                pages.add(PageState.from(pageConfig));
            }
        }
        while (pages.size() < BetterScoreBoardConfig.MAX_PAGES) {
            pages.add(PageState.emptyPage(pages.size() + 1));
        }
    }

    com.hypixel.hytale.server.core.Message text(String raw) {
        return com.hypixel.hytale.server.core.Message.raw(raw);
    }

    private String formatPlaytime(Player player) {
        TrackedHud tracked = huds.get(player.getUuid());
        long join = tracked != null ? tracked.joinedAtMs : System.currentTimeMillis();
        long delta = Math.max(0, System.currentTimeMillis() - join);
        long seconds = delta / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02dh %02dm %02ds", hours, minutes, secs);
    }

    private String formatPos(Player player, Axis axis) {
        if (player == null || player.getTransformComponent() == null || player.getTransformComponent().getPosition() == null) {
            return "0";
        }
        double value = switch (axis) {
            case X -> player.getTransformComponent().getPosition().getX();
            case Y -> player.getTransformComponent().getPosition().getY();
            case Z -> player.getTransformComponent().getPosition().getZ();
        };
        String prefix = switch (axis) {
            case X -> "X: ";
            case Y -> "Y: ";
            case Z -> "Z: ";
        };
        return prefix + (int) Math.round(value);
    }

    private String formatTps(Player player) {
        if (player == null || player.getWorld() == null) {
            return "20.0";
        }
        TrackedHud tracked = huds.get(player.getUuid());
        long tickNow = player.getWorld().getTick();
        long timeNow = System.currentTimeMillis();
        if (tracked == null) {
            return "20.0";
        }
        long tickDelta = tickNow - tracked.lastWorldTick;
        long timeDelta = timeNow - tracked.lastTickTimeMs;
        if (tickDelta <= 0 || timeDelta <= 0) {
            tracked.lastWorldTick = tickNow;
            tracked.lastTickTimeMs = timeNow;
            return "20.0";
        }
        double tps = tickDelta / (timeDelta / 1000.0);
        tracked.lastWorldTick = tickNow;
        tracked.lastTickTimeMs = timeNow;
        return String.format("%.1f", Math.max(0.0, Math.min(20.0, tps)));
    }

    private String formatBalance(Player player) {
        long balance = economyBalanceSource.getBalance(player);
        return Long.toString(balance);
    }

    private String formatGameMode(Player player) {
        if (player == null || player.getGameMode() == null) {
            return "SURVIVAL";
        }
        return player.getGameMode().name();
    }

    private String formatWorldTick(Player player) {
        if (player == null || player.getWorld() == null) {
            return "0";
        }
        return Long.toString(player.getWorld().getTick());
    }

    private String formatChunk(Player player, Axis axis) {
        if (player == null || player.getTransformComponent() == null || player.getTransformComponent().getPosition() == null) {
            return "0";
        }
        double value = switch (axis) {
            case X -> player.getTransformComponent().getPosition().getX();
            case Z -> player.getTransformComponent().getPosition().getZ();
            default -> 0.0;
        };
        int chunk = (int) Math.floor(value / 16.0);
        return Integer.toString(chunk);
    }

    private String formatUuid(Player player) {
        if (player == null || player.getUuid() == null) {
            return "";
        }
        return player.getUuid().toString();
    }


    private List<String> sanitizeLines(List<String> requestedLines) {
        List<String> sanitized = new ArrayList<>();
        if (requestedLines != null) {
            int limit = Math.min(BetterScoreBoardHud.MAX_LINES, Math.max(1, config.maxLines()));
            for (String line : requestedLines) {
                if (sanitized.size() >= limit) {
                    break;
                }
                sanitized.add(line != null ? line : "");
            }
        }
        trimTrailingEmpty(sanitized);
        return sanitized;
    }

    private void trimTrailingEmpty(List<String> lines) {
        while (!lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            if (last == null || last.isEmpty()) {
                lines.remove(lines.size() - 1);
            } else {
                break;
            }
        }
    }

    private enum Axis { X, Y, Z }

    private BoldResult stripBoldMarkers(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new BoldResult("", false);
        }
        StringBuilder builder = new StringBuilder();
        boolean inside = false;
        boolean pairFound = false;
        for (char c : raw.toCharArray()) {
            if (c == '*') {
                if (inside) {
                    pairFound = true;
                }
                inside = !inside;
                continue;
            }
            builder.append(c);
        }
        return new BoldResult(builder.toString(), pairFound);
    }

    private List<ScoreboardView.LineSegment> parseSegments(String value) {
        List<ScoreboardView.LineSegment> segments = new ArrayList<>();
        String content = value != null ? value : "";
        String currentColor = "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < content.length(); ) {
            char c = content.charAt(i);
            if (c == '[') {
                int close = content.indexOf(']', i);
                if (close > i + 1) {
                    String candidate = content.substring(i + 1, close);
                    String normalized = normalizeColor(candidate);
                    if (!normalized.isEmpty()) {
                        if (builder.length() > 0) {
                            segments.add(new ScoreboardView.LineSegment(builder.toString(), currentColor));
                            builder.setLength(0);
                        }
                        currentColor = normalized;
                        i = close + 1;
                        continue;
                    }
                }
            }
            builder.append(c);
            i++;
        }
        if (builder.length() > 0) {
            segments.add(new ScoreboardView.LineSegment(builder.toString(), currentColor));
        }
        if (segments.isEmpty()) {
            segments.add(new ScoreboardView.LineSegment("", currentColor));
        }
        return segments;
    }

    private String normalizeColor(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String candidate = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        if (candidate.length() != 6) {
            return "";
        }
        for (char c : candidate.toCharArray()) {
            if (Character.digit(c, 16) < 0) {
                return "";
            }
        }
        return "#" + candidate.toLowerCase();
    }

    private LineParts decodeLine(String raw) {
        if (raw == null) {
            return new LineParts("", "");
        }
        String color = "";
        String text = raw;
        if (raw.startsWith("[") && raw.length() > 8) {
            int close = raw.indexOf(']');
            if (close > 1) {
                String candidate = raw.substring(1, close);
                String normalized = normalizeColor(candidate);
                if (!normalized.isEmpty()) {
                    color = normalized;
                    text = raw.substring(close + 1);
                }
            }
        }
        return new LineParts(color, text);
    }

    private record BoldResult(String text, boolean bold) {}

    private record LineParts(String color, String text) {}

    private String sanitizeTitle(String requestedTitle) {
        if (requestedTitle == null || requestedTitle.trim().isEmpty()) {
            return currentPage().title;
        }
        return requestedTitle.trim();
    }

    private String safePlayerName(Player player) {
        if (player == null) {
            return "Player";
        }
        String name = player.getDisplayName();
        return name != null ? name : "Player";
    }

    private String safeWorld(Player player) {
        if (player == null) {
            return "world";
        }
        World world = player.getWorld();
        if (world == null || world.getName() == null || world.getName().isEmpty()) {
            return "world";
        }
        return world.getName();
    }

    private String normalizedWorld(Player player) {
        return safeWorld(player).toLowerCase();
    }

    // Bridge to the optional EconomyPlugin so {balance}/{money} can be resolved when available.
    private static final class EconomyBalanceSource {

        private static final String MONEY_COMPONENT_CLASS = "com.ryukazan.economy.MoneyComponent";
        private static final String TYPE_FIELD_NAME = "TYPE";
        private static final String BALANCE_FIELD_NAME = "balance";

        private volatile ComponentType moneyType;
        private volatile Field balanceField;
        private volatile Method componentGetter;

        EconomyBalanceSource() {
            refresh();
        }

        long getBalance(Player player) {
            if (player == null) {
                return 0L;
            }
            ensureInitialized();
            ComponentType type = moneyType;
            Method getter = componentGetter;
            Field balance = balanceField;
            if (type == null || getter == null || balance == null) {
                return 0L;
            }
            EntityStore store = player.getWorld() != null ? player.getWorld().getEntityStore() : null;
            PlayerRef ref = player.getPlayerRef();
            if (store == null || ref == null) {
                return 0L;
            }
            Object component = invokeGetter(getter, store, ref, type);
            if (component == null) {
                return 0L;
            }
            Object rawBalance = readBalance(balance, component);
            if (rawBalance instanceof Number number) {
                return number.longValue();
            }
            if (rawBalance != null) {
                try {
                    return Long.parseLong(rawBalance.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            return 0L;
        }

        private void ensureInitialized() {
            if (moneyType != null && componentGetter != null && balanceField != null) {
                return;
            }
            refresh();
        }

        private void refresh() {
            if (componentGetter == null) {
                componentGetter = findComponentGetter();
            }
            try {
                Class<?> moneyClass = Class.forName(MONEY_COMPONENT_CLASS);
                Field balance = moneyClass.getField(BALANCE_FIELD_NAME);
                balance.setAccessible(true);
                balanceField = balance;
                Field typeField = moneyClass.getField(TYPE_FIELD_NAME);
                Object typeValue = typeField.get(null);
                if (typeValue instanceof ComponentType componentType) {
                    moneyType = componentType;
                }
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignored) {
            }
        }

        private Method findComponentGetter() {
            for (Method method : EntityStore.class.getMethods()) {
                if (!"getComponent".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 2) {
                    continue;
                }
                if (!ComponentType.class.isAssignableFrom(params[1])) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            return null;
        }

        private Object invokeGetter(Method getter, EntityStore store, PlayerRef ref, ComponentType type) {
            try {
                return getter.invoke(store, ref, type);
            } catch (Exception ignored) {
                return null;
            }
        }

        private Object readBalance(Field field, Object component) {
            try {
                return field.get(component);
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
    }

    private static final class TrackedHud {
        final Player player;
        final PlayerRef ref;
        final BetterScoreBoardHud hud;
        final long joinedAtMs;
        long lastWorldTick;
        long lastTickTimeMs;

        TrackedHud(Player player, PlayerRef ref, BetterScoreBoardHud hud) {
            this.player = player;
            this.ref = ref;
            this.hud = hud;
            this.joinedAtMs = System.currentTimeMillis();
            this.lastTickTimeMs = System.currentTimeMillis();
            this.lastWorldTick = player != null && player.getWorld() != null ? player.getWorld().getTick() : 0;
        }
    }

    private void scheduleRefresh() {
        long interval = Math.max(250L, currentPage().refreshMs);
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        refreshTask = refresher.scheduleAtFixedRate(this::refreshAll, interval, interval, TimeUnit.MILLISECONDS);
    }

    private PageState currentPage() {
        return pages.get(clampPageIndex(activePageIndex));
    }

    private int clampPageIndex(int index) {
        return Math.max(0, Math.min(pages.size() - 1, index));
    }

    private List<BetterScoreBoardConfig.PageConfig> snapshotPages() {
        List<BetterScoreBoardConfig.PageConfig> snapshot = new ArrayList<>();
        for (PageState page : pages) {
            snapshot.add(page.toConfig());
        }
        return snapshot;
    }

    private void maybeRotatePages() {
        if (!rotationEnabled || pages.isEmpty()) {
            return;
        }
        List<Integer> candidates = rotationCandidates();
        if (candidates.size() < 2) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRotationAtMs) {
            return;
        }
        int currentIndex = clampPageIndex(activePageIndex);
        int nextIndex = nextCandidateIndex(candidates, currentIndex);
        activePageIndex = nextIndex;
        nextRotationAtMs = now + currentPage().durationMs;
        scheduleRefresh();
    }

    private List<Integer> rotationCandidates() {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            if (pageHasContent(pages.get(i))) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add(clampPageIndex(activePageIndex));
        }
        return candidates;
    }

    private boolean pageHasContent(PageState page) {
        for (String line : page.lines) {
            if (line != null && !line.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private int nextCandidateIndex(List<Integer> candidates, int currentIndex) {
        int pos = candidates.indexOf(currentIndex);
        if (pos < 0) {
            return candidates.get(0);
        }
        int nextPos = (pos + 1) % candidates.size();
        return candidates.get(nextPos);
    }

    private static final class PageState {
        String title;
        List<String> lines;
        long durationMs;
        long refreshMs;
        List<String> worlds;

        PageState(String title, List<String> lines, long durationMs, long refreshMs, List<String> worlds) {
            this.title = title != null ? title : "";
            this.lines = lines != null ? lines : new ArrayList<>();
            this.durationMs = durationMs;
            this.refreshMs = refreshMs;
            this.worlds = worlds != null ? worlds : new ArrayList<>();
        }

        static PageState from(BetterScoreBoardConfig.PageConfig pageConfig) {
            return new PageState(pageConfig.title(), new ArrayList<>(pageConfig.lines()), pageConfig.durationMillis(), pageConfig.refreshMillis(), new ArrayList<>(pageConfig.worlds()));
        }

        static PageState emptyPage(int pageNumber) {
            return new PageState("Page " + pageNumber, new ArrayList<>(), 8_000L, 2500L, new ArrayList<>());
        }

        BetterScoreBoardConfig.PageConfig toConfig() {
            return new BetterScoreBoardConfig.PageConfig(title, Collections.unmodifiableList(new ArrayList<>(lines)), durationMs, refreshMs, Collections.unmodifiableList(new ArrayList<>(worlds)));
        }
    }
}
