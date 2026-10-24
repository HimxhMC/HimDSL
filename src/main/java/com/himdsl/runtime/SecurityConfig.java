package com.himdsl.runtime;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.*;

public class SecurityConfig {
    
    private static final long DEFAULT_SLEEP_MAX_MS = 15 * 60 * 1000L;
    
    public static class PathRule {
        public final String glob;
        public final SecurityLevel level;
        
        public PathRule(String glob, SecurityLevel level) {
            this.glob = glob == null ? "" : glob.replace('\\', '/');
            this.level = level;
        }
        
        /** 跨平台 glob 匹配，支持 ** / * / 字面量 */
        public boolean matches(String relativePath) {
            if (relativePath == null || glob.isEmpty()) return false;
            String path = relativePath.replace('\\', '/');
            
            // 特殊情况：** 单独出现 → 匹配一切
            if (glob.equals("**")) return true;
            
            // 后缀 /** ：匹配目录本身或其后代
            if (glob.endsWith("/**")) {
                String prefix = glob.substring(0, glob.length() - 3);
                return path.equals(prefix) || path.startsWith(prefix + "/");
            }
            
            // 前缀 **/ ：匹配任意前导目录
            if (glob.startsWith("**/")) {
                String suffix = glob.substring(3);
                if (path.equals(suffix)) return true;
                // 从每个 "/" 处切分，检查后缀是否匹配
                int idx = -1;
                while ((idx = path.indexOf('/', idx + 1)) >= 0) {
                    if (path.substring(idx + 1).equals(suffix)) return true;
                }
                return false;
            }
            
            // 通用情况：把 glob 编译为正则
            return toRegex(glob).matcher(path).matches();
        }
        
        private static java.util.regex.Pattern toRegex(String glob) {
            StringBuilder sb = new StringBuilder("^");
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*') {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        sb.append(".*");   // ** 匹配跨目录
                        i++;
                    } else {
                        sb.append("[^/]*"); // * 不跨目录
                    }
                } else if (".+()^$|{}[]\\?".indexOf(c) >= 0) {
                    sb.append('\\').append(c);
                } else {
                    sb.append(c);
                }
            }
            sb.append('$');
            return java.util.regex.Pattern.compile(sb.toString());
        }
    }
    
    private SecurityLevel defaultLevel;
    private boolean auditOnly;
    private long sleepMaxMs;
    private final List<PathRule> pathRules = new ArrayList<>();
    
    private final Set<String>  blacklistClasses   = new HashSet<>();
    private final List<String> blacklistPrefixes  = new ArrayList<>();
    private final Set<String>  blacklistMethods   = new HashSet<>();
    
    private final Map<SecurityLevel, Set<Capability>> levelCaps = new EnumMap<>(SecurityLevel.class);
    
    public SecurityConfig() { applyDefaults(); }
    
    private void applyDefaults() {
        defaultLevel = SecurityLevel.NORMAL;
        auditOnly = false;
        sleepMaxMs = DEFAULT_SLEEP_MAX_MS;
        pathRules.clear();
        
        // ---------- 类黑名单（精确） ----------
        blacklistClasses.clear();
        blacklistClasses.addAll(Arrays.asList(
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Process",
            "java.lang.ClassLoader",
            "java.lang.invoke.MethodHandle",
            "java.lang.invoke.MethodHandles",
            "java.lang.invoke.VarHandle",
            "sun.misc.Unsafe",
            "jdk.internal.misc.Unsafe"
        ));
        
        // ---------- 类黑名单（前缀） ----------
        blacklistPrefixes.clear();
        blacklistPrefixes.addAll(Arrays.asList("sun.", "com.sun.", "jdk.internal."));
        
        // ---------- 方法名黑名单 ----------
        blacklistMethods.clear();
        blacklistMethods.addAll(Arrays.asList(
            // 反射逃逸链入口
            "getClass",
            "forName", "loadClass",
            "getMethod", "getDeclaredMethod", "getMethods", "getDeclaredMethods",
            "getField", "getDeclaredField", "getFields", "getDeclaredFields",
            "getConstructor", "getDeclaredConstructor",
            "getConstructors", "getDeclaredConstructors",
            "setAccessible",
            "invoke",
            "newInstance",
            // 进程 / JVM
            "exec", "exit", "halt", "getRuntime", "setSecurityManager"
        ));
        
        // ---------- 能力表 ----------
        levelCaps.clear();
        levelCaps.put(SecurityLevel.TRUSTED, EnumSet.allOf(Capability.class));
        levelCaps.put(SecurityLevel.NORMAL, EnumSet.of(
            Capability.FS_READ, Capability.FS_WRITE,
            Capability.CMD_PLAYER,
            Capability.SCHEDULER, Capability.SLEEP,
            Capability.CLASS_LOAD, Capability.NEW_OBJECT, Capability.STATIC_ACCESS,
            Capability.WORLD_WRITE, Capability.ENTITY_MODIFY, Capability.INVENTORY_MODIFY,
            Capability.GUI, Capability.EVENT_HANDLER,
            Capability.SELECTOR_GLOBAL,
            Capability.PAPI_READ, Capability.PAPI_REGISTER, Capability.BOSS_API
        ));
        levelCaps.put(SecurityLevel.SANDBOX, EnumSet.of(
            Capability.FS_READ, Capability.SLEEP, Capability.PAPI_READ
        ));
    }
    
    // ---------------- 加载 / 保存 ----------------
    public void load(File file) {
        applyDefaults();
        if (file == null) return;
        if (!file.exists()) { save(file); return; }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = yaml.getConfigurationSection("security");
            if (sec == null) { save(file); return; }
            
            defaultLevel = SecurityLevel.parse(sec.getString("default-level", "normal"));
            auditOnly    = sec.getBoolean("audit-only", false);
            sleepMaxMs   = sec.getLong("sleep-max-ms", DEFAULT_SLEEP_MAX_MS);
            
            List<String> bc = sec.getStringList("blacklist-classes");
            if (!bc.isEmpty()) {
                blacklistClasses.clear();
                blacklistClasses.addAll(bc);
            }
            List<String> bp = sec.getStringList("blacklist-class-prefixes");
            if (!bp.isEmpty()) {
                blacklistPrefixes.clear();
                blacklistPrefixes.addAll(bp);
            }
            List<String> bm = sec.getStringList("blacklist-methods");
            if (!bm.isEmpty()) {
                blacklistMethods.clear();
                blacklistMethods.addAll(bm);
            }
            
            pathRules.clear();
            ConfigurationSection per = sec.getConfigurationSection("per-script");
            if (per != null) {
                for (String key : per.getKeys(false)) {
                    String lv = per.getString(key);
                    if (lv == null) continue;
                    pathRules.add(new PathRule(key, SecurityLevel.parse(lv)));
                }
            }
            
            ConfigurationSection levels = sec.getConfigurationSection("levels");
            if (levels != null) {
                for (String name : levels.getKeys(false)) {
                    SecurityLevel lv = SecurityLevel.parse(name);
                    ConfigurationSection lvSec = levels.getConfigurationSection(name);
                    if (lvSec == null) continue;
                    List<String> caps = lvSec.getStringList("capabilities");
                    Set<Capability> set = EnumSet.noneOf(Capability.class);
                    if (caps.contains("*")) {
                        set = EnumSet.allOf(Capability.class);
                    } else {
                        for (String c : caps) {
                            try { set.add(Capability.valueOf(c.trim().toUpperCase())); }
                            catch (IllegalArgumentException ignored) {
                                Bukkit.getLogger().warning("[HimDSL] 未知能力: " + c);
                            }
                        }
                    }
                    levelCaps.put(lv, set);
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[HimDSL] 读取安全配置失败，使用默认值: " + e.getMessage());
            applyDefaults();
        }
    }
    
    public void save(File file) {
        if (file == null) return;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().header(
                "HimDSL 安全配置（黑名单策略）\n" +
                "\n" +
                "策略：默认全部放行，只拒绝明确列出的危险项。\n" +
                "  blacklist-classes         精确类名（全路径）\n" +
                "  blacklist-class-prefixes  类名前缀\n" +
                "  blacklist-methods         任意对象上出现此方法名一律拒绝\n" +
                "  trusted 级别绕过全部黑名单\n" +
                "\n" +
                "default-level: trusted | normal | sandbox\n" +
                "per-script:    glob 匹配脚本相对路径，从上到下第一个命中生效\n" +
                "levels:        每个级别的 capabilities，* 表示全部\n"
            );
            yaml.set("security.default-level", defaultLevel.name().toLowerCase());
            yaml.set("security.audit-only", auditOnly);
            yaml.set("security.sleep-max-ms", sleepMaxMs);
            yaml.set("security.blacklist-classes", new ArrayList<>(blacklistClasses));
            yaml.set("security.blacklist-class-prefixes", new ArrayList<>(blacklistPrefixes));
            yaml.set("security.blacklist-methods", new ArrayList<>(blacklistMethods));
            
            ConfigurationSection perSec = yaml.createSection("security.per-script");
            perSec.set("admin/**",  "trusted");
            perSec.set("public/**", "sandbox");
            
            for (SecurityLevel lv : SecurityLevel.values()) {
                Set<Capability> caps = levelCaps.get(lv);
                List<String> names = new ArrayList<>();
                if (caps != null) {
                    if (caps.size() == Capability.values().length) names.add("*");
                    else { for (Capability c : caps) names.add(c.name()); Collections.sort(names); }
                }
                yaml.set("security.levels." + lv.name().toLowerCase() + ".capabilities", names);
            }
            yaml.save(file);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[HimDSL] 写入安全配置失败: " + e.getMessage());
        }
    }
    
    // ---------------- 查询 ----------------
    public SecurityLevel levelFor(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return defaultLevel;
        String norm = relativePath.replace('\\', '/');
        for (PathRule r : pathRules) if (r.matches(norm)) return r.level;
        return defaultLevel;
    }
    
    public boolean isAllowed(SecurityLevel level, Capability cap) {
        Set<Capability> set = levelCaps.get(level);
        return set != null && set.contains(cap);
    }
    
    /** 类是否被禁 */
    public boolean isClassBlacklisted(String className) {
        if (className == null) return false;
        if (blacklistClasses.contains(className)) return true;
        for (String p : blacklistPrefixes) if (className.startsWith(p)) return true;
        return false;
    }
    
    /** 方法名是否被禁 */
    public boolean isMethodBlacklisted(String methodName) {
        return methodName != null && blacklistMethods.contains(methodName);
    }
    
    public SecurityLevel getDefaultLevel() { return defaultLevel; }
    public boolean isAuditOnly() { return auditOnly; }
    public long getSleepMaxMs() { return sleepMaxMs; }
}