package com.gillodaby.betterscoreboard;

import java.util.List;

record ScoreboardView(String title, String titleColor, String logoTexturePath, int offsetRight, int offsetTop, List<ScoreboardView.LineRender> lines, boolean dividerVisible) {
    record LineRender(List<LineSegment> segments, boolean bold) {}

    record LineSegment(String text, String color) {}
}
