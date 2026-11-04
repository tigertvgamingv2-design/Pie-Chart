package com.cdmstudios.piechart.listeners;

import com.cdmstudios.piechart.commands.PieChartCommand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChunkChangeListener implements Listener {

    private final PieChartCommand command;

    public ChunkChangeListener(PieChartCommand command) {
        this.command = command;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!command.isEnabled(player)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getWorld() != to.getWorld()
                || (from.getBlockX() >> 4) != (to.getBlockX() >> 4)
                || (from.getBlockZ() >> 4) != (to.getBlockZ() >> 4)) {
            command.update(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        command.remove(event.getPlayer());
    }
}
