package com.himdsl.runtime;

import com.himdsl.api.PlaceholderHandler;
import com.himdsl.HimDSLPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;

import java.util.*;

public class PlaceholderResolver {
    private final HimDSLRuntime runtime;
    private final Map<String, PlaceholderHandler> customHandlers = new HashMap<>();

    public PlaceholderResolver(HimDSLRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * 注册自定义占位符
     */
    public void registerCustomPlaceholder(String name, PlaceholderHandler handler) {
        customHandlers.put(name, handler);
    }

    public Object resolve(String type, List<Object> args,Environment env) {
        // 先尝试内置
        switch (type) {
            case "player":
            case "p": return resolvePlayer(args,env);
            case "world":
            case "w": return resolveWorld(args);
            case "server":
            case "s": return resolveServer(args);
            case "boss":
            case "b": return resolveBoss(args);
            case "haveVar": return resolveHaveVar(args ,env);
            default:
                // 尝试自定义
                PlaceholderHandler handler = customHandlers.get(type);
                if (handler != null) {
                    return handler.resolve(args);
                }
                throw new RuntimeException("未知占位符类型: " + type);
        }
    }

    // ---------- 玩家占位符 ----------
// ---------- 玩家占位符 ----------
private Object resolvePlayer(List<Object> args, Environment env) {
    if (args.size() < 2) throw new RuntimeException("$player需要至少2个参数");
    String varName = args.get(0).toString();
    String prop = args.get(1).toString().toLowerCase();

    Object playerObj = env.get(varName);
    if (playerObj == null) {
        return null;
    }

    // 1. EntityRef 引用路径：优先实时读取
    if (playerObj instanceof EntityRef) {
        EntityRef ref = (EntityRef) playerObj;
        // 命中字段名（x / y / z / yaw / pitch / name / uuid / world / health / hunger 等）
        if (ref.hasField(prop)) {
            return ref.getField(prop);
        }
        // 字段未命中：尝试拿到真实 Player 继续走原有属性 switch
        Entity e = ref.getEntity();
        if (e instanceof Player) {
            playerObj = e;
        } else {
            return null;
        }
    }

    // 2. 兼容旧 StructInstance 路径（历史脚本可能仍持有 StructInstance）
    if (playerObj instanceof StructInstance) {
        StructInstance inst = (StructInstance) playerObj;
        Object fieldValue = inst.get(prop);
        if (fieldValue != null) {
            return fieldValue;
        }
        String name = (String) inst.get("name");
        if (name == null) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) return null;
        playerObj = p;
    }

    // 3. 兜底：按变量名找 lastPlayer
    if (!(playerObj instanceof Player)) {
        Player p = runtime.eventListener.getLastPlayer(varName);
        if (p == null) return null;
        playerObj = p;
    }

    Player player = (Player) playerObj;

    // 4. 原有属性 switch（保持不变）
    switch (prop) {
        case "x": return player.getLocation().getX();
        case "y": return player.getLocation().getY();
        case "z": return player.getLocation().getZ();
        case "yaw": return (double) player.getLocation().getYaw();
        case "pitch": return (double) player.getLocation().getPitch();
        case "hp": return player.getHealth();
        case "hungry": return (double) player.getFoodLevel();
        case "name": return player.getName();
        case "uuid": return player.getUniqueId().toString();
        case "opengui": return player.getOpenInventory() != null;
        case "handlejoin": return runtime.eventListener.isPlayerJoin(player);
        case "handleleave": return runtime.eventListener.isPlayerLeave(player);
        case "breaktype": return runtime.eventListener.getLastBreakType(player);
        case "breakpos": return runtime.eventListener.getLastBreakPos(player);
        case "buildtype": return runtime.eventListener.getLastBuildType(player);
        case "buildpos": return runtime.eventListener.getLastBuildPos(player);
        case "runcommand": return runtime.eventListener.getLastCommand(player);
        case "slot": {
            if (args.size() > 2) {
                int slot = ((Number) args.get(2)).intValue();
                ItemStack item = player.getInventory().getItem(slot);
                return item == null ? "air" : item.getType().name().toLowerCase();
            }
            return null;
        }
        case "mainhand": {
            ItemStack item = player.getInventory().getItemInMainHand();
            return item == null ? "air" : item.getType().name().toLowerCase();
        }
        case "offhand": {
            ItemStack item = player.getInventory().getItemInOffHand();
            return item == null ? "air" : item.getType().name().toLowerCase();
        }
        case "bossdistance": {
            if (Bukkit.getPluginManager().getPlugin("HimDungeons") != null) {
                return 0.0;
            }
            return 0.0;
        }
        case "atroom": {
            if (args.size() >= 3) {
                String roomPath = args.get(2).toString();
                org.bukkit.plugin.Plugin himDungeons =
                        Bukkit.getPluginManager().getPlugin("HimDungeons");
                if (himDungeons != null && himDungeons.isEnabled()) {
                    try {
                        Object instance = himDungeons.getClass()
                                .getMethod("getInstance").invoke(null);
                        if (instance != null) {
                            Boolean result = (Boolean) instance.getClass()
                                    .getMethod("isPlayerAtRoom", java.util.UUID.class, String.class)
                                    .invoke(instance, player.getUniqueId(), roomPath);
                            if (HimDSLPlugin.DEBUG_MODE) {
                                Bukkit.getLogger().info("[HimDSL] atroom 结果: " + result
                                        + " for player " + player.getName()
                                        + ", roomPath: " + roomPath);
                            }
                            return result != null && result;
                        }
                    } catch (Exception e) {
                        if (HimDSLPlugin.DEBUG_MODE) {
                            Bukkit.getLogger().warning(
                                    "[HimDSL] 调用 HimDungeons.isPlayerAtRoom 失败: " + e.getMessage());
                        }
                    }
                }
            }
            return false;
        }
        case "issneaking": return player.isSneaking();
        case "issprinting": return player.isSprinting();
        case "gamemode": return player.getGameMode().name().toLowerCase();
        case "explevel": return player.getLevel();
        case "world": return player.getWorld().getName();
        default: throw new RuntimeException("未知玩家属性: " + prop);
    }
}
    // ---------- 世界占位符 ----------
private Object resolveWorld(List<Object> args) {
    if (args.isEmpty()) return null;
    String prop = args.get(0).toString().toLowerCase();

    // ---------- 统一处理坐标类操作的世界和偏移 ----------
    World world = null;
    int offset = 0;  // 如果显式传入世界名，则参数索引偏移 1

    // 对于需要坐标的操作，尝试提取世界名
    boolean isCoordOp = prop.equals("block") || prop.equals("iscontainer") || prop.equals("contain");
    if (isCoordOp) {
        // 检查是否显式传入世界名（参数格式: block, "worldName", x, y, z, ...）
        if (args.size() >= 5 && args.get(1) instanceof String) {
            world = Bukkit.getWorld(args.get(1).toString());
            if (world != null) offset = 1;
        }
        if (world == null) {
            world = runtime.eventListener.getLastWorld();
            if (world == null && !Bukkit.getWorlds().isEmpty()) {
                world = Bukkit.getWorlds().get(0);
            }
        }
        // 如果还是没有世界，则返回 null（避免 NPE）
        if (world == null) return null;
    }

    // ---------- 根据属性处理 ----------
    switch (prop) {
        case "name": {
            // 获取世界：可能传入世界名作为参数
            if (args.size() > 1) {
                String wName = args.get(1).toString();
                World w = Bukkit.getWorld(wName);
                if (w != null) return w.getName();
            }
            // 否则返回默认世界名
            if (runtime.eventListener.getLastWorld() != null)
                return runtime.eventListener.getLastWorld().getName();
            if (!Bukkit.getWorlds().isEmpty())
                return Bukkit.getWorlds().get(0).getName();
            return null;
        }
        case "id": {
            // 软依赖 HimDungeons，简化返回世界名
            return world != null ? world.getName() : null;
        }
        case "block": {
            Number xNum = (Number) args.get(1 + offset);
            Number yNum = (Number) args.get(2 + offset);
            Number zNum = (Number) args.get(3 + offset);
            return world.getBlockAt(xNum.intValue(), yNum.intValue(), zNum.intValue())
                    .getType().name().toLowerCase();
        }
        case "iscontainer": {
            Number xNum = (Number) args.get(1 + offset);
            Number yNum = (Number) args.get(2 + offset);
            Number zNum = (Number) args.get(3 + offset);
            return world.getBlockAt(xNum.intValue(), yNum.intValue(), zNum.intValue())
                    .getState() instanceof Container;
        }
        case "contain": {
            // contain 需要 4 个坐标参数 + 1 个槽位（加上世界名偏移）
            Number xNum = (Number) args.get(1 + offset);
            Number yNum = (Number) args.get(2 + offset);
            Number zNum = (Number) args.get(3 + offset);
            Number slotNum = (Number) args.get(4 + offset);
            if (world.getBlockAt(xNum.intValue(), yNum.intValue(), zNum.intValue())
                    .getState() instanceof Container) {
                Container container = (Container) world.getBlockAt(
                        xNum.intValue(), yNum.intValue(), zNum.intValue()
                ).getState();
                ItemStack item = container.getInventory().getItem(slotNum.intValue());
                return item == null ? "air" : item.getType().name().toLowerCase();
            }
            return "not_container";
        }
        case "hasplayer": {
            if (args.size() > 1) {
                String wName = args.get(1).toString();
                World w = Bukkit.getWorld(wName);
                if (w == null) return false;
                return !w.getPlayers().isEmpty();
            }
            return false;
        }
        // ---------- 软依赖 WorldEdit 的选区 ----------
        case "getx":
        case "gety":
        case "getz": {
            if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null) {
                // 简化返回 0（可扩展）
                return 0;
            }
            return 0;
        }
        default:
            throw new RuntimeException("未知世界属性: " + prop);
    }
}
    // ---------- 服务器占位符 ----------
private double getTPS() {
    try {
        // 反射获取 Bukkit 类的 getTPS 方法
        java.lang.reflect.Method method = Bukkit.class.getMethod("getTPS");
        double[] tps = (double[]) method.invoke(null);
        return tps[0];
    } catch (Exception e) {
        // 方法不存在或调用失败，返回默认值
        return 20.0;
    }
}


    private Object resolveServer(List<Object> args) {
        if (args.isEmpty()) return null;
        String prop = args.get(0).toString().toLowerCase();
        switch (prop) {
            case "tps": return getTPS();
            case "mspt": {
                double tps = getTPS();
                return tps == 20 ? 50.0 : 1000.0 / tps;
            }
            case "ping": {
                if (args.size() > 1) {
                    String name = args.get(1).toString();
                    Player p = Bukkit.getPlayer(name);
                    if (p != null) {
                        try {
                            return (double) p.getPing();
                        } catch (NoSuchMethodError e) { return 0.0; }
                    }
                }
                return 0.0;
            }
            case "serverloadevent": return runtime.eventListener.isServerLoad();
            case "servercloseevent": return false; // 无法检测，返回false
            case "onpluginenable": return runtime.eventListener.isPluginEnable();
            case "onplugindisable": return runtime.eventListener.isPluginDisable();
            case "ondungeonfail": {
                if (Bukkit.getPluginManager().getPlugin("HimDungeons") != null)
                    return runtime.eventListener.isDungeonFail();
                return false;
            }
            case "ondungeonwin": {
                if (Bukkit.getPluginManager().getPlugin("HimDungeons") != null)
                    return runtime.eventListener.isDungeonWin();
                return false;
            }
            default: throw new RuntimeException("未知服务器属性: " + prop);
        }
    }

// ---------- Boss 占位符（软依赖 HimDungeons） ----------
/**
 * 解析 $boss(<属性>[, <玩家>])$ 或 $b(...)$
 * 
 * 属性包括：x, y, z, yaw, pitch, world, health, maxhealth
 * 第二个参数可选，应为玩家对象（如 @s），用于精准定位该玩家所在世界的 Boss。
 * 若不传玩家，则返回第一个存活的 Boss（全局）。
 */
private Object resolveBoss(List<Object> args) {
    if (args.isEmpty()) {
        throw new RuntimeException("$boss 需要一个属性参数");
    }

    org.bukkit.plugin.Plugin himDungeons = Bukkit.getServer().getPluginManager().getPlugin("HimDungeons");
    if (himDungeons == null || !himDungeons.isEnabled()) {
        return null;
    }

    String prop = args.get(0).toString().toLowerCase();
    Player player = null;
    if (args.size() >= 2) {
        Object maybePlayer = args.get(1);
        if (maybePlayer instanceof Player) {
            player = (Player) maybePlayer;
        }
    }

    try {
        Class<?> bossAPIClass = Class.forName("com.him.dungeons.api.BossAPI");

        // 映射属性到方法名
        String methodName = null;
        boolean needPlayer = false;
        switch (prop) {
            case "x":        methodName = "getBossX";        needPlayer = true; break;
            case "y":        methodName = "getBossY";        needPlayer = true; break;
            case "z":        methodName = "getBossZ";        needPlayer = true; break;
            case "yaw":      methodName = "getBossYaw";      needPlayer = true; break;
            case "pitch":    methodName = "getBossPitch";    needPlayer = true; break;
            case "world":    methodName = "getBossWorld";    needPlayer = true; break;
            case "health":   methodName = "getBossHealth";   needPlayer = true; break;
            case "maxhealth":methodName = "getBossMaxHealth";needPlayer = true; break;
            default:
                throw new RuntimeException("未知 Boss 属性: " + prop);
        }

        // 优先调用带 Player 参数的方法（若玩家非空且该方法存在）
        if (needPlayer && player != null) {
            try {
                java.lang.reflect.Method method = bossAPIClass.getMethod(methodName, Player.class);
                return method.invoke(null, player);
            } catch (NoSuchMethodException e) {
                // 该方法可能不支持 Player 参数，则尝试无参方法
            }
        }

        // 回退：调用无参方法（旧版兼容）
        try {
            java.lang.reflect.Method method = bossAPIClass.getMethod(methodName);
            return method.invoke(null);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("BossAPI 不支持方法: " + methodName + "，请检查 HimDungeons 版本");
        }

    } catch (ClassNotFoundException e) {
        if (HimDSLPlugin.DEBUG_MODE) {
            Bukkit.getLogger().warning("[HimDSL] BossAPI 类未找到");
        }
        return null;
    } catch (Exception e) {
        if (HimDSLPlugin.DEBUG_MODE) {
            Bukkit.getLogger().warning("[HimDSL] 无法获取 Boss 属性: " + e.getMessage());
        }
        return null;
    }
}

    // ---------- haveVar ----------
    private boolean resolveHaveVar(List<Object> args ,Environment env) {
        if (args.isEmpty()) return false;
        String name = args.get(0).toString();
        return env.has(name);
    }
}