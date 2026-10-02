package com.himdsl.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * GUI 引用。
 *
 * 继承 AbstractList&lt;List&lt;GuiSlotRef&gt;&gt; 让它天然支持 gui[r] 与 gui[r][c] 索引：
 *   gui[r]        → 第 r 行 (List&lt;GuiSlotRef&gt;)
 *   gui[r][c]     → 第 r 行第 c 列的 GuiSlotRef
 *   gui[r][c] = s → 直接替换 grid 中对象（不写 Bukkit inventory，需要显式 update）
 */
public class GuiRef extends AbstractList<List<GuiSlotRef>> implements RefLike {
    public final int rows;
    public final String title;
    public final Inventory inventory;
    public final GuiHolder holder;

    private final HimDSLRuntime runtime;
    private final List<List<GuiSlotRef>> grid = new ArrayList<>();
    private Object onCloseCb;

    public GuiRef(int rows, String title, HimDSLRuntime runtime) {
        if (rows < 1 || rows > 6) {
            throw new RuntimeException("GUI 行数必须在 1~6 之间，实际: " + rows);
        }
        this.rows = rows;
        this.title = title == null ? "" : title;
        this.runtime = runtime;
        runtime.ensureGuiListener();

        this.holder = new GuiHolder(this);
        this.inventory = Bukkit.createInventory(holder, rows * 9, this.title);
        holder.setInventory(this.inventory);

        for (int r = 0; r < rows; r++) {
            List<GuiSlotRef> row = new ArrayList<>(9);
            for (int c = 0; c < 9; c++) {
                row.add(new GuiSlotRef());           // 默认 AIR，无回调
            }
            grid.add(row);
        }
    }

    // ---------- AbstractList：让 gui[r] 工作 ----------
    @Override public int size() { return rows; }
    @Override public List<GuiSlotRef> get(int index) {
        if (index < 0 || index >= rows) {
            throw new RuntimeException("GUI 行索引越界: " + index + "，行数: " + rows);
        }
        return grid.get(index);
    }
    @Override public List<GuiSlotRef> set(int index, List<GuiSlotRef> element) {
        throw new RuntimeException("不允许直接替换整行；请用 gui[r][c] = slot");
    }

    // ---------- RefLike ----------
    @Override
    public boolean hasField(String field) {
        switch (field) {
            case "rows": case "title": return true;
            default: return false;
        }
    }
    @Override
    public Object getField(String field) {
        switch (field) {
            case "rows":  return rows;
            case "title": return title;
            default: return null;
        }
    }
    @Override
    public void setField(String field, Object value) {
        throw new RuntimeException("GUI 字段 " + field + " 是只读的");
    }

    @Override
    public Object invoke(String method, List<Object> args) {
        switch (method) {
            case "open": {
                Player p = asPlayer(args, 0, "open");
                openNextTick(p);
                return null;
            }
            case "close": {
                Player p = asPlayer(args, 0, "close");
                closeNextTick(p);
                return null;
            }
            case "update": {
                if (args.size() != 2) throw new RuntimeException("update(row, col) 需要 2 个参数");
                int r = toInt(args.get(0));
                int c = toInt(args.get(1));
                updateSlot(r, c);
                return null;
            }
            case "updateAll": {
                updateAllSlots();
                return null;
            }
            case "onClose": {
                if (args.isEmpty()) { this.onCloseCb = null; return null; }
                Object cb = args.get(0);
                if (cb != null && !(cb instanceof LambdaValue) && !(cb instanceof Runnable)) {
                    throw new RuntimeException("onClose 需要 lambda 或 Runnable");
                }
                this.onCloseCb = cb;
                return null;
            }
            default:
                throw new RuntimeException("GUI 不支持方法: " + method);
        }
    }

    // ---------- 内部 ----------
    private Player asPlayer(List<Object> args, int idx, String method) {
        if (args.size() <= idx) throw new RuntimeException(method + " 需要一个玩家参数");
        Object o = args.get(idx);
        if (o instanceof EntityRef) {
            Object e = ((EntityRef) o).getEntity();
            if (e instanceof Player) return (Player) e;
        }
        if (o instanceof Player) return (Player) o;
        throw new RuntimeException(method + " 的参数必须是玩家，实际: "
                + (o == null ? "null" : o.getClass().getSimpleName()));
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        throw new RuntimeException("需要数字参数，实际: "
                + (o == null ? "null" : o.getClass().getSimpleName()));
    }

    private void openNextTick(Player p) {
        Plugin plugin = runtime.getPlugin();
        if (plugin == null) { p.openInventory(inventory); return; }
        Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(inventory));
    }

    private void closeNextTick(Player p) {
        Plugin plugin = runtime.getPlugin();
        if (plugin == null) { p.closeInventory(); return; }
        Bukkit.getScheduler().runTask(plugin, p::closeInventory);
    }

    public void updateSlot(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= 9) {
            throw new RuntimeException("GUI 槽位越界: [" + r + "][" + c + "]");
        }
        GuiSlotRef s = grid.get(r).get(c);
        inventory.setItem(r * 9 + c, s.buildIcon());
        refreshViewers();
    }

    public void updateAllSlots() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                inventory.setItem(r * 9 + c, grid.get(r).get(c).buildIcon());
            }
        }
        refreshViewers();
    }

    /** 让所有正在打开此 GUI 的玩家刷新视图 */
    private void refreshViewers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().equals(inventory)) {
                p.updateInventory();
            }
        }
    }

    /** 触发 onClose 回调 */
    public void fireClose(Player p) {
        Object cb = onCloseCb;
        if (cb == null) return;
        try {
            if (cb instanceof LambdaValue) {
                Map<String, Object> extra = new HashMap<>();
                extra.put("_trigger_player", p);
                ((LambdaValue) cb).applyWithBindings(Collections.emptyList(), extra);
            } else if (cb instanceof Runnable) {
                ((Runnable) cb).run();
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[HimDSL] GUI onClose 回调异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** 点击事件路由入口 */
    public void handleClick(int rawSlot, ClickType click, Player p) {
        if (rawSlot < 0 || rawSlot >= rows * 9) return;
        int r = rawSlot / 9;
        int c = rawSlot % 9;
        GuiSlotRef s = grid.get(r).get(c);

        String kind;
        switch (click) {
            case LEFT:        kind = "onLeftClick";       break;
            case RIGHT:       kind = "onRightClick";      break;
            case SHIFT_LEFT:  kind = "onShiftLeftClick";  break;
            case SHIFT_RIGHT: kind = "onShiftRightClick"; break;
            default:          kind = "onLeftClick";       break;
        }
        try {
            s.fire(kind, p);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[HimDSL] GUI 回调 " + kind + " 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}