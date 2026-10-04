package com.himdsl.runtime;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof GuiHolder)) return;
        GuiRef gui = ((GuiHolder) holder).getGui();
        Player p = (Player) e.getWhoClicked();

        int raw = e.getRawSlot();
        // 只拦截 GUI 内部槽位；玩家背包内操作放行
        if (raw >= 0 && raw < gui.rows * 9) {
            e.setCancelled(true);
            gui.handleClick(raw, e.getClick(), p);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof GuiHolder)) return;
        GuiRef gui = ((GuiHolder) holder).getGui();
        gui.fireClose((Player) e.getPlayer());
    }
}