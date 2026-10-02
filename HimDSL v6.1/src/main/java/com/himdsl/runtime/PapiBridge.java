package com.himdsl.runtime;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI 反射桥接。
 * 不直接依赖 PlaceholderAPI，插件未安装时全部安全降级。
 */
public class PapiBridge {
    private static boolean initialized = false;
    private static boolean papiAvailable = false;
    private static Class<?> placeholderAPIClass;
    private static Method setPlaceholdersMethod;
    private static final Map<String, String> localVariables = new ConcurrentHashMap<>();

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                return;
            }
            placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");

            // 查找 setPlaceholders(OfflinePlayer, String)
            for (Method m : placeholderAPIClass.getMethods()) {
                if (!m.getName().equals("setPlaceholders")) continue;
                if (m.getParameterCount() != 2) continue;
                Class<?>[] types = m.getParameterTypes();
                if (OfflinePlayer.class.isAssignableFrom(types[0]) && types[1] == String.class) {
                    setPlaceholdersMethod = m;
                    break;
                }
            }
            papiAvailable = setPlaceholdersMethod != null;
        } catch (Throwable ignored) {
            papiAvailable = false;
        }
    }

    /**
     * 获取变量值。
     * @param key 变量名，不带 %，例如 "player_name"
     * @param playerObj 可选玩家对象或玩家名，用于玩家相关变量
     */
    public static String get(String key, Object playerObj) {
        init();

        // 1. 本地变量优先
        if (localVariables.containsKey(key)) {
            return localVariables.get(key);
        }

        // 2. 尝试 PlaceholderAPI
        if (!papiAvailable) return "";

        try {
            OfflinePlayer player = toOfflinePlayer(playerObj);
            String placeholder = key.startsWith("%") && key.endsWith("%")
                    ? key
                    : "%" + key + "%";
            Object result = setPlaceholdersMethod.invoke(null, player, placeholder);
            return result != null ? result.toString() : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public static String get(String key) {
        return get(key, null);
    }

    /** 设置本地变量值 */
    public static void set(String key, String value) {
        localVariables.put(key, value);
    }

    /** 注册本地变量（初始为空字符串） */
    public static void register(String key) {
        localVariables.putIfAbsent(key, "");
    }

    /** 取消注册本地变量 */
    public static void unregister(String key) {
        localVariables.remove(key);
    }

    /** 是否存在本地变量 */
    public static boolean has(String key) {
        return localVariables.containsKey(key);
    }

    private static OfflinePlayer toOfflinePlayer(Object obj) {
        if (obj instanceof OfflinePlayer) return (OfflinePlayer) obj;
        if (obj instanceof String) {
            String name = (String) obj;
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) return online;
            return Bukkit.getOfflinePlayer(name);
        }
        return null;
    }
}