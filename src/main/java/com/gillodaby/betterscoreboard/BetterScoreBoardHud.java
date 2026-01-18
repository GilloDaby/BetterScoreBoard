package com.gillodaby.betterscoreboard;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

final class BetterScoreBoardHud extends CustomUIHud {

    static final int MAX_LINES = 12;

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
            String textSelector = baseId + ".Text";
            String visibleSelector = baseId + ".Visible";
            String colorSelector = baseId + ".Style.TextColor";

            if (view.lines() != null && i < maxVisible && i < view.lines().size()) {
                ScoreboardView.LineRender render = view.lines().get(i);
                builder.set(textSelector, render.text());
                if (render.color() != null && !render.color().isEmpty()) {
                    builder.set(colorSelector, render.color());
                } else {
                    builder.set(colorSelector, "#f6f8ff");
                }
                builder.set(baseId + ".Style.RenderBold", render.bold());
                builder.set(visibleSelector, true);
            } else {
                builder.set(textSelector, "");
                builder.set(colorSelector, "#f6f8ff");
                builder.set(baseId + ".Style.RenderBold", false);
                builder.set(visibleSelector, false);
            }
        }
    }
}
