package com.gillodaby.betterscoreboard;

import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class BetterScoreBoardPlugin extends JavaPlugin {

    private BetterScoreBoardService service;

    public BetterScoreBoardPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
    }

    @Override
    public void start() {
        BetterScoreBoardConfig config = BetterScoreBoardConfig.load(getDataDirectory());
        service = new BetterScoreBoardService(config);

        // Command registration
        CommandManager.get().register(new ScoreboardCommand(service, config));

        EventBus bus = HytaleServer.get().getEventBus();
        bus.registerGlobal(PlayerReadyEvent.class, service::handlePlayerReady);
        bus.registerGlobal(PlayerDisconnectEvent.class, service::handlePlayerDisconnect);
        service.start();
        System.out.println("[BetterScoreBoard] Started with refresh " + config.refreshMillis() + " ms.");
    }

    @Override
    protected void shutdown() {
        if (service != null) {
            service.stop();
        }
    }
}
