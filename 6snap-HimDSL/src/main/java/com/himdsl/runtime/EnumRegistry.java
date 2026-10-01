package com.himdsl.runtime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
* 枚举解析中心。
*
* 支持三种写法：
*   1. 完全限定路径：org.bukkit.Material.STONE
*   2. 简单名：       GameMode.CREATIVE
*   3. 用户自定义：   MyColors.RED
*
* 所有解析结果都会缓存，避免重复反射。通过 clearCache() / clearUserEnums() 清空。
*/
public final class EnumRegistry {
    private EnumRegistry() {}
    
    /** 简单名 → 完全限定名 */
    private static final Map<String, String> simpleToFqcn = new ConcurrentHashMap<>();
    /** 完全限定名 → 已解析的枚举类 */
    private static final Map<String, Class<?>> classCache  = new ConcurrentHashMap<>();
    /** 用户自定义枚举名 → 常量集合 */
    private static final Map<String, Set<String>> userEnums = new ConcurrentHashMap<>();
    
    /**
    * 简单名探测时使用的默认包（只覆盖常用的主要包，避免全类路径扫描）。
    * 想用完全限定名就直接写 org.bukkit.xxx.YYY，不依赖此表。
    */
    private static final String[] DEFAULT_PACKAGES = {
        "org.bukkit",
        "org.bukkit.attribute",
        "org.bukkit.block",
        "org.bukkit.block.biome",
        "org.bukkit.block.data",
        "org.bukkit.block.sign",
        "org.bukkit.enchantments",
        "org.bukkit.entity",
        "org.bukkit.event",
        "org.bukkit.event.block",
        "org.bukkit.event.enchantment",
        "org.bukkit.event.entity",
        "org.bukkit.event.hanging",
        "org.bukkit.event.inventory",
        "org.bukkit.event.player",
        "org.bukkit.event.raid",
        "org.bukkit.event.server",
        "org.bukkit.event.vehicle",
        "org.bukkit.event.weather",
        "org.bukkit.event.world",
        "org.bukkit.inventory",
        "org.bukkit.inventory.meta",
        "org.bukkit.loot",
        "org.bukkit.map",
        "org.bukkit.permissions",
        "org.bukkit.potion",
        "org.bukkit.scoreboard",
        "org.bukkit.util",
    };
    
    /**
    * 解析枚举常量路径。
    * @param parts 例如 {"GameMode","CREATIVE"} 或 {"org","bukkit","Material","STONE"}
    * @return Java 枚举常量 / DSLEnumValue / null
    */
    public static Object resolveEnumPath(String[] parts) {
        if (parts == null || parts.length < 2) return null;
        
        String constantName = parts[parts.length - 1];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        String classPath = sb.toString();
        
        // 1) 用户自定义枚举（约定是两段 Name.CONST）
        if (parts.length == 2) {
            Set<String> constants = userEnums.get(parts[0]);
            if (constants != null && constants.contains(constantName)) {
                return new DSLEnumValue(parts[0], constantName);
            }
        }
        
        // 2) Java 枚举
        Class<?> enumClass = resolveEnumClass(classPath);
        if (enumClass == null || !enumClass.isEnum()) return null;
        for (Object c : enumClass.getEnumConstants()) {
            if (((Enum<?>) c).name().equals(constantName)) {
                return c;
            }
        }
        return null;
    }
    // ---------- 用户枚举 ----------
    public static void registerUserEnum(String name, Set<String> constants) {
        userEnums.put(name, new LinkedHashSet<>(constants));
    }
    
    public static boolean isUserEnum(String name) { return userEnums.containsKey(name); }
    
    public static Set<String> getUserEnumConstants(String name) {
        Set<String> c = userEnums.get(name);
        return c == null ? Collections.emptySet() : c;
    }
    
    // ---------- 缓存清理 ----------
    /** 清空全部（含 Bukkit 枚举缓存） */
    public static void clearCache() {
        simpleToFqcn.clear();
        classCache.clear();
        userEnums.clear();
    }
    
    /** 只清空用户自定义枚举（脚本重载时用） */
    public static void clearUserEnums() { userEnums.clear(); }
    /** 快照当前用户枚举表（用于块级作用域） */
    public static Map<String, Set<String>> snapshotUserEnums() {
        return new java.util.LinkedHashMap<>(userEnums);
    }
    
    /** 恢复用户枚举表到快照状态 */
    public static void restoreUserEnums(Map<String, Set<String>> snapshot) {
        userEnums.clear();
        if (snapshot != null) userEnums.putAll(snapshot);
    }
    /** 通用类解析：全路径直接加载；简单名扫默认包并缓存 */
    public static Class<?> resolveClass(String classPath) {
        if (classPath == null || classPath.isEmpty()) return null;
        
        // 1) 全路径
        if (classPath.indexOf('.') >= 0) {
            Class<?> cached = classCache.get(classPath);
            if (cached != null) return cached;
            try {
                Class<?> c = Class.forName(classPath);
                classCache.put(classPath, c);
                return c;
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }
        
        // 2) 简单名，先查缓存
        String fqcn = simpleToFqcn.get(classPath);
        if (fqcn != null) {
            Class<?> cached = classCache.get(fqcn);
            if (cached != null) return cached;
        }
        
        // 3) 扫描默认包
        for (String pkg : DEFAULT_PACKAGES) {
            String full = pkg + "." + classPath;
            try {
                Class<?> c = Class.forName(full);
                simpleToFqcn.put(classPath, full);
                classCache.put(full, c);
                return c;
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }
    
    /** 枚举类解析（内部走 resolveClass 后做 isEnum 过滤） */
    public static Class<?> resolveEnumClass(String classPath) {
        Class<?> c = resolveClass(classPath);
        return (c != null && c.isEnum()) ? c : null;
    }
}