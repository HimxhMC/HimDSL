package com.himdsl.runtime;

import com.himdsl.parser.HimDSLVisitorImpl;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class HimDSLRuntime {
    private final File dataFolder;
    public final Environment globalEnv = new Environment();
    public final Map<String, FunctionDef> functions = new HashMap<>();
    public final PlaceholderResolver resolver;
    public final BukkitRunnableWrapper taskManager;
    public final EventListener eventListener;
    public final EventManager eventManager;
    private final Map<String, StructDef> structDefs = new HashMap<>();
    public void registerStruct(StructDef def) { structDefs.put(def.name, def); }
    public StructDef getStruct(String name) { return structDefs.get(name); }
    
    public HimDSLRuntime(File dataFolder) {
        this.dataFolder = dataFolder;
        this.resolver = new PlaceholderResolver(this);
        this.taskManager = new BukkitRunnableWrapper(getPlugin());
        BuiltinFunctions.register(this);
        BuiltinFunctions.setRuntime(this);  // 注入自身
        this.eventListener = new EventListener(this);
        this.eventManager = new EventManager(this);
        List<StructDef.StructField> fields = Arrays.asList(
            new StructDef.StructField("string", "type"),
            new StructDef.StructField("string", "name"),
            new StructDef.StructField("string", "uuid"),
            new StructDef.StructField("string", "world"),
            new StructDef.StructField("double", "x"),
            new StructDef.StructField("double", "y"),
            new StructDef.StructField("double", "z"),
            new StructDef.StructField("double", "yaw"),
            new StructDef.StructField("double", "pitch"),
            new StructDef.StructField("double", "health"),
            new StructDef.StructField("int", "hunger")
        );
        registerStruct(new StructDef("Entity", fields));
    }
    
    public Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin("HimDSL");
    }
    
    public Object callFunction(String name, List<Object> args) {
        if (BuiltinFunctions.isBuiltin(name))
            return BuiltinFunctions.call(name, args);
        FunctionDef func = functions.get(name);
        if (func == null) throw new RuntimeException("未定义函数: " + name);
        return null; // 由 Visitor 实际执行
    }
    
    public void setEventListener(EventListener listener) {
        // 用于外部注入
    }
    
    public PlaceholderResolver getResolver() {
        return resolver;
    }
    
    // ===== 新增 start/stop 方法 =====
    public void startFunction(String name) {
        FunctionDef func = functions.get(name);
        if (func == null) throw new RuntimeException("未定义函数: " + name);
        if (func.annotation == null) throw new RuntimeException("函数 " + name + " 没有注解，无法启动");
        
        if ("BukkitRunnable".equals(func.annotation)) {
            if (func.stopHandle != null) {
                throw new RuntimeException("函数 " + name + " 已经启动");
            }
            if (func.schedule == null) {
                throw new RuntimeException("BukkitRunnable 函数 " + name + " 缺少 schedule 参数");
            }
            Runnable runnable = () -> {
                Environment oldEnv = new Environment(globalEnv);
                HimDSLVisitorImpl visitor = new HimDSLVisitorImpl(this);
                visitor.setEnvironment(oldEnv);
                try {
                    visitor.visit(func.body);
                } catch (Exception e) {
                    e.printStackTrace();
                    Bukkit.getLogger().warning("[HimDSL] 定时任务 " + name + " 执行异常: " + e.getMessage());
                }
            };
            Object stopHandle = taskManager.schedule(name, runnable, func.schedule, true);
            func.stopHandle = stopHandle;
            Bukkit.getLogger().info("[HimDSL] 已启动定时任务: " + name);
        } else if ("EventHandler".equals(func.annotation)) {
            if (eventManager.isEnabled(name)) {
                throw new RuntimeException("函数 " + name + " 已经启动");
            }
            eventManager.enableFunction(name);
        } else {
            throw new RuntimeException("不支持的注解: " + func.annotation);
        }
    }
    
    public void stopFunction(String name) {
        FunctionDef func = functions.get(name);
        if (func == null) throw new RuntimeException("未定义函数: " + name);
        if (func.annotation == null) throw new RuntimeException("函数 " + name + " 没有注解，无法停止");
        
        if ("BukkitRunnable".equals(func.annotation)) {
            if (func.stopHandle == null) {
                throw new RuntimeException("函数 " + name + " 未启动");
            }
            ((Runnable) func.stopHandle).run();
            func.stopHandle = null;
            Bukkit.getLogger().info("[HimDSL] 已停止定时任务: " + name);
        } else if ("EventHandler".equals(func.annotation)) {
            if (!eventManager.isEnabled(name)) {
                throw new RuntimeException("函数 " + name + " 未启动");
            }
            eventManager.disableFunction(name);
        } else {
            throw new RuntimeException("不支持的注解: " + func.annotation);
        }
    }
public void clearStructs() {
    structDefs.clear();
}
// 在 HimDSLRuntime.java 中添加
public void clearEventCache() {
    eventManager.clearCache();
}
/** 清空全部枚举缓存（包含 Bukkit 枚举类缓存） */
public void clearEnumCache() {
    EnumRegistry.clearCache();
}

/** 只清空用户自定义枚举（脚本重载时推荐） */
public void clearUserEnums() {
    EnumRegistry.clearUserEnums();
}
}