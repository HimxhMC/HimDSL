package com.himdsl.runtime;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** 绑定 GuiRef 与 Bukkit Inventory，供事件里 getHolder() 反查。 */
public class GuiHolder implements InventoryHolder {
    private final GuiRef gui;
    private Inventory inventory;

    public GuiHolder(GuiRef gui) { this.gui = gui; }
    public GuiRef getGui() { return gui; }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}