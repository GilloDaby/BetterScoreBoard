package com.gillodaby.betterscoreboard;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.concurrent.CompletableFuture;

/**
 * /scoreboard reload
 * /scoreboard list
 * /scoreboard set <index> <text...>
 * /scoreboard add <text...>
 * /scoreboard remove <index>
 * /scoreboard save
 */
final class ScoreboardCommand extends AbstractCommand {

    private final BetterScoreBoardService service;
    private final BetterScoreBoardConfig config;
    private final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<Integer> setIndexArg;
    private final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<Integer> removeIndexArg;

    ScoreboardCommand(BetterScoreBoardService service, BetterScoreBoardConfig config) {
        super("scoreboard", "Edit BetterScoreBoard lines");
        this.service = service;
        this.config = config;
        setAllowsExtraArguments(true);

        // reload
        AbstractCommand reload = new AbstractCommand("reload", "Reload scoreboard config") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleReload(ctx);
            }
        };
        addSubCommand(reload);

        // list
        AbstractCommand list = new AbstractCommand("list", "List scoreboard lines") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleList(ctx);
            }
        };
        addSubCommand(list);

        // set <index> <text...>
        AbstractCommand set = new AbstractCommand("set", "Set a line") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleSet(ctx);
            }
        };
        this.setIndexArg = set.withRequiredArg("index", "line index (1-12)", ArgTypes.INTEGER);
        set.setAllowsExtraArguments(true);
        addSubCommand(set);

        // add <text...>
        AbstractCommand add = new AbstractCommand("add", "Append a line") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleAdd(ctx);
            }
        };
        add.setAllowsExtraArguments(true);
        addSubCommand(add);

        // remove <index>
        AbstractCommand remove = new AbstractCommand("remove", "Remove a line") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleRemove(ctx);
            }
        };
        this.removeIndexArg = remove.withRequiredArg("index", "line index (1-12)", ArgTypes.INTEGER);
        addSubCommand(remove);

        // save
        AbstractCommand save = new AbstractCommand("save", "Save lines to config.yaml") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleSave(ctx);
            }
        };
        addSubCommand(save);

        // show
        AbstractCommand show = new AbstractCommand("show", "Show the scoreboard HUD") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.show")) {
                    ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.show"));
                    return CompletableFuture.completedFuture(null);
                }
                Player player = ctx.senderAs(Player.class);
                service.showHud(player);
                ctx.sendMessage(service.text("Scoreboard affiché."));
                return CompletableFuture.completedFuture(null);
            }
        };
        addSubCommand(show);

        // off
        AbstractCommand off = new AbstractCommand("off", "Hide the scoreboard HUD") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.off")) {
                    ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.off"));
                    return CompletableFuture.completedFuture(null);
                }
                Player player = ctx.senderAs(Player.class);
                service.hideHud(player);
                ctx.sendMessage(service.text("Scoreboard caché."));
                return CompletableFuture.completedFuture(null);
            }
        };
        addSubCommand(off);

        // divider
        AbstractCommand divider = new AbstractCommand("divider", "Toggle the divider line") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleDivider(ctx);
            }
        };
        divider.setAllowsExtraArguments(true);
        divider.requirePermission("betterscoreboard.divider");
        addSubCommand(divider);

        // logo
        AbstractCommand logo = new AbstractCommand("logo", "Toggle the logo globally") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleLogo(ctx);
            }
        };
        logo.setAllowsExtraArguments(true);
        logo.requirePermission("betterscoreboard.logo");
        addSubCommand(logo);

        // help
        AbstractCommand help = new AbstractCommand("help", "Show help") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleHelp(ctx);
            }
        };
        addSubCommand(help);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.editor")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.editor"));
            return CompletableFuture.completedFuture(null);
        }
        Player player = ctx.senderAs(Player.class);
        if (player != null) {
            service.openEditor(player);
            return CompletableFuture.completedFuture(null);
        }
        return handleList(ctx);
    }

    private CompletableFuture<Void> handleReload(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.reload")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.reload"));
            return CompletableFuture.completedFuture(null);
        }
        service.reloadConfig();
        ctx.sendMessage(service.text("[BetterScoreBoard] reloaded config."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleList(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.list")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.list"));
            return CompletableFuture.completedFuture(null);
        }
        StringBuilder sb = new StringBuilder("Lines (" + service.lines().size() + "):");
        int i = 1;
        for (String line : service.lines()) {
            sb.append("\n").append(i).append(": ").append(line == null ? "" : line);
            i++;
        }
        ctx.sendMessage(service.text(sb.toString()));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleSet(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.set")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.set"));
            return CompletableFuture.completedFuture(null);
        }
        Integer idx = ctx.get(setIndexArg);
        if (idx == null || idx < 1 || idx > BetterScoreBoardHud.MAX_LINES) {
            ctx.sendMessage(service.text("Index must be between 1 and " + BetterScoreBoardHud.MAX_LINES));
            return CompletableFuture.completedFuture(null);
        }
        String value = parseTextAfter(ctx.getInputString(), 3);
        if (value.isEmpty()) {
            ctx.sendMessage(service.text("Usage: /scoreboard set <index> <text>"));
            return CompletableFuture.completedFuture(null);
        }
        service.setLine(idx - 1, value);
        ctx.sendMessage(service.text("Set line " + idx + " to: " + value));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleAdd(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.add")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.add"));
            return CompletableFuture.completedFuture(null);
        }
        String value = parseTextAfter(ctx.getInputString(), 2);
        if (value.isEmpty()) {
            ctx.sendMessage(service.text("Usage: /scoreboard add <text>"));
            return CompletableFuture.completedFuture(null);
        }
        boolean ok = service.addLine(value);
        if (!ok) {
            ctx.sendMessage(service.text("Cannot add more than " + BetterScoreBoardHud.MAX_LINES + " lines."));
            return CompletableFuture.completedFuture(null);
        }
        ctx.sendMessage(service.text("Added line: " + value));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleRemove(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.remove")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.remove"));
            return CompletableFuture.completedFuture(null);
        }
        Integer idx = ctx.get(removeIndexArg);
        if (idx == null || idx < 1 || idx > BetterScoreBoardHud.MAX_LINES) {
            ctx.sendMessage(service.text("Index must be between 1 and " + BetterScoreBoardHud.MAX_LINES));
            return CompletableFuture.completedFuture(null);
        }
        boolean ok = service.removeLine(idx - 1);
        if (!ok) {
            ctx.sendMessage(service.text("No line at index " + idx));
            return CompletableFuture.completedFuture(null);
        }
        ctx.sendMessage(service.text("Removed line " + idx));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleSave(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.save")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.save"));
            return CompletableFuture.completedFuture(null);
        }
        service.saveConfig();
        ctx.sendMessage(service.text("Saved scoreboard lines to config.yaml."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleDivider(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.divider")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.divider"));
            return CompletableFuture.completedFuture(null);
        }
        String targetArg = parseTextAfter(ctx.getInputString(), 2).trim();
        boolean current = service.dividerVisible();
        boolean target = current;
        if (targetArg.isEmpty()) {
            target = !current;
        } else if (targetArg.equalsIgnoreCase("on") || targetArg.equalsIgnoreCase("show")) {
            target = true;
        } else if (targetArg.equalsIgnoreCase("off") || targetArg.equalsIgnoreCase("hide")) {
            target = false;
        } else if (targetArg.equalsIgnoreCase("toggle")) {
            target = !current;
        } else {
            ctx.sendMessage(service.text("Usage: /scoreboard divider [on|off|toggle]"));
            return CompletableFuture.completedFuture(null);
        }
        boolean changed = service.setDividerVisible(target, true);
        String state = target ? "visible" : "hidden";
        ctx.sendMessage(service.text(changed ? "Divider is now " + state + "." : "Divider is already " + state + "."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleLogo(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.logo")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Missing permission: betterscoreboard.logo"));
            return CompletableFuture.completedFuture(null);
        }
        String targetArg = parseTextAfter(ctx.getInputString(), 2).trim();
        boolean currentVisible = service.logoVisible();
        boolean targetVisible = currentVisible;
        if (targetArg.isEmpty()) {
            targetVisible = !currentVisible;
        } else if (targetArg.equalsIgnoreCase("on") || targetArg.equalsIgnoreCase("show")) {
            targetVisible = true;
        } else if (targetArg.equalsIgnoreCase("off") || targetArg.equalsIgnoreCase("hide")) {
            targetVisible = false;
        } else if (targetArg.equalsIgnoreCase("toggle")) {
            targetVisible = !currentVisible;
        } else {
            ctx.sendMessage(service.text("Usage: /scoreboard logo [on|off|toggle]"));
            return CompletableFuture.completedFuture(null);
        }
        boolean changed = service.setLogoVisible(targetVisible, true);
        String state = targetVisible ? "visible" : "hidden";
        ctx.sendMessage(service.text(changed ? "Logo is now " + state + "." : "Logo is already " + state + "."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleHelp(CommandContext ctx) {
        if (!ctx.isPlayer() || !ctx.senderAs(Player.class).hasPermission("betterscoreboard.help")) {
            ctx.sendMessage(service.text("[BetterScoreBoard] Permission permission: betterscoreboard.help"));
            return CompletableFuture.completedFuture(null);
        }
        String help = String.join("\n",
            "/scoreboard (Open the panel editor)",
            "/scoreboard reload",
            "/scoreboard list",
            "/scoreboard set <index> <text>",
            "/scoreboard add <text>",
            "  tip: you can wrap text in quotes to keep spaces, e.g. /scoreboard add \"    centered text    \"",
            "/scoreboard remove <index>",
            "/scoreboard save",
            "/scoreboard divider [on|off|toggle]",
            "/scoreboard logo [on|off|toggle]",
            "/scoreboard show",
            "/scoreboard off",
            "/scoreboard help",
            "Placeholders: " + service.placeholdersLine()
        );
        ctx.sendMessage(service.text(help));
        return CompletableFuture.completedFuture(null);
    }

    private String parseTextAfter(String input, int skipTokens) {
        if (input == null || input.isEmpty()) return "";
        String[] tokens = input.split(" ");
        if (tokens.length <= skipTokens) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = skipTokens; i < tokens.length; i++) {
            if (i > skipTokens) sb.append(" ");
            sb.append(tokens[i]);
        }
        String raw = sb.toString().trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return raw;
    }
}
