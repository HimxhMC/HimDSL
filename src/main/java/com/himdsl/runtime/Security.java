package com.himdsl.runtime;

import org.bukkit.Bukkit;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Security {

    private static final ThreadLocal<SecurityContext> CTX = new ThreadLocal<>();
    private static volatile SecurityConfig config;
    private static final Set<String> warnedKeys = ConcurrentHashMap.newKeySet();

    private Security() {}

    public static void init(SecurityConfig c) { config = c; warnedKeys.clear(); }
    public static SecurityConfig getConfig() { return config; }

    public static void set(SecurityContext ctx) {
        if (ctx == null) CTX.remove(); else CTX.set(ctx);
    }
    public static SecurityContext current() { return CTX.get(); }
    public static void clear() { CTX.remove(); }
    public static void clearWarnings() { warnedKeys.clear(); }

    /** 能力检查 */
    public static void check(Capability cap) {
        SecurityContext ctx = CTX.get();
        SecurityConfig cfg = config;
        if (ctx == null || cfg == null) return;
        if (cfg.isAllowed(ctx.level, cap)) return;

        if (cfg.isAuditOnly()) {
            warnOnce("audit", ctx, "能力 " + cap, "检测到使用（审计放行）");
            return;
        }
        warnOnce("denied", ctx, "能力 " + cap, "被拒绝");
        throw new PermissionDeniedException(cap,
            "脚本 " + ctx.scriptId + "（" + ctx.level.name().toLowerCase()
            + "）无权使用能力 " + cap);
    }

    /** 类黑名单检查；trusted 绕过 */
    public static void checkClass(String className) {
        SecurityContext ctx = CTX.get();
        SecurityConfig cfg = config;
        if (ctx == null || cfg == null) return;
        if (ctx.level == SecurityLevel.TRUSTED) return;
        if (!cfg.isClassBlacklisted(className)) return;

        if (cfg.isAuditOnly()) {
            warnOnce("audit", ctx, "类 " + className, "黑名单命中（审计放行）");
            return;
        }
        warnOnce("denied", ctx, "类 " + className, "黑名单命中");
        throw new PermissionDeniedException(Capability.CLASS_LOAD,
            "脚本 " + ctx.scriptId + " 不允许访问类 " + className);
    }

    /** 方法名黑名单检查；trusted 绕过 */
    public static void checkMethod(String methodName) {
        SecurityContext ctx = CTX.get();
        SecurityConfig cfg = config;
        if (ctx == null || cfg == null) return;
        if (ctx.level == SecurityLevel.TRUSTED) return;
        if (!cfg.isMethodBlacklisted(methodName)) return;

        if (cfg.isAuditOnly()) {
            warnOnce("audit", ctx, "方法 " + methodName, "黑名单命中（审计放行）");
            return;
        }
        warnOnce("denied", ctx, "方法 " + methodName, "黑名单命中");
        throw new PermissionDeniedException(Capability.CLASS_LOAD,
            "脚本 " + ctx.scriptId + " 不允许调用方法 " + methodName);
    }

    private static void warnOnce(String kind, SecurityContext ctx, String item, String msg) {
        String key = kind + ":" + ctx.scriptId + ":" + item;
        if (!warnedKeys.add(key)) return;
        Bukkit.getLogger().warning("[HimDSL] [安全] " + msg + " → " + ctx.scriptId + " / " + item);
    }
}