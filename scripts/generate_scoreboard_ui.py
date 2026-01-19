from pathlib import Path

path = Path("src/main/resources/Common/UI/Custom/Pages/GilloDaby_BetterScoreBoard.ui")
lines = [
    "Group #BoardRoot {",
    "  Anchor: (Width: 280, Height: 320, Right: 1, Top: 400);",
    "  LayoutMode: Top;",
    "  Padding: (Left: 10, Right: 10, Top: 10, Bottom: 10);",
    "  Background: (TexturePath: \"../Common/ContainerPanelPatch.png\", Border: 6);",
    "",
    "  // Header (logo + titre)",
    "  Group #Header {",
    "    Anchor: (Width: 260, Height: 90);",
    "    LayoutMode: Top;",
    "    Padding: (Left: 4, Right: 4, Top: 0, Bottom: 8);",
    "",
    "    Group #BoardLogo {",
    "      Anchor: (Width: 252, Height: 64, Left: 0, Top: 0);",
    "      Background: \"../Textures/BetterScoreBoard/better_logo.png\";",
    "      Visible: true;",
    "    }",
    "",
    "    Label #BoardTitle {",
    "      @Text = \"Better ScoreBoard\";",
    "      Anchor: (Width: 252, Height: 22, Top: 5);",
    "      Style: (FontSize: 16, RenderBold: true, TextColor: #f2f4f8, HorizontalAlignment: Center, VerticalAlignment: Center);",
    "    }",
    "  }",
    "",
    "  Label #Divider {",
    "    Anchor: (Width: 296, Height: 4, Top: 6);",
    "    Background: (TexturePath: \"Tiles/TileEmpty.png\");",
    "  }",
    "",
    "  Group #Lines {",
    "    Anchor: (Width: 260, Height: 180);",
    "    LayoutMode: Top;",
    "    Padding: (Top: 6, Bottom: 4);",
    "",
]
for row in range(1, 13):
    name = f"Line{row}"
    lines.append(f"    Group #{name}Row {")
    lines.append("      Anchor: (Width: 260, Height: 18);")
    lines.append("      LayoutMode: Left;")
    lines.append("      Visible: false;")
    lines.append(
        f"      Label #{name} {{ @Text = \"\"; Anchor: (Height: 18); Style: (FontSize: 14, TextColor: #f6f8ff, VerticalAlignment: Center); }}"
    )
    for seg in range(2, 26):
        lines.append(
            f"      Label #{name}Segment{seg} {{ @Text = \"\"; Anchor: (Height: 18); Style: (FontSize: 14, TextColor: #f6f8ff, VerticalAlignment: Center); }}"
        )
    lines.append("    }")
    lines.append("")
lines.append("  }")
lines.append("}")
path.write_text("\n".join(lines) + "\n")
