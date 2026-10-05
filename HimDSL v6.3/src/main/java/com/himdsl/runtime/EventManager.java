package com.himdsl.runtime;

import com.himdsl.parser.HimDSLVisitorImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.server.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class EventManager {
    private final HimDSLRuntime runtime;
    private final Plugin plugin;
    private final Map<String, Listener> functionListeners = new HashMap<>();
    private final Set<String> enabledFunctions = new HashSet<>();
    private final Map<String, Class<? extends Event>> eventClassCache = new HashMap<>();

    public EventManager(HimDSLRuntime runtime) {
        this.runtime = runtime;
        this.plugin = runtime.getPlugin();
        if (plugin == null) {
            Bukkit.getLogger().warning("[HimDSL] 无法获取插件实例，事件注册可能失败");
        }
    }

    public void enableFunction(String funcName) {
        FunctionDef func = runtime.resolveUniqueFunction(funcName);
        if (func == null) throw new RuntimeException("函数未定义: " + funcName);
        if (!"EventHandler".equals(func.annotation)) {
            throw new RuntimeException("函数 " + funcName + " 不是事件处理函数");
        }
        if (enabledFunctions.contains(funcName)) {
            throw new RuntimeException("函数 " + funcName + " 已启用");
        }

        Class<? extends Event> eventClass = resolveEventClass(func.eventType);
        if (eventClass == null) {
            throw new RuntimeException("未知事件类型: " + func.eventType);
        }

        Listener listener = new Listener() {};
        EventExecutor executor = (listenerObj, event) -> {
            if (!eventClass.isInstance(event)) return;
            Environment env = new Environment(runtime.globalEnv);
            env.set("event", new EventWrapper(event));
            Player trigger = getTriggerPlayer(event);
            if (trigger != null) {
                env.set("_trigger_player", trigger);
            }
            HimDSLVisitorImpl visitor = new HimDSLVisitorImpl(runtime);
            visitor.setEnvironment(env);
            try {
                visitor.visit(func.body);
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getLogger().warning("[HimDSL] 执行事件函数 " + func.name + " 异常: " + e.getMessage());
            }
        };

        Plugin plugin = this.plugin;
        if (plugin == null) {
            plugin = Bukkit.getPluginManager().getPlugin("HimDSL");
        }
        if (plugin == null) {
            throw new RuntimeException("无法找到 HimDSL 插件实例");
        }

        Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.NORMAL, executor, plugin);
        functionListeners.put(funcName, listener);
        enabledFunctions.add(funcName);
        Bukkit.getLogger().info("[HimDSL] 已启用事件监听: " + funcName + " -> " + func.eventType);
    }

    public void disableFunction(String funcName) {
        Listener listener = functionListeners.remove(funcName);
        if (listener == null) {
            throw new RuntimeException("函数 " + funcName + " 未启用");
        }
        HandlerList.unregisterAll(listener);
        enabledFunctions.remove(funcName);
        Bukkit.getLogger().info("[HimDSL] 已禁用事件监听: " + funcName);
    }

    public boolean isEnabled(String funcName) {
        return enabledFunctions.contains(funcName);
    }

    private Class<? extends Event> resolveEventClass(String typeName) {
        // 1. 检查缓存
        if (eventClassCache.containsKey(typeName)) {
            return eventClassCache.get(typeName);
        }

        Class<? extends Event> resolvedClass = null;

        // 2. 如果 typeName 包含 '.', 视为全路径，尝试直接加载
        if (typeName.contains(".")) {
            try {
                // 使用插件自身的类加载器，确保能加载第三方插件的事件类
                ClassLoader loader = plugin != null ? plugin.getClass().getClassLoader() : getClass().getClassLoader();
                Class<?> clazz = Class.forName(typeName, true, loader);
                if (Event.class.isAssignableFrom(clazz)) {
                    resolvedClass = (Class<? extends Event>) clazz;
                    Bukkit.getLogger().info("[HimDSL] 成功加载全路径事件类: " + typeName);
                } else {
                    Bukkit.getLogger().warning("[HimDSL] 类 " + typeName + " 不是 Event 的子类");
                }
            } catch (ClassNotFoundException e) {
                Bukkit.getLogger().warning("[HimDSL] 无法加载全路径事件类: " + typeName + "，尝试回退到默认包");
                // 全路径加载失败，继续尝试默认包
            } catch (Exception e) {
                Bukkit.getLogger().warning("[HimDSL] 加载全路径事件类 " + typeName + " 时发生异常: " + e.getMessage());
            }
        }

        // 3. 如果全路径加载失败（或 typeName 不包含 '.'），尝试默认包前缀
        if (resolvedClass == null) {
            // 定义常见的包前缀，按优先级排列（越靠前越先尝试）
            String[] packages = {
                "org.bukkit.event.player.",
                "org.bukkit.event.block.",
                "org.bukkit.event.entity.",
                "org.bukkit.event.inventory.",
                "org.bukkit.event.server.",
                "org.bukkit.event.world.",
                "org.bukkit.event."   // 通用包，最后尝试
            };

            for (String pkg : packages) {
                String fullName = pkg + typeName;
                try {
                    // 先检查缓存（虽然我们上面查过，但可能 typeName 无点但之前加载过）
                    if (eventClassCache.containsKey(fullName)) {
                        resolvedClass = eventClassCache.get(fullName);
                        break;
                    }

                    Class<?> clazz = Class.forName(fullName);
                    if (Event.class.isAssignableFrom(clazz)) {
                        resolvedClass = (Class<? extends Event>) clazz;
                        Bukkit.getLogger().info("[HimDSL] 成功加载事件类: " + fullName);
                        // 将全名作为缓存 key，方便后续快速命中
                        eventClassCache.put(fullName, resolvedClass);
                        break;
                    }
                } catch (ClassNotFoundException ignored) {
                    // 继续尝试下一个包
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[HimDSL] 加载 " + fullName + " 异常: " + e.getMessage());
                }
            }
        }

        // 4. 将结果存入缓存（以原始 typeName 为 key）
        if (resolvedClass != null) {
            eventClassCache.put(typeName, resolvedClass);
        } else {
            Bukkit.getLogger().warning("[HimDSL] 无法解析事件类型: " + typeName);
        }

        return resolvedClass;
    }

    private Player getTriggerPlayer(Event event) {
        if (event instanceof PlayerEvent) return ((PlayerEvent) event).getPlayer();
        if (event instanceof BlockBreakEvent) return ((BlockBreakEvent) event).getPlayer();
        if (event instanceof BlockPlaceEvent) return ((BlockPlaceEvent) event).getPlayer();
        if (event instanceof PlayerInteractEvent) return ((PlayerInteractEvent) event).getPlayer();
        if (event instanceof PlayerInteractEntityEvent) return ((PlayerInteractEntityEvent) event).getPlayer();
        if (event instanceof SignChangeEvent) return ((SignChangeEvent) event).getPlayer();
        if (event instanceof PlayerCommandPreprocessEvent) return ((PlayerCommandPreprocessEvent) event).getPlayer();
        if (event instanceof InventoryClickEvent) return (Player) ((InventoryClickEvent) event).getWhoClicked();
        if (event instanceof PlayerDropItemEvent) return ((PlayerDropItemEvent) event).getPlayer();
        if (event instanceof PlayerPickupItemEvent) return ((PlayerPickupItemEvent) event).getPlayer();
        if (event instanceof PlayerMoveEvent) return ((PlayerMoveEvent) event).getPlayer();
        if (event instanceof PlayerRespawnEvent) return ((PlayerRespawnEvent) event).getPlayer();
        return null;
    }
    // 在 EventManager.java 中添加
public void clearCache() {
    eventClassCache.clear();
}
}