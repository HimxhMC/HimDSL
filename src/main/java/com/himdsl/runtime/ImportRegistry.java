package com.himdsl.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * import 表。
 *
 * 结构：
 *   imports           显式 import  (简单名 → 全名)
 *   wildcardPackages  通配 import  (包名列表，保序)
 *
 * 查找优先级：
 *   显式 import  >  通配 import  >  默认包扫描
 *
 * 与 EnumRegistry 的缓存完全独立，脚本重载只需 clear() 本表。
 */
public final class ImportRegistry {
    private ImportRegistry() {}

    /** 显式 import：simpleName → fqcn */
    private static final Map<String, String> imports = new ConcurrentHashMap<>();

    /** 通配 import：包名列表（保序，按声明顺序查找） */
    private static final List<String> wildcardPackages =
            Collections.synchronizedList(new ArrayList<>());

    /** 通配解析结果缓存：simpleName → Class<?> */
    private static final Map<String, Class<?>> wildcardResolved = new ConcurrentHashMap<>();

    /** 通配解析失败缓存：simpleName（避免反复 Class.forName 失败） */
    private static final java.util.Set<String> wildcardMiss = ConcurrentHashMap.newKeySet();

    // ---------------- 注册 ----------------

    public static void register(String simpleName, String fqcn) {
        if (simpleName == null || simpleName.isEmpty()) return;
        if (fqcn == null || fqcn.isEmpty()) return;
        imports.put(simpleName, fqcn);
    }

    public static void registerWildcard(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        if (!wildcardPackages.contains(packageName)) {
            wildcardPackages.add(packageName);
            // 新增包 → 旧的 miss 结果可能不再成立
            wildcardResolved.clear();
            wildcardMiss.clear();
        }
    }

    // ---------------- 查询 ----------------

    /** 显式 import 查表；未导入返回 null */
    public static String resolve(String simpleName) {
        if (simpleName == null) return null;
        return imports.get(simpleName);
    }

    public static boolean isImported(String simpleName) {
        return simpleName != null && imports.containsKey(simpleName);
    }

    /**
     * 在通配包中查找类。按声明顺序，第一个命中即返回。
     * 命中 / 未命中都会缓存。
     */
    public static Class<?> resolveWildcard(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) return null;
        if (wildcardPackages.isEmpty()) return null;

        Class<?> cached = wildcardResolved.get(simpleName);
        if (cached != null) return cached;
        if (wildcardMiss.contains(simpleName)) return null;

        List<String> snapshot;
        synchronized (wildcardPackages) {
            snapshot = new ArrayList<>(wildcardPackages);
        }
        for (String pkg : snapshot) {
            String fqcn = pkg + "." + simpleName;
            try {
                Class<?> c = Class.forName(fqcn);
                wildcardResolved.put(simpleName, c);
                return c;
            } catch (ClassNotFoundException ignored) {}
        }
        wildcardMiss.add(simpleName);
        return null;
    }

    // ---------------- 清理 / 快照 ----------------

    public static void clear() {
        imports.clear();
        wildcardPackages.clear();
        wildcardResolved.clear();
        wildcardMiss.clear();
    }

    /** 快照（供需要时扩展块级作用域用） */
    public static Map<String, String> snapshot() {
        return new LinkedHashMap<>(imports);
    }

    public static void restore(Map<String, String> snapshot) {
        imports.clear();
        if (snapshot != null) imports.putAll(snapshot);
    }
}