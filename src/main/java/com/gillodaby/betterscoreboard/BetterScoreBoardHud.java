package com.gillodaby.betterscoreboard;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;

final class BetterScoreBoardHud extends CustomUIHud {

    static final int MAX_LINES = 12;
    private static final int MAX_SEGMENTS = 25;
    private static final String DEFAULT_TEXT_COLOR = "#f6f8ff";
    private static final int ROOT_WIDTH = 280;
    private static final int ROOT_TOP = 400;
    private static final int ROOT_RIGHT = 1;
    private static final int ROOT_PADDING_TOP = 10;
    private static final int ROOT_PADDING_BOTTOM = 10;
    private static final int HEADER_WIDTH = 260;
    private static final int HEADER_PADDING_BOTTOM = 8;
    private static final int LOGO_HEIGHT = 64;
    private static final int TITLE_HEIGHT = 22;
    private static final int TITLE_TOP = 5;
    private static final int LINES_HEIGHT = 180;
    private static final int DIVIDER_HEIGHT = 4;
    private static final int DIVIDER_TOP = 6;

    private final BetterScoreBoardConfig config;
    private volatile ScoreboardView currentView;

    BetterScoreBoardHud(PlayerRef ref, BetterScoreBoardConfig config) {
        super(ref);
        this.config = config;
    }

    @Override
    protected void build(UICommandBuilder builder) {
        writeHud(builder, currentView);
    }

    void refresh(Player player, PlayerRef ref, ScoreboardView view) {
        this.currentView = view;
        // Delegate to MultipleHUD so only our HUD entry is rebuilt.
        MultipleHUD.getInstance().setCustomHud(player, ref, "BetterScoreBoard", this);
    }

    void hideHud() {
        UICommandBuilder builder = new UICommandBuilder();
        builder.append("Pages/GilloDaby_BetterScoreBoard.ui");
        builder.set("#BoardRoot.Visible", false);
        update(true, builder);
    }

    private void writeHud(UICommandBuilder builder, ScoreboardView view) {
        builder.append("Pages/GilloDaby_BetterScoreBoard.ui");

        if (view == null) {
            builder.set("#BoardRoot.Visible", false);
            return;
        }

        builder.set("#BoardRoot.Visible", true);
        builder.set("#Divider.Visible", view.dividerVisible());
        builder.set("#BoardTitle.Text", view.title());
        boolean showTitle = view.title() != null && !view.title().trim().isEmpty();
        boolean showLogo = view.logoVisible();
        builder.set("#BoardTitle.Visible", showTitle);
        builder.set("#BoardLogo.Visible", showLogo);
        builder.set("#Header.Visible", showTitle || showLogo);
        updateLayoutAnchors(builder, showTitle, showLogo, view.dividerVisible());
        // Logo texture is static in UI; only visibility is toggled at runtime.
        if (view.titleColor() != null && !view.titleColor().isEmpty()) {
            builder.set("#BoardTitle.Style.TextColor", view.titleColor());
        } else {
            builder.set("#BoardTitle.Style.TextColor", "#f6f8ff");
        }

        int maxVisible = Math.min(MAX_LINES, config.maxLines());
        for (int i = 0; i < MAX_LINES; i++) {
            String baseId = "#Line" + (i + 1);
            String rowSelector = baseId + "Row.Visible";

            if (view.lines() != null && i < maxVisible && i < view.lines().size()) {
                ScoreboardView.LineRender render = view.lines().get(i);
                builder.set(rowSelector, true);
                List<ScoreboardView.LineSegment> segments = render.segments();
                for (int segmentIndex = 0; segmentIndex < MAX_SEGMENTS; segmentIndex++) {
                    String segmentId = segmentIndex == 0 ? baseId : baseId + "Segment" + (segmentIndex + 1);
                    String textSelector = segmentId + ".Text";
                    String colorSelector = segmentId + ".Style.TextColor";
                    String boldSelector = segmentId + ".Style.RenderBold";
                    String visibleSelector = segmentId + ".Visible";
                    if (segmentIndex < segments.size()) {
                        ScoreboardView.LineSegment segment = segments.get(segmentIndex);
                        String color = segment.color();
                        if (color == null || color.isEmpty()) {
                            color = DEFAULT_TEXT_COLOR;
                        }
                        builder.set(textSelector, segment.text());
                        builder.set(colorSelector, color);
                        builder.set(boldSelector, segment.bold());
                        builder.set(visibleSelector, !segment.text().isEmpty());
                    } else {
                        builder.set(textSelector, "");
                        builder.set(colorSelector, DEFAULT_TEXT_COLOR);
                        builder.set(boldSelector, false);
                        builder.set(visibleSelector, false);
                    }
                }
            } else {
                builder.set(rowSelector, false);
                for (int segmentIndex = 0; segmentIndex < MAX_SEGMENTS; segmentIndex++) {
                    String segmentId = segmentIndex == 0 ? baseId : baseId + "Segment" + (segmentIndex + 1);
                    builder.set(segmentId + ".Text", "");
                    builder.set(segmentId + ".Style.TextColor", DEFAULT_TEXT_COLOR);
                    builder.set(segmentId + ".Style.RenderBold", false);
                    builder.set(segmentId + ".Visible", false);
                }
            }
        }
    }

    private void updateLayoutAnchors(UICommandBuilder builder, boolean showTitle, boolean showLogo, boolean showDivider) {
        int headerHeight = calculateHeaderHeight(showTitle, showLogo);
        int rootHeight = calculateRootHeight(headerHeight, showDivider);
        builder.setObject("#Header.Anchor", buildAnchor(HEADER_WIDTH, headerHeight, null, null));
        builder.setObject("#BoardRoot.Anchor", buildAnchor(ROOT_WIDTH, rootHeight, ROOT_RIGHT, ROOT_TOP));
    }

    private int calculateHeaderHeight(boolean showTitle, boolean showLogo) {
        int headerHeight = 0;
        if (showLogo) {
            headerHeight += LOGO_HEIGHT;
        }
        if (showTitle) {
            headerHeight += TITLE_HEIGHT + TITLE_TOP;
        }
        if (headerHeight > 0) {
            headerHeight += HEADER_PADDING_BOTTOM;
        }
        return headerHeight;
    }

    private int calculateRootHeight(int headerHeight, boolean showDivider) {
        int rootHeight = ROOT_PADDING_TOP + ROOT_PADDING_BOTTOM + LINES_HEIGHT;
        if (showDivider) {
            rootHeight += DIVIDER_HEIGHT + DIVIDER_TOP;
        }
        rootHeight += headerHeight;
        return rootHeight;
    }

    private Anchor buildAnchor(Integer width, Integer height, Integer right, Integer top) {
        Anchor anchor = new Anchor();
        if (width != null) {
            anchor.setWidth(Value.of(width));
        }
        if (height != null) {
            anchor.setHeight(Value.of(height));
        }
        if (right != null) {
            anchor.setRight(Value.of(right));
        }
        if (top != null) {
            anchor.setTop(Value.of(top));
        }
        return anchor;
    }
}
