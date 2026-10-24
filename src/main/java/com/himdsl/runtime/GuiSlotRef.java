package com.himdsl.runtime;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUISlot 引用。持有图标、显示名、lore、四个回调。
 * 默认 icon = AIR，无回调。
 */
public class GuiSlotRef implements RefLike {
    private Object icon;                       // ItemStack 或 Material
    private String name;                       // 显示名（可含颜色码）
    private List<String> lore = new ArrayList<>();
    private final Map<String, Object> callbacks = new HashMap<>();

    public GuiSlotRef() {
        this.icon = new ItemStack(Material.AIR);
    }

    public GuiSlotRef(Object icon) {
        setIcon(icon);
    }

    private void setIcon(Object v) {
        if (v == null) { this.icon = new ItemStack(Material.AIR); return; }
        if (v instanceof ItemStack) { this.icon = v; return; }
        if (v instanceof Material) { this.icon = new ItemStack((Material) v); return; }
        throw new RuntimeException("icon 必须是 ItemStack 或 Material，实际: "
                + v.getClass().getSimpleName());
    }

    // ---------- RefLike ----------
    @Override
    public boolean hasField(String field) {
        switch (field) {
            case "icon": case "item":
            case "name": case "lore":
            case "onLeftClick": case "onRightClick":
            case "onShiftLeftClick": case "onShiftRightClick":
                return true;
            default: return false;
        }
    }

    @Override
    public Object getField(String field) {
        switch (field) {
            case "icon": case "item": return icon;
            case "name": return name;
            case "lore": return lore;
            case "onLeftClick":        return callbacks.get("onLeftClick");
            case "onRightClick":       return callbacks.get("onRightClick");
            case "onShiftLeftClick":   return callbacks.get("onShiftLeftClick");
            case "onShiftRightClick":  return callbacks.get("onShiftRightClick");
            default: return null;
        }
    }

    @Override
    public void setField(String field, Object value) {
        switch (field) {
            case "icon":
            case "item":
                setIcon(value);
                break;
            case "name":
                this.name = value == null ? null : value.toString();
                break;
            case "lore":
                if (value == null) {
                    this.lore = new ArrayList<>();
                } else if (value instanceof List) {
                    this.lore = new ArrayList<>();
                    for (Object o : (List<?>) value) this.lore.add(String.valueOf(o));
                } else {
                    throw new RuntimeException("lore 必须是列表");
                }
                break;
            case "onLeftClick":
            case "onRightClick":
            case "onShiftLeftClick":
            case "onShiftRightClick":
                if (value == null) {
                    callbacks.remove(field);
                } else if (value instanceof LambdaValue || value instanceof Runnable) {
                    callbacks.put(field, value);
                } else {
                    throw new RuntimeException(field + " 必须是 lambda 或 Runnable，实际: "
                            + value.getClass().getSimpleName());
                }
                break;
            default:
                throw new RuntimeException("GUISlot 未知字段: " + field);
        }
    }

    @Override
    public Object invoke(String method, List<Object> args) {
        throw new RuntimeException("GUISlot 不支持方法: " + method);
    }

    // ---------- 内部 ----------
    /** 生成写入 inventory 用的 ItemStack（应用 name / lore） */
    public ItemStack buildIcon() {
        ItemStack base;
        if (icon instanceof ItemStack) {
            base = ((ItemStack) icon).clone();
        } else if (icon instanceof Material) {
            base = new ItemStack((Material) icon);
        } else {
            base = new ItemStack(Material.AIR);
        }
        if (base.getType() == Material.AIR) return base;

        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            base.setItemMeta(meta);
        }
        return base;
    }

    /** 触发指定回调；玩家通过 _trigger_player 注入，脚本里 @s 可拿 */
    public void fire(String kind, Player p) {
        Object cb = callbacks.get(kind);
        if (cb == null) return;
        if (cb instanceof LambdaValue) {
            Map<String, Object> extra = new HashMap<>();
            extra.put("_trigger_player", p);
            ((LambdaValue) cb).applyWithBindings(Collections.emptyList(), extra);
        } else if (cb instanceof Runnable) {
            ((Runnable) cb).run();
        }
    }
}