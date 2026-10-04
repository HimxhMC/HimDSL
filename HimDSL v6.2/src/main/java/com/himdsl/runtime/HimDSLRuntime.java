package com.himdsl.runtime;

import com.himdsl.parser.HimDSLVisitorImpl;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HimDSLRuntime {
    private final File dataFolder;
    private boolean guiListenerRegistered = false;
    public final Environment globalEnv = new Environment();
    public final Map<String, List<FunctionDef>> functions = new HashMap<>();
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
        FunctionDef func = resolveFunction(name, args);
        if (func == null) throw new RuntimeException("未定义函数: " + name);
        return null; // 由 Visitor 实际执行
    }
    
    public void setEventListener(EventListener listener) {
        // 用于外部注入
    }
    
    public PlaceholderResolver getResolver() {
        return resolver;
    }
    
    /** 注册函数重载：同名同参数个数 → 报错 */
    public void registerFunction(FunctionDef func) {
        List<FunctionDef> list = functions.computeIfAbsent(func.name, k -> new ArrayList<>());
        String sig = signatureOf(func);
        for (FunctionDef existing : list) {
            if (signatureOf(existing).equals(sig)) {
                throw new RuntimeException("函数 " + func.name + " 已有签名 " + sig + " 的定义");
            }
        }
        list.add(func);
    }
    
    /** 参数类型签名：未声明类型记为 *，(int,*) 与 (int,string) 视为不同 */
    private String signatureOf(FunctionDef f) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < f.parameterTypes.size(); i++) {
            if (i > 0) sb.append(',');
            String t = f.parameterTypes.get(i);
            sb.append(t == null ? "*" : t);
        }
        sb.append(')');
        return sb.toString();
    }
    
    /** 按参数个数 + 类型粗匹配选重载 */
    public FunctionDef resolveFunction(String name, List<Object> args) {
        List<FunctionDef> list = functions.get(name);
        if (list == null || list.isEmpty()) return null;
        
        // 1) 按参数个数过滤
        List<FunctionDef> byCount = new ArrayList<>();
        for (FunctionDef f : list) {
            if (f.parameters.size() == args.size()) byCount.add(f);
        }
        if (byCount.isEmpty()) return null;
        if (byCount.size() == 1) return byCount.get(0);
        
        // 2) 按类型粗匹配过滤
        List<FunctionDef> matched = new ArrayList<>();
        for (FunctionDef f : byCount) {
            if (matchesTypes(f.parameterTypes, args)) matched.add(f);
        }
        if (matched.isEmpty()) return byCount.get(0);
        if (matched.size() == 1) return matched.get(0);
        
        // 3) 具体类型优先（非 null 类型多者优先）
        matched.sort((a, b) -> Integer.compare(specificity(b), specificity(a)));
        if (specificity(matched.get(0)) == specificity(matched.get(1))) {
            throw new RuntimeException("函数 " + name + " 在 " + args.size()
            + " 个参数上存在重载歧义");
        }
        return matched.get(0);
    }
    
    private int specificity(FunctionDef f) {
        int n = 0;
        for (String t : f.parameterTypes) if (t != null) n++;
        return n;
    }
    
    /** 要求函数唯一（用于 start/stop/事件函数） */
    public FunctionDef resolveUniqueFunction(String name) {
        List<FunctionDef> list = functions.get(name);
        if (list == null || list.isEmpty()) throw new RuntimeException("未定义函数: " + name);
        if (list.size() > 1) {
            throw new RuntimeException("函数 " + name + " 存在重载，此操作要求唯一实现");
        }
        return list.get(0);
    }
    
    private boolean matchesTypes(List<String> types, List<Object> args) {
        if (types == null) return false;
        for (int i = 0; i < types.size() && i < args.size(); i++) {
            String t = types.get(i);
            Object v = args.get(i);
            if (t == null || v == null) continue;
            if (!typeMatches(t, v)) return false;
        }
        return true;
    }
    
    private boolean typeMatches(String type, Object v) {
        switch (type) {
            case "int":
            case "double":
            return v instanceof Number;
            case "bool":
            return v instanceof Boolean;
            case "string":
            return v instanceof String;
            default:
            return true;
        }
    }
    // ===== 新增 start/stop 方法 =====
    public void startFunction(String name) {
        FunctionDef func = resolveUniqueFunction(name);
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
        FunctionDef func = resolveUniqueFunction(name);
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
    public void ensureGuiListener() {
    if (guiListenerRegistered) return;
    guiListenerRegistered = true;
    Plugin plugin = getPlugin();
    if (plugin == null) {
        Bukkit.getLogger().warning("[HimDSL] 无法注册 GUI 监听器：插件实例为空");
        return;
    }
    Bukkit.getPluginManager().registerEvents(new GuiListener(), plugin);
    Bukkit.getLogger().info("[HimDSL] 已注册 GUI 事件监听器");
}
}