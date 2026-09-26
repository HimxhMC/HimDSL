package com.himdsl.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * DSL 层的 Entity 引用。底层持有真实的 Bukkit Entity。
 * 字段访问实时读取；方法调用走反射 + 主线程同步。
 */
public class EntityRef {
    private final Entity entity;

    public EntityRef(Entity entity) { this.entity = entity; }

    public Entity getEntity() { return entity; }

    public boolean isValid() {
        return entity != null && !entity.isDead() && entity.isValid();
    }

    // ---------- 实时字段 ----------
    public boolean hasField(String name) {
        switch (name) {
            case "type": case "name": case "uuid": case "world":
            case "x": case "y": case "z": case "yaw": case "pitch":
            case "health": case "hunger": case "id": case "customname":
                return true;
            default: return false;
        }
    }

    public Object getField(String name) {
        if (entity == null) return null;
        Location loc = entity.getLocation();
        switch (name) {
            case "type":     return entity.getType().name().toLowerCase();
            case "name":     return getName();
            case "uuid":     return entity.getUniqueId().toString();
            case "world":    return entity.getWorld().getName();
            case "x":        return loc.getX();
            case "y":        return loc.getY();
            case "z":        return loc.getZ();
            case "yaw":      return (double) loc.getYaw();
            case "pitch":    return (double) loc.getPitch();
            case "health":
                return entity instanceof LivingEntity
                        ? ((LivingEntity) entity).getHealth() : 0.0;
            case "hunger":
                return entity instanceof Player
                        ? ((Player) entity).getFoodLevel() : 0;
            case "id":       return entity.getEntityId();
            case "customname":
                return entity.getCustomName() != null ? entity.getCustomName() : "";
            default: return null;
        }
    }

    public String getName() {
        if (entity == null) return "";
        if (entity instanceof Player) return ((Player) entity).getName();
        return entity.getCustomName() != null ? entity.getCustomName() : "";
    }

    // ---------- 反射调用 ----------
    public Object invoke(String methodName, List<Object> args) {
        if (entity == null) throw new RuntimeException("Entity 引用为空");
        if (!isValid()) throw new RuntimeException("Entity 已失效，无法调用 " + methodName);

        return runSync(() -> {
            Method matched = findMethod(entity.getClass(), methodName, args);
            if (matched == null) {
                throw new RuntimeException("实体类型 "
                        + entity.getClass().getSimpleName()
                        + " 找不到方法 " + methodName + "（参数 " + args.size() + " 个）");
            }
            Object[] converted = convertArgs(args, matched.getParameterTypes());
            try {
                matched.setAccessible(true);
                return matched.invoke(entity, converted);
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                throw new RuntimeException("调用 " + methodName + " 失败: " + c.getMessage(), c);
            }
        });
    }

private Method findMethod(Class<?> clazz, String name, List<Object> args) {
    List<Method> candidates = new ArrayList<>();
    for (Method m : clazz.getMethods()) {
        if (!m.getName().equals(name)) continue;
        if (canConvert(args, m.getParameterTypes())) candidates.add(m);
    }
    if (candidates.isEmpty()) return null;
    if (candidates.size() == 1) return candidates.get(0);

    // 枚举参数更多的重载优先：避免 String 版本遮蔽 Sound / GameMode / WeatherType 等枚举版本
    candidates.sort((a, b) -> {
        int ea = countEnumParams(a.getParameterTypes());
        int eb = countEnumParams(b.getParameterTypes());
        if (ea != eb) return Integer.compare(eb, ea);
        // 枚举数量相同时，原始类型更多的优先（避免 Object 版本遮蔽具体版本）
        int pa = countPrimitiveParams(a.getParameterTypes());
        int pb = countPrimitiveParams(b.getParameterTypes());
        return Integer.compare(pb, pa);
    });
    return candidates.get(0);
}

private int countEnumParams(Class<?>[] types) {
    int n = 0;
    for (Class<?> t : types) if (t.isEnum()) n++;
    return n;
}

private int countPrimitiveParams(Class<?>[] types) {
    int n = 0;
    for (Class<?> t : types) if (t.isPrimitive()) n++;
    return n;
}

/** 判断能否把 args 转换为目标类型（含 Location 拆参、EntityRef 解包、String→Enum） */
private boolean canConvert(List<Object> args, Class<?>[] types) {
    int idx = 0;
    for (Class<?> t : types) {
        // -------- Location 拆参 --------
        if (t == Location.class) {
            Object arg = idx < args.size() ? args.get(idx) : null;
            if (isLocationLike(arg)) { idx++; continue; }
            if (idx + 5 < args.size()
                    && args.get(idx) instanceof String
                    && args.get(idx + 1) instanceof Number
                    && args.get(idx + 2) instanceof Number
                    && args.get(idx + 3) instanceof Number
                    && args.get(idx + 4) instanceof Number
                    && args.get(idx + 5) instanceof Number) {
                idx += 6;
                continue;
            }
            return false;
        }

        // -------- Entity / EntityRef 解包 --------
        if (Entity.class.isAssignableFrom(t)) {
            if (idx >= args.size()) return false;
            Object arg = args.get(idx);
            if (arg instanceof EntityRef || arg instanceof Entity) { idx++; continue; }
            return false;
        }

        if (idx >= args.size()) return false;
        Object arg = args.get(idx);

        // -------- null --------
        if (arg == null) {
            if (t.isPrimitive()) return false;
            idx++;
            continue;
        }

        // -------- 引用类型直接匹配 --------
        if (t.isAssignableFrom(arg.getClass())) { idx++; continue; }

        // -------- 基本类型：放宽数值互转 --------
        if (t.isPrimitive()) {
            if (isNumericPrimitive(t) && arg instanceof Number) { idx++; continue; }
            if (t == boolean.class && arg instanceof Boolean) { idx++; continue; }
            if (t == char.class
                    && (arg instanceof Character || arg instanceof String)) {
                idx++; continue;
            }
            return false;
        }

        // -------- String → Enum --------
        if (t.isEnum() && arg instanceof String) {
            if (toEnum(t, arg.toString()) != null) { idx++; continue; }
            return false;
        }

        // -------- 任意对象 → String --------
        if (t == String.class) { idx++; continue; }

        return false;
    }
    return idx == args.size();
}

private boolean isNumericPrimitive(Class<?> t) {
    return t == int.class || t == long.class || t == double.class
        || t == float.class || t == short.class || t == byte.class;
}

    private Object[] convertArgs(List<Object> args, Class<?>[] types) {
        List<Object> out = new ArrayList<>();
        int idx = 0;
        for (Class<?> t : types) {
            if (t == Location.class) {
                Object arg = args.get(idx);
                if (isLocationLike(arg)) {
                    out.add(buildLocation(arg));
                    idx++;
                } else {
                    String world = (String) args.get(idx);
                    double x = toDouble(args.get(idx+1));
                    double y = toDouble(args.get(idx+2));
                    double z = toDouble(args.get(idx+3));
                    double yaw = toDouble(args.get(idx+4));
                    double pitch = toDouble(args.get(idx+5));
                    out.add(new Location(resolveWorld(world), x, y, z,
                            (float) yaw, (float) pitch));
                    idx += 6;
                }
                continue;
            }
            if (Entity.class.isAssignableFrom(t)) {
                Object arg = args.get(idx);
                if (arg instanceof EntityRef) out.add(((EntityRef) arg).getEntity());
                else out.add(arg);
                idx++;
                continue;
            }
            out.add(convertScalar(args.get(idx), t));
            idx++;
        }
        return out.toArray();
    }

    private boolean isLocationLike(Object obj) {
        if (obj instanceof Map) {
            Map<?,?> m = (Map<?,?>) obj;
            return m.containsKey("world") && m.containsKey("x")
                    && m.containsKey("y") && m.containsKey("z");
        }
        if (obj instanceof StructInstance) {
            StructInstance s = (StructInstance) obj;
            return s.hasField("world") && s.hasField("x")
                    && s.hasField("y") && s.hasField("z");
        }
        return false;
    }

    private Location buildLocation(Object obj) {
        String world; double x, y, z, yaw = 0, pitch = 0;
        if (obj instanceof Map) {
            Map<?,?> m = (Map<?,?>) obj;
            world = String.valueOf(m.get("world"));
            x = toDouble(m.get("x"));
            y = toDouble(m.get("y"));
            z = toDouble(m.get("z"));
            if (m.containsKey("yaw")) yaw = toDouble(m.get("yaw"));
            if (m.containsKey("pitch")) pitch = toDouble(m.get("pitch"));
        } else {
            StructInstance s = (StructInstance) obj;
            world = String.valueOf(s.get("world"));
            x = toDouble(s.get("x"));
            y = toDouble(s.get("y"));
            z = toDouble(s.get("z"));
            if (s.hasField("yaw")) yaw = toDouble(s.get("yaw"));
            if (s.hasField("pitch")) pitch = toDouble(s.get("pitch"));
        }
        return new Location(resolveWorld(world), x, y, z, (float) yaw, (float) pitch);
    }

    private World resolveWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w == null && entity != null) w = entity.getWorld();
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        return w;
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj == null) return 0;
        try { return Double.parseDouble(obj.toString()); }
        catch (NumberFormatException e) { return 0; }
    }

private Object convertScalar(Object arg, Class<?> t) {
    if (arg == null) return null;
    if (t.isAssignableFrom(arg.getClass())) return arg;
    if (t == int.class)     return ((Number) arg).intValue();
    if (t == long.class)    return ((Number) arg).longValue();
    if (t == double.class)  return ((Number) arg).doubleValue();
    if (t == float.class)   return ((Number) arg).floatValue();
    if (t == short.class)   return ((Number) arg).shortValue();
    if (t == byte.class)    return ((Number) arg).byteValue();
    if (t == boolean.class) return arg;
    if (t == char.class)    return arg.toString().charAt(0);
    if (t == String.class)  return arg.toString();

    // String → Enum
    if (t.isEnum() && arg instanceof String) {
        Object v = toEnum(t, arg.toString());
        if (v != null) return v;
    }

    return arg;
}

private boolean isPrimitiveMatch(Class<?> primitive, Class<?> wrapper) {
    if (primitive == boolean.class) return wrapper == Boolean.class;
    if (primitive == char.class) return wrapper == Character.class;
    if (isNumericPrimitive(primitive)) {
        return Number.class.isAssignableFrom(wrapper);
    }
    return false;
}

    /** 强制在主线程执行；异步线程会派发并等待 */
    private Object runSync(Callable<Object> task) {
        if (Bukkit.isPrimaryThread()) {
            try { return task.call(); }
            catch (RuntimeException e) { throw e; }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        org.bukkit.plugin.Plugin plugin =
                Bukkit.getPluginManager().getPlugin("HimDSL");
        if (plugin == null) {
            try { return task.call(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        FutureTask<Object> future = new FutureTask<>(task);
        Bukkit.getScheduler().runTask(plugin, future);
        try { return future.get(30, TimeUnit.SECONDS); }
        catch (Exception e) {
            throw new RuntimeException("主线程调度失败/超时: " + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return "[EntityRef " + (entity == null ? "null"
                : entity.getType() + ":" + getName()) + "]";
    }
/**
 * 字符串 → 枚举。
 * 对常见 Bukkit 枚举做别名映射；其余走 Enum.valueOf(name) 的忽略大小写匹配。
 */
private Object toEnum(Class<?> enumType, String name) {
    if (name == null) return null;
    String n = name.trim();

    // -------- 常见枚举别名 --------
    if (enumType == org.bukkit.GameMode.class) {
        switch (n.toLowerCase()) {
            case "survival":   return org.bukkit.GameMode.SURVIVAL;
            case "creative":   return org.bukkit.GameMode.CREATIVE;
            case "adventure":  return org.bukkit.GameMode.ADVENTURE;
            case "spectator":  return org.bukkit.GameMode.SPECTATOR;
        }
    }
    if (enumType == org.bukkit.WeatherType.class) {
        switch (n.toLowerCase()) {
            case "clear":             return org.bukkit.WeatherType.CLEAR;
            case "rain": case "raining":
            case "downfall": case "storm": case "thunder":
                return org.bukkit.WeatherType.DOWNFALL;
        }
    }

    // -------- 通用匹配（忽略大小写） --------
    for (Object c : enumType.getEnumConstants()) {
        if (((Enum<?>) c).name().equalsIgnoreCase(n)) return c;
    }
    return null;
}
}