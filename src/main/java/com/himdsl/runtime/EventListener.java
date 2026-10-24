package com.himdsl.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.HashMap;
import java.util.Map;

public class EventListener implements Listener {
    private final HimDSLRuntime runtime;
    private boolean serverLoad = false, pluginEnable = false, pluginDisable = false;
    private boolean dungeonFail = false, dungeonWin = false;
    private World lastWorld;
    private final Map<String, Player> lastJoinPlayer = new HashMap<>();
    private final Map<String, Player> lastLeavePlayer = new HashMap<>();
    private final Map<String, String> lastBreakType = new HashMap<>();
    private final Map<String, int[]> lastBreakPos = new HashMap<>();
    private final Map<String, String> lastBuildType = new HashMap<>();
    private final Map<String, int[]> lastBuildPos = new HashMap<>();
    private final Map<String, String> lastCommand = new HashMap<>();

    public EventListener(HimDSLRuntime runtime) { this.runtime = runtime; }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) { serverLoad = true; lastWorld = Bukkit.getWorlds().get(0); }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) { pluginEnable = true; }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) { pluginDisable = true; }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        lastJoinPlayer.put(p.getName(), p);
        lastWorld = p.getWorld();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        lastLeavePlayer.put(p.getName(), p);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        lastBreakType.put(p.getName(), e.getBlock().getType().name().toLowerCase());
        lastBreakPos.put(p.getName(), new int[]{e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()});
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        lastBuildType.put(p.getName(), e.getBlock().getType().name().toLowerCase());
        lastBuildPos.put(p.getName(), new int[]{e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()});
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e) {
        lastCommand.put(e.getPlayer().getName(), e.getMessage());
    }

    public void setDungeonFail(boolean v) { dungeonFail = v; }
    public void setDungeonWin(boolean v) { dungeonWin = v; }

    public boolean isServerLoad() { return serverLoad; }
    public boolean isPluginEnable() { return pluginEnable; }
    public boolean isPluginDisable() { return pluginDisable; }
    public boolean isDungeonFail() { return dungeonFail; }
    public boolean isDungeonWin() { return dungeonWin; }
    public World getLastWorld() { return lastWorld; }
    public Player getLastPlayer(String name) {
        Player p = lastJoinPlayer.getOrDefault(name, null);
        if (p == null) p = Bukkit.getPlayerExact(name);
        return p;
    }
    public boolean isPlayerJoin(Player p) { return lastJoinPlayer.containsKey(p.getName()); }
    public boolean isPlayerLeave(Player p) { return lastLeavePlayer.containsKey(p.getName()); }
    public String getLastBreakType(Player p) { return lastBreakType.getOrDefault(p.getName(), ""); }
    public int[] getLastBreakPos(Player p) { return lastBreakPos.getOrDefault(p.getName(), new int[3]); }
    public String getLastBuildType(Player p) { return lastBuildType.getOrDefault(p.getName(), ""); }
    public int[] getLastBuildPos(Player p) { return lastBuildPos.getOrDefault(p.getName(), new int[3]); }
    public String getLastCommand(Player p) { return lastCommand.getOrDefault(p.getName(), ""); }
}