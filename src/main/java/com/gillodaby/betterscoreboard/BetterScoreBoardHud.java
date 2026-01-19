package com.gillodaby.betterscoreboard;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;

final class BetterScoreBoardHud extends CustomUIHud {

    static final int MAX_LINES = 12;
    private static final int MAX_SEGMENTS = 25;
    private static final String DEFAULT_TEXT_COLOR = "#f6f8ff";

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
        if (view.logoTexturePath() != null && !view.logoTexturePath().isEmpty()) {
            builder.set("#BoardLogo.TexturePath", view.logoTexturePath());
        }
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
                        builder.set(boldSelector, render.bold());
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
}
