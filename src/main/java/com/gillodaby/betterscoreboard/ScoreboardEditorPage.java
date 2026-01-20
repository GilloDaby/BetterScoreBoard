package com.gillodaby.betterscoreboard;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;

final class ScoreboardEditorPage extends InteractiveCustomUIPage<ScoreboardEditorPage.EditorEventData> {

    private final PlayerRef playerRef;
    private final BetterScoreBoardService service;
    private BetterScoreBoardConfig config;
    private final List<PageDraft> pages;
    private int currentPageIndex;
    private boolean rotationEnabled;

    ScoreboardEditorPage(PlayerRef playerRef, BetterScoreBoardService service, BetterScoreBoardConfig config, List<BetterScoreBoardConfig.PageConfig> pages, int activePageIndex, boolean rotationEnabled) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EditorEventData.CODEC);
        this.playerRef = playerRef;
        this.service = service;
        this.config = config;
        this.pages = new ArrayList<>();
        if (pages != null) {
            for (BetterScoreBoardConfig.PageConfig page : pages) {
                this.pages.add(PageDraft.from(page));
            }
        }
        while (this.pages.size() < BetterScoreBoardConfig.MAX_PAGES) {
            this.pages.add(PageDraft.empty(this.pages.size() + 1));
        }
        this.currentPageIndex = Math.max(0, Math.min(BetterScoreBoardConfig.MAX_PAGES - 1, activePageIndex));
        this.rotationEnabled = rotationEnabled;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder builder, UIEventBuilder events, Store<EntityStore> store) {
        builder.append("Pages/GilloDaby_BetterScoreBoardEditor.ui");
        builder.set("#EditorRoot.Visible", true);
        PageDraft current = currentPage();
        LineParts titleParts = parseLine(current.title);
        builder.set("#EditorTitle.Text", "Scoreboard Editor - Page " + (currentPageIndex + 1));
        builder.set("#TitleInput.Value", titleParts.text());
        builder.set("#TitleColorHex.Value", titleParts.color().isEmpty() ? "#f6f8ff" : titleParts.color());
        builder.set("#PlaceholderHint.Text", "Placeholders: " + service.placeholdersLine());
        builder.set("#LimitHint.Text", "Max lines shown: " + Math.min(config.maxLines(), BetterScoreBoardHud.MAX_LINES));
        builder.set("#ActivePageLabel.Text", "Page active: " + (currentPageIndex + 1));
        builder.set("#AutoRotateToggle #CheckBox.Value", rotationEnabled);
        builder.set("#PageDurationInput.Value", formatSeconds(current.durationSeconds));
        builder.set("#PageRefreshInput.Value", formatSeconds(current.refreshSeconds));
        builder.set("#PageWorldsInput.Value", formatWorlds(current.worlds));
        for (int i = 0; i < BetterScoreBoardConfig.MAX_PAGES; i++) {
            String selector = "#Page" + (i + 1) + "Button.Visible";
            builder.set(selector, true);
        }

        int max = Math.min(BetterScoreBoardHud.MAX_LINES, Math.max(1, config.maxLines()));
        for (int i = 0; i < BetterScoreBoardHud.MAX_LINES; i++) {
            String rowSelector = "#Line" + (i + 1) + "Row.Visible";
            String valueSelector = "#Line" + (i + 1) + "Input.Value";
            String colorSelector = "#Line" + (i + 1) + "Color.Value";
            boolean withinLimit = i < max;
            builder.set(rowSelector, withinLimit);
            LineParts parts = i < current.lines.size() ? parseLine(current.lines.get(i)) : new LineParts("", "");
            builder.set(valueSelector, parts.text());
            builder.set(colorSelector, parts.color());
        }

        EventData apply = new EventData().append("Action", "apply");
        EventData save = new EventData().append("Action", "save");
        List<EventData> pageEvents = new ArrayList<>();
        for (int i = 1; i <= BetterScoreBoardConfig.MAX_PAGES; i++) {
            pageEvents.add(new EventData().append("Action", "page" + i));
        }
        EventData toggleRotation = new EventData().append("RotationToggle", "CAT");
        apply.append("@Title", "#TitleInput.Value");
        save.append("@Title", "#TitleInput.Value");
        for (EventData pageEvent : pageEvents) {
            pageEvent.append("@Title", "#TitleInput.Value");
        }
        apply.append("@TitleColorHex", "#TitleColorHex.Value");
        save.append("@TitleColorHex", "#TitleColorHex.Value");
        for (EventData pageEvent : pageEvents) {
            pageEvent.append("@TitleColorHex", "#TitleColorHex.Value");
        }
        apply.append("@PageDuration", "#PageDurationInput.Value");
        save.append("@PageDuration", "#PageDurationInput.Value");
        for (EventData pageEvent : pageEvents) {
            pageEvent.append("@PageDuration", "#PageDurationInput.Value");
        }
        apply.append("@PageRefresh", "#PageRefreshInput.Value");
        save.append("@PageRefresh", "#PageRefreshInput.Value");
        for (EventData pageEvent : pageEvents) {
            pageEvent.append("@PageRefresh", "#PageRefreshInput.Value");
        }
        apply.append("@PageWorlds", "#PageWorldsInput.Value");
        save.append("@PageWorlds", "#PageWorldsInput.Value");
        for (EventData pageEvent : pageEvents) {
            pageEvent.append("@PageWorlds", "#PageWorldsInput.Value");
        }
        for (int i = 0; i < BetterScoreBoardHud.MAX_LINES; i++) {
            String key = "@Line" + (i + 1);
            String selector = "#Line" + (i + 1) + "Input.Value";
            String colorHexKey = "@ColorHex" + (i + 1);
            String colorSelector = "#Line" + (i + 1) + "Color.Value";
            apply.append(key, selector);
            save.append(key, selector);
            for (EventData pageEvent : pageEvents) {
                pageEvent.append(key, selector);
            }
            apply.append(colorHexKey, colorSelector);
            save.append(colorHexKey, colorSelector);
            for (EventData pageEvent : pageEvents) {
                pageEvent.append(colorHexKey, colorSelector);
            }
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyButton", apply, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", save, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadButton", new EventData().append("Action", "reload"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", new EventData().append("Action", "close"), false);
        for (int i = 1; i <= BetterScoreBoardConfig.MAX_PAGES; i++) {
            String selector = "#Page" + i + "Button";
            events.addEventBinding(CustomUIEventBindingType.Activating, selector, pageEvents.get(i - 1), false);
        }
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AutoRotateToggle #CheckBox", toggleRotation, false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, EditorEventData data) {
        if (data == null) {
            return;
        }
        if (data.rotationToggle != null) {
            rotationEnabled = !rotationEnabled;
            refreshPageUI();
            return;
        }
        if (data.action == null) {
            return;
        }
        EditorSubmission submission = collect(data);
        switch (data.action) {
            case "apply" -> {
                updateDraft(submission);
                service.applyEditorUpdate(currentPageIndex, buildUpdatedPages(), rotationEnabled, false);
            }
            case "save" -> {
                updateDraft(submission);
                service.applyEditorUpdate(currentPageIndex, buildUpdatedPages(), rotationEnabled, false);
                service.saveConfig();
                close();
            }
            case "reload" -> {
                service.reloadConfig();
                reloadFromService();
            }
            case "close" -> close();
            default -> {
                if (data.action.startsWith("page")) {
                    updateDraft(submission);
                    currentPageIndex = parsePageIndex(data.action);
                    refreshPageUI();
                }
            }
        }
    }

    private EditorSubmission collect(EditorEventData data) {
        List<String> values = new ArrayList<>();
        values.add(encodeLine(resolveColor(data.colorHex1), data.line1));
        values.add(encodeLine(resolveColor(data.colorHex2), data.line2));
        values.add(encodeLine(resolveColor(data.colorHex3), data.line3));
        values.add(encodeLine(resolveColor(data.colorHex4), data.line4));
        values.add(encodeLine(resolveColor(data.colorHex5), data.line5));
        values.add(encodeLine(resolveColor(data.colorHex6), data.line6));
        values.add(encodeLine(resolveColor(data.colorHex7), data.line7));
        values.add(encodeLine(resolveColor(data.colorHex8), data.line8));
        values.add(encodeLine(resolveColor(data.colorHex9), data.line9));
        values.add(encodeLine(resolveColor(data.colorHex10), data.line10));
        values.add(encodeLine(resolveColor(data.colorHex11), data.line11));
        values.add(encodeLine(resolveColor(data.colorHex12), data.line12));
        double durationSeconds = parseDoubleOrDefault(data.pageDuration, currentPage().durationSeconds);
        double refreshSeconds = parseDoubleOrDefault(data.pageRefresh, currentPage().refreshSeconds);
        List<String> worlds = parseWorlds(data.pageWorlds);
        return new EditorSubmission(
            encodeTitle(resolveColor(data.titleColorHex), safe(data.title)),
            values,
            durationSeconds,
            refreshSeconds,
            worlds
        );
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String encodeLine(String color, String text) {
        String sanitizedColor = sanitizeColor(color);
        String sanitizedText = safe(text);
        if (!sanitizedText.isEmpty() && textStartsWithColorSegment(sanitizedText)) {
            return sanitizedText;
        }
        if (!sanitizedColor.isEmpty()) {
            return "[" + sanitizedColor + "]" + sanitizedText;
        }
        return sanitizedText;
    }

    private boolean textStartsWithColorSegment(String raw) {
        if (raw == null || raw.length() < 8 || raw.charAt(0) != '[') {
            return false;
        }
        int close = raw.indexOf(']');
        if (close <= 1 || close > 8) {
            return false;
        }
        String maybeColor = raw.substring(1, close);
        return !sanitizeColor(maybeColor).isEmpty();
    }

    private String sanitizeColor(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.length() != 6) {
            return "";
        }
        for (char c : trimmed.toCharArray()) {
            if (Character.digit(c, 16) < 0) {
                return "";
            }
        }
        return "#" + trimmed.toLowerCase();
    }

    private LineParts parseLine(String raw) {
        if (raw == null) {
            return new LineParts("", "");
        }
        String color = "";
        String text = raw;
        if (raw.startsWith("[") && raw.length() > 8) {
            int close = raw.indexOf(']');
            if (close > 1) {
                String maybeColor = raw.substring(1, close);
                String sanitizedColor = sanitizeColor(maybeColor);
                if (!sanitizedColor.isEmpty()) {
                    color = sanitizedColor;
                    text = raw.substring(close + 1);
                }
            }
        }
        return new LineParts(color, safe(text));
    }

    private String encodeTitle(String color, String title) {
        String sanitizedColor = sanitizeColor(color);
        if (!sanitizedColor.isEmpty()) {
            return "[" + sanitizedColor + "]" + title;
        }
        return title;
    }

    private String resolveColor(String hexValue) {
        String hex = sanitizeColor(hexValue);
        if (!hex.isEmpty()) {
            return hex;
        }
        return "";
    }

    private int parseIntOrDefault(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            String cleaned = raw.trim();
            if (cleaned.isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double parseDoubleOrDefault(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            String cleaned = raw.trim();
            if (cleaned.isEmpty()) {
                return fallback;
            }
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }


    private int parsePageIndex(String action) {
        if (action == null || !action.startsWith("page")) {
            return currentPageIndex;
        }
        try {
            int index = Integer.parseInt(action.substring(4)) - 1;
            return Math.max(0, Math.min(BetterScoreBoardConfig.MAX_PAGES - 1, index));
        } catch (NumberFormatException e) {
            return currentPageIndex;
        }
    }

    private void updateDraft(EditorSubmission submission) {
        PageDraft current = currentPage();
        current.title = submission.title();
        current.lines = trimTrailingEmpty(submission.lines());
        current.durationSeconds = Math.max(1.0, submission.pageDurationSeconds());
        current.refreshSeconds = Math.max(0.25, submission.pageRefreshSeconds());
        current.worlds = submission.worlds();
    }

    private void refreshPageUI() {
        UICommandBuilder builder = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        populatePageFields(builder);
        sendUpdate(builder, events, false);
    }

    private void reloadFromService() {
        this.config = service.currentConfig();
        this.pages.clear();
        List<BetterScoreBoardConfig.PageConfig> updatedPages = service.snapshotPages();
        for (BetterScoreBoardConfig.PageConfig page : updatedPages) {
            this.pages.add(PageDraft.from(page));
        }
        while (this.pages.size() < BetterScoreBoardConfig.MAX_PAGES) {
            this.pages.add(PageDraft.empty(this.pages.size() + 1));
        }
        this.currentPageIndex = Math.max(0, Math.min(BetterScoreBoardConfig.MAX_PAGES - 1, service.activePageIndex()));
        this.rotationEnabled = service.rotationEnabled();
        refreshPageUI();
    }

    private void populatePageFields(UICommandBuilder builder) {
        PageDraft current = currentPage();
        LineParts titleParts = parseLine(current.title);
        builder.set("#EditorTitle.Text", "Scoreboard Editor - Page " + (currentPageIndex + 1));
        builder.set("#TitleInput.Value", titleParts.text());
        builder.set("#TitleColorHex.Value", titleParts.color().isEmpty() ? "#f6f8ff" : titleParts.color());
        builder.set("#ActivePageLabel.Text", "Page active: " + (currentPageIndex + 1));
        builder.set("#AutoRotateToggle #CheckBox.Value", rotationEnabled);
        builder.set("#PageDurationInput.Value", formatSeconds(current.durationSeconds));
        builder.set("#PageRefreshInput.Value", formatSeconds(current.refreshSeconds));
        builder.set("#PageWorldsInput.Value", formatWorlds(current.worlds));
        int max = Math.min(BetterScoreBoardHud.MAX_LINES, Math.max(1, config.maxLines()));
        for (int i = 0; i < BetterScoreBoardHud.MAX_LINES; i++) {
            String rowSelector = "#Line" + (i + 1) + "Row.Visible";
            String valueSelector = "#Line" + (i + 1) + "Input.Value";
            String colorSelector = "#Line" + (i + 1) + "Color.Value";
            boolean withinLimit = i < max;
            builder.set(rowSelector, withinLimit);
            LineParts parts = i < current.lines.size() ? parseLine(current.lines.get(i)) : new LineParts("", "");
            builder.set(valueSelector, parts.text());
            builder.set(colorSelector, parts.color());
        }
        for (int i = 0; i < BetterScoreBoardConfig.MAX_PAGES; i++) {
            String selector = "#Page" + (i + 1) + "Button.Visible";
            builder.set(selector, true);
        }
    }

    private String formatSeconds(double seconds) {
        return String.format("%.1f", seconds);
    }

    private String formatWorlds(List<String> worlds) {
        if (worlds == null || worlds.isEmpty()) {
            return "";
        }
        return String.join(", ", worlds);
    }

    private List<String> parseWorlds(String raw) {
        List<String> values = new ArrayList<>();
        if (raw == null) {
            return values;
        }
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase();
            if (!values.contains(lower)) {
                values.add(lower);
            }
        }
        return values;
    }

    private List<String> trimTrailingEmpty(List<String> lines) {
        List<String> trimmed = new ArrayList<>(lines);
        while (!trimmed.isEmpty()) {
            String last = trimmed.get(trimmed.size() - 1);
            if (last == null || last.isEmpty()) {
                trimmed.remove(trimmed.size() - 1);
            } else {
                break;
            }
        }
        return trimmed;
    }

    private List<BetterScoreBoardConfig.PageConfig> buildUpdatedPages() {
        List<BetterScoreBoardConfig.PageConfig> updated = new ArrayList<>();
        for (PageDraft draft : pages) {
            List<String> sanitized = new ArrayList<>(draft.lines);
            updated.add(new BetterScoreBoardConfig.PageConfig(
                draft.title,
                sanitized,
                (long) Math.max(1_000, draft.durationSeconds * 1000),
                (long) Math.max(250, draft.refreshSeconds * 1000),
                draft.worlds
            ));
        }
        return updated;
    }

    private PageDraft currentPage() {
        return pages.get(Math.max(0, Math.min(pages.size() - 1, currentPageIndex)));
    }

    static final class EditorEventData {
        static final BuilderCodec<EditorEventData> CODEC = BuilderCodec.builder(EditorEventData.class, EditorEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
                .append(new KeyedCodec<>("@Title", Codec.STRING), (e, v) -> e.title = v, e -> e.title).add()
                .append(new KeyedCodec<>("RotationToggle", Codec.STRING), (e, v) -> e.rotationToggle = v, e -> e.rotationToggle).add()
                .append(new KeyedCodec<>("@PageDuration", Codec.STRING), (e, v) -> e.pageDuration = v, e -> e.pageDuration).add()
                .append(new KeyedCodec<>("@PageRefresh", Codec.STRING), (e, v) -> e.pageRefresh = v, e -> e.pageRefresh).add()
                .append(new KeyedCodec<>("@PageWorlds", Codec.STRING), (e, v) -> e.pageWorlds = v, e -> e.pageWorlds).add()
                .append(new KeyedCodec<>("@Line1", Codec.STRING), (e, v) -> e.line1 = v, e -> e.line1).add()
                .append(new KeyedCodec<>("@Line2", Codec.STRING), (e, v) -> e.line2 = v, e -> e.line2).add()
                .append(new KeyedCodec<>("@Line3", Codec.STRING), (e, v) -> e.line3 = v, e -> e.line3).add()
                .append(new KeyedCodec<>("@Line4", Codec.STRING), (e, v) -> e.line4 = v, e -> e.line4).add()
                .append(new KeyedCodec<>("@Line5", Codec.STRING), (e, v) -> e.line5 = v, e -> e.line5).add()
                .append(new KeyedCodec<>("@Line6", Codec.STRING), (e, v) -> e.line6 = v, e -> e.line6).add()
                .append(new KeyedCodec<>("@Line7", Codec.STRING), (e, v) -> e.line7 = v, e -> e.line7).add()
                .append(new KeyedCodec<>("@Line8", Codec.STRING), (e, v) -> e.line8 = v, e -> e.line8).add()
                .append(new KeyedCodec<>("@Line9", Codec.STRING), (e, v) -> e.line9 = v, e -> e.line9).add()
                .append(new KeyedCodec<>("@Line10", Codec.STRING), (e, v) -> e.line10 = v, e -> e.line10).add()
                .append(new KeyedCodec<>("@Line11", Codec.STRING), (e, v) -> e.line11 = v, e -> e.line11).add()
                .append(new KeyedCodec<>("@Line12", Codec.STRING), (e, v) -> e.line12 = v, e -> e.line12).add()
                .append(new KeyedCodec<>("@ColorHex1", Codec.STRING), (e, v) -> e.colorHex1 = v, e -> e.colorHex1).add()
                .append(new KeyedCodec<>("@ColorHex2", Codec.STRING), (e, v) -> e.colorHex2 = v, e -> e.colorHex2).add()
                .append(new KeyedCodec<>("@ColorHex3", Codec.STRING), (e, v) -> e.colorHex3 = v, e -> e.colorHex3).add()
                .append(new KeyedCodec<>("@ColorHex4", Codec.STRING), (e, v) -> e.colorHex4 = v, e -> e.colorHex4).add()
                .append(new KeyedCodec<>("@ColorHex5", Codec.STRING), (e, v) -> e.colorHex5 = v, e -> e.colorHex5).add()
                .append(new KeyedCodec<>("@ColorHex6", Codec.STRING), (e, v) -> e.colorHex6 = v, e -> e.colorHex6).add()
                .append(new KeyedCodec<>("@ColorHex7", Codec.STRING), (e, v) -> e.colorHex7 = v, e -> e.colorHex7).add()
                .append(new KeyedCodec<>("@ColorHex8", Codec.STRING), (e, v) -> e.colorHex8 = v, e -> e.colorHex8).add()
                .append(new KeyedCodec<>("@ColorHex9", Codec.STRING), (e, v) -> e.colorHex9 = v, e -> e.colorHex9).add()
                .append(new KeyedCodec<>("@ColorHex10", Codec.STRING), (e, v) -> e.colorHex10 = v, e -> e.colorHex10).add()
                .append(new KeyedCodec<>("@ColorHex11", Codec.STRING), (e, v) -> e.colorHex11 = v, e -> e.colorHex11).add()
                .append(new KeyedCodec<>("@ColorHex12", Codec.STRING), (e, v) -> e.colorHex12 = v, e -> e.colorHex12).add()
                .append(new KeyedCodec<>("@TitleColorHex", Codec.STRING), (e, v) -> e.titleColorHex = v, e -> e.titleColorHex).add()
                .build();

        private String action;
        private String title;
        private String titleColorHex;
        private String rotationToggle;
        private String pageDuration;
        private String pageRefresh;
        private String pageWorlds;
        private String line1;
        private String line2;
        private String line3;
        private String line4;
        private String line5;
        private String line6;
        private String line7;
        private String line8;
        private String line9;
        private String line10;
        private String line11;
        private String line12;
        private String colorHex1;
        private String colorHex2;
        private String colorHex3;
        private String colorHex4;
        private String colorHex5;
        private String colorHex6;
        private String colorHex7;
        private String colorHex8;
        private String colorHex9;
        private String colorHex10;
        private String colorHex11;
        private String colorHex12;
        EditorEventData() {
        }
    }

    private record LineParts(String color, String text) {}
    private record EditorSubmission(String title, List<String> lines, double pageDurationSeconds, double pageRefreshSeconds, List<String> worlds) {}

    private static final class PageDraft {
        String title;
        List<String> lines;
        double durationSeconds;
        double refreshSeconds;
        List<String> worlds;

        PageDraft(String title, List<String> lines, double durationSeconds, double refreshSeconds, List<String> worlds) {
            this.title = title != null ? title : "";
            this.lines = lines != null ? lines : new ArrayList<>();
            this.durationSeconds = durationSeconds;
            this.refreshSeconds = refreshSeconds;
            this.worlds = worlds != null ? worlds : new ArrayList<>();
        }

        static PageDraft from(BetterScoreBoardConfig.PageConfig page) {
            return new PageDraft(page.title(), new ArrayList<>(page.lines()), page.durationMillis() / 1000.0, page.refreshMillis() / 1000.0, new ArrayList<>(page.worlds()));
        }

        static PageDraft empty(int pageNumber) {
            return new PageDraft("Page " + pageNumber, new ArrayList<>(), 8.0, 2.5, new ArrayList<>());
        }
    }
}
