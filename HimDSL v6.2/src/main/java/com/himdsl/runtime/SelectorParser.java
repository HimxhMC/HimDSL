package com.himdsl.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class SelectorParser {

    /**
     * 解析选择器。
     * 返回值统一为：
     *   - EntityRef        单个实体
     *   - List<EntityRef>  多个实体
     *   - null             未匹配
     */
    public static Object parse(String selectorText, Environment env, Location origin) {
        selectorText = selectorText.trim();

        if (selectorText.startsWith("@a")) {
            return parseAtA(selectorText, origin);
        } else if (selectorText.startsWith("@r")) {
            return parseAtR(selectorText, origin);
        } else if (selectorText.startsWith("@s")) {
            return parseAtS(env);
        } else if (selectorText.startsWith("@p")) {
            return parseAtP(selectorText, origin);
        } else if (selectorText.startsWith("@e")) {
            return parseAtE(selectorText, origin);
        }
        return null;
    }

    // ---------- @a ----------
    private static Object parseAtA(String selector, Location origin) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Map<String, String> filters = parseFilters(selector);
        players = filterPlayers(players, filters, origin);
        List<EntityRef> entities = players.stream()
                .map(p -> createEntity((Entity) p))
                .collect(Collectors.toList());
        return applyLimit(entities, filters);
    }

    // ---------- @r ----------
    private static Object parseAtR(String selector, Location origin) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Map<String, String> filters = parseFilters(selector);
        players = filterPlayers(players, filters, origin);
        if (players.isEmpty()) return null;
        Player p = players.get(new Random().nextInt(players.size()));
        return createEntity((Entity) p);
    }

    // ---------- @p ----------
    private static Object parseAtP(String selector, Location origin) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Map<String, String> filters = parseFilters(selector);
        players = filterPlayers(players, filters, origin);
        if (players.isEmpty()) return null;
        if (origin != null) {
            players.sort(Comparator.comparingDouble(p -> p.getLocation().distance(origin)));
        }
        return createEntity((Entity) players.get(0));
    }

    // ---------- @s ----------
    private static Object parseAtS(Environment env) {
        Object trigger = env.get("_trigger_player");
        if (trigger == null) return null;
        // 事件系统注入的可能是真实 Player（引用），也可能是已包装的 EntityRef
        if (trigger instanceof EntityRef) return trigger;
        if (trigger instanceof Entity) return createEntity((Entity) trigger);
        return null;
    }

    // ---------- @e ----------
    private static Object parseAtE(String selector, Location origin) {
        List<Entity> entities = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            entities.addAll(w.getEntities());
        }
        Map<String, String> filters = parseFilters(selector);
        entities = filterEntities(entities, filters, origin);
        List<EntityRef> result = entities.stream()
                .map(SelectorParser::createEntity)
                .collect(Collectors.toList());
        return applyLimit(result, filters);
    }

    // ---------- 通用 limit 处理 ----------
    private static Object applyLimit(List<EntityRef> list, Map<String, String> filters) {
        int limit = 0;
        if (filters.containsKey("limit")) {
            limit = Integer.parseInt(filters.get("limit"));
        }
        if (limit == 1) {
            return list.isEmpty() ? null : list.get(0);
        } else {
            if (limit > 1 && list.size() > limit) {
                list = new ArrayList<>(list.subList(0, limit));
            }
            return list;
        }
    }

    // ---------- 解析过滤参数 ----------
    private static Map<String, String> parseFilters(String selector) {
        Map<String, String> filters = new HashMap<>();
        int start = selector.indexOf('[');
        int end = selector.lastIndexOf(']');
        if (start != -1 && end != -1) {
            String inside = selector.substring(start + 1, end);
            for (String pair : inside.split(",")) {
                String[] kv = pair.split("=");
                if (kv.length == 2) filters.put(kv[0].trim(), kv[1].trim());
            }
        }
        return filters;
    }

    // ---------- 玩家过滤（@a / @p / @r） ----------
    private static List<Player> filterPlayers(List<Player> players, Map<String, String> filters, Location origin) {
        Iterator<Player> it = players.iterator();
        while (it.hasNext()) {
            Player p = it.next();
            boolean match = true;
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.equals("limit")) continue;
                switch (key) {
                    case "x":
                        if (Math.abs(p.getLocation().getX() - Double.parseDouble(value)) > 0.01) match = false;
                        break;
                    case "y":
                        if (Math.abs(p.getLocation().getY() - Double.parseDouble(value)) > 0.01) match = false;
                        break;
                    case "z":
                        if (Math.abs(p.getLocation().getZ() - Double.parseDouble(value)) > 0.01) match = false;
                        break;
                    case "distance": {
                        if (origin == null) { match = false; break; }
                        double dist = p.getLocation().distance(origin);
                        if (!matchRange(dist, value)) match = false;
                        break;
                    }
                    case "level":
                        if (!matchRange(p.getLevel(), value)) match = false;
                        break;
                    case "name":
                        if (!p.getName().equalsIgnoreCase(value)) match = false;
                        break;
                    case "gamemode":
                        if (!p.getGameMode().name().equalsIgnoreCase(value)) match = false;
                        break;
                    case "world":
                        if (!p.getWorld().getName().equalsIgnoreCase(value)) match = false;
                        break;
                    default:
                        break;
                }
                if (!match) break;
            }
            if (!match) it.remove();
        }
        return players;
    }

    // ---------- 实体过滤（@e） ----------
    private static List<Entity> filterEntities(List<Entity> entities, Map<String, String> filters, Location origin) {
        Iterator<Entity> it = entities.iterator();
        while (it.hasNext()) {
            Entity e = it.next();
            boolean match = true;
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.equals("limit")) continue;
                switch (key) {
                    case "type":
                        if (!e.getType().name().equalsIgnoreCase(value)) match = false;
                        break;
                    case "name": {
                        if (e instanceof Player) {
                            if (!((Player) e).getName().equalsIgnoreCase(value)) match = false;
                        } else {
                            match = false;
                        }
                        break;
                    }
                    case "world":
                        if (!e.getWorld().getName().equalsIgnoreCase(value)) match = false;
                        break;
                    case "x": {
                        double dx = Double.parseDouble(value);
                        if (Math.abs(e.getLocation().getX() - dx) > 0.01) match = false;
                        break;
                    }
                    case "y": {
                        double dy = Double.parseDouble(value);
                        if (Math.abs(e.getLocation().getY() - dy) > 0.01) match = false;
                        break;
                    }
                    case "z": {
                        double dz = Double.parseDouble(value);
                        if (Math.abs(e.getLocation().getZ() - dz) > 0.01) match = false;
                        break;
                    }
                    case "distance": {
                        if (origin == null) { match = false; break; }
                        double dist = e.getLocation().distance(origin);
                        if (!matchRange(dist, value)) match = false;
                        break;
                    }
                    default:
                        break;
                }
                if (!match) break;
            }
            if (!match) it.remove();
        }
        return entities;
    }

    // ---------- 范围匹配 ----------
    private static boolean matchRange(double value, String rangeStr) {
        if (rangeStr.contains("..")) {
            String[] parts = rangeStr.split("\\\\.\\\\.");
            if (parts.length == 2) {
                if (parts[0].isEmpty()) {
                    double max = Double.parseDouble(parts[1]);
                    return value <= max;
                } else if (parts[1].isEmpty()) {
                    double min = Double.parseDouble(parts[0]);
                    return value >= min;
                } else {
                    double min = Double.parseDouble(parts[0]);
                    double max = Double.parseDouble(parts[1]);
                    return value >= min && value <= max;
                }
            }
        } else {
            return Math.abs(value - Double.parseDouble(rangeStr)) < 0.001;
        }
        return false;
    }

    // ---------- Entity -> EntityRef（唯一出口） ----------
    public static EntityRef createEntity(Entity entity) {
        if (entity == null) return null;
        return new EntityRef(entity);
    }
}