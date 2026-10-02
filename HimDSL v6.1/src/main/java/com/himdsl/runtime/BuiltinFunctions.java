package com.himdsl.runtime;

import com.himdsl.HimDSLPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicInteger;

public class BuiltinFunctions {
    private static final Random random = new Random();
    private static HimDSLRuntime runtimeInstance;
    
    // ---------- 文件 I/O ----------
    private static final Map<Integer, Closeable> openFiles = new HashMap<>();
    private static final AtomicInteger fileIdGenerator = new AtomicInteger(1);
    
    public static void setRuntime(HimDSLRuntime runtime) {
        runtimeInstance = runtime;
    }
    
    public static void register(HimDSLRuntime runtime) {
        // 无需额外注册
    }
    
    /**
    * 判断名称是否为内置函数（包括对象方法和全局函数）
    */
private static final Set<String> BUILTIN_NAMES = new HashSet<>(Arrays.asList(
    "rand", "toInt", "toString", "toDouble", "toLong", "toBool", "toFloat",
    "toUpperCase", "toLowerCase", "trim", "replace", "split", "contains",
    "length", "runcmd", "sleep",
    "stack", "push", "pop", "peek", "isEmpty",
    "queue", "offer", "poll",
    "map", "put", "get", "remove", "containsKey",
    "start", "stop", "startsWith", "endsWith", "randChoose",
    "abs", "fabs", "fmod", "max", "min",
    "pow", "sqrt", "cbrt", "hypot",
    "log", "log10", "log2",
    "ceil", "floor", "round",
    "sin", "cos", "tan",
    "asin", "acos", "atan", "isOp", "vector", "set",
    "bigAdd", "bigSub", "bigMul", "bigDiv", "bigDivExact",
    "fopen", "fread", "fwrite", "fclose", "fexists", "fdelete", "freadlines", "fwritelines",
    "add", "size",
    "exp", "expm1", "log1p", "atan2", "sinh", "cosh", "tanh",
    "toRadians", "toDegrees", "rint", "signum", "copySign",
    "nextAfter", "nextUp", "nextDown", "scalb", "getExponent",
    "IEEEremainder", "addExact", "subtractExact", "multiplyExact",
    "incrementExact", "decrementExact", "negateExact",
    "floorDiv", "floorMod", "toIntExact", "random", "fma", "ulp",
    "papi", "papiSet", "papiRegister", "papiUnregister", "papiHas",
    "toJson", "fromJson"
));

public static boolean isBuiltin(String name) {
    return BUILTIN_NAMES.contains(name);
}
    
    /**
    * 判断一个对象是否可以作为方法调用的目标（即支持对象式调用）
    */
    public static boolean isSupportedTarget(Object obj) {
        return obj instanceof Stack || /*obj instanceof Queue || obj instanceof Map ||
        obj instanceof List || obj instanceof Set ||*/ obj instanceof String;
    }
    
    /**
    * 对象式方法调用分发
    */
    public static Object callMethod(Object target, String name, List<Object> args) {
        if ("toJson".equals(name)) {
            return toJson(target);
        }
        if ("fromJson".equals(name)) {
            if (target instanceof String) {
                return parseJson((String) target);
            }
            throw new RuntimeException("fromJson 只能在字符串上调用");
        }
        if (target instanceof String) {
            return callStringMethod((String) target, name, args);
        } else if (target instanceof Stack) {
            return callStackMethod((Stack<Object>) target, name, args);
        } else if (target instanceof Queue) {
            return callQueueMethod((Queue<Object>) target, name, args);
        } else if (target instanceof Map) {
            return callMapMethod((Map<Object, Object>) target, name, args);
        } else if (target instanceof List) {
            return callListMethod((List<Object>) target, name, args);
        } else if (target instanceof Set) {
            return callSetMethod((Set<Object>) target, name, args);
        } else {
            throw new RuntimeException("不支持的目标类型: " + target.getClass().getSimpleName());
        }
    }
    
    // ---------- 字符串方法 ----------
    private static Object callStringMethod(String str, String name, List<Object> args) {
        switch (name) {
            case "toUpperCase": return str.toUpperCase();
            case "toLowerCase": return str.toLowerCase();
            case "trim": return str.trim();
            case "replace": {
                if (args.size() >= 2) {
                    String old = args.get(0).toString();
                    String newStr = args.get(1).toString();
                    return str.replace(old, newStr);
                }
                throw new RuntimeException("replace 需要2个参数");
            }
            case "split": {
                if (args.size() >= 1) {
                    String regex = args.get(0).toString();
                    return str.split(regex);
                }
                throw new RuntimeException("split 需要1个参数");
            }
            case "contains": {
                if (args.size() >= 1) {
                    return str.contains(args.get(0).toString());
                }
                throw new RuntimeException("contains 需要1个参数");
            }
            case "length": return str.length();
            case "startsWith": {
                if (args.size() >= 1) {
                    return str.startsWith(args.get(0).toString());
                }
                throw new RuntimeException("startsWith 需要1个参数");
            }
            case "endsWith": {
                if (args.size() >= 1) {
                    return str.endsWith(args.get(0).toString());
                }
                throw new RuntimeException("endsWith 需要1个参数");
            }
            default:
            throw new RuntimeException("字符串不支持方法: " + name);
        }
    }
    
    // ---------- 栈方法 ----------
    private static Object callStackMethod(Stack<Object> stack, String name, List<Object> args) {
        switch (name) {
            case "push": {
                if (args.size() >= 1) {
                    stack.push(args.get(0));
                    return stack; // 链式
                }
                throw new RuntimeException("push 需要1个参数");
            }
            case "pop": {
                if (args.isEmpty()) return stack.pop();
                throw new RuntimeException("pop 不需要参数");
            }
            case "peek": {
                if (args.isEmpty()) return stack.peek();
                throw new RuntimeException("peek 不需要参数");
            }
            case "isEmpty": {
                if (args.isEmpty()) return stack.isEmpty();
                throw new RuntimeException("isEmpty 不需要参数");
            }
            default:
            throw new RuntimeException("栈不支持方法: " + name);
        }
    }
    
    // ---------- 队列方法 ----------
    private static Object callQueueMethod(Queue<Object> queue, String name, List<Object> args) {
        switch (name) {
            case "offer": {
                if (args.size() >= 1) {
                    queue.offer(args.get(0));
                    return queue;
                }
                throw new RuntimeException("offer 需要1个参数");
            }
            case "poll": {
                if (args.isEmpty()) return queue.poll();
                throw new RuntimeException("poll 不需要参数");
            }
            case "peek": {
                if (args.isEmpty()) return queue.peek();
                throw new RuntimeException("peek 不需要参数");
            }
            case "isEmpty": {
                if (args.isEmpty()) return queue.isEmpty();
                throw new RuntimeException("isEmpty 不需要参数");
            }
            default:
            throw new RuntimeException("队列不支持方法: " + name);
        }
    }
    
    // ---------- Map 方法 ----------
    private static Object callMapMethod(Map<Object, Object> map, String name, List<Object> args) {
        switch (name) {
            case "put": {
                if (args.size() >= 2) {
                    map.put(args.get(0), args.get(1));
                    return map;
                }
                throw new RuntimeException("put 需要2个参数");
            }
            case "get": {
                if (args.size() >= 1) {
                    return map.get(args.get(0));
                }
                throw new RuntimeException("get 需要1个参数");
            }
            case "remove": {
                if (args.size() >= 1) {
                    return map.remove(args.get(0));
                }
                throw new RuntimeException("remove 需要1个参数");
            }
            case "containsKey": {
                if (args.size() >= 1) {
                    return map.containsKey(args.get(0));
                }
                throw new RuntimeException("containsKey 需要1个参数");
            }
            case "isEmpty": {
                if (args.isEmpty()) return map.isEmpty();
                throw new RuntimeException("isEmpty 不需要参数");
            }
            default:
            throw new RuntimeException("Map不支持方法: " + name);
        }
    }
    
    // ---------- List 方法 ----------
    private static Object callListMethod(List<Object> list, String name, List<Object> args) {
        switch (name) {
            case "add": {
                if (args.size() >= 1) {
                    list.add(args.get(0));
                    return list;
                }
                throw new RuntimeException("add 需要1个参数");
            }
            case "get": {
                if (args.size() >= 1) {
                    int index = ((Number) args.get(0)).intValue();
                    return list.get(index);
                }
                throw new RuntimeException("get 需要1个参数");
            }
            case "remove": {
                if (args.size() >= 1) {
                    // 如果参数是整数，按索引删除；否则按对象删除
                    Object arg = args.get(0);
                    if (arg instanceof Number) {
                        int index = ((Number) arg).intValue();
                        return list.remove(index);
                    } else {
                        return list.remove(arg);
                    }
                }
                throw new RuntimeException("remove 需要1个参数");
            }
            case "contains": {
                if (args.size() >= 1) {
                    return list.contains(args.get(0));
                }
                throw new RuntimeException("contains 需要1个参数");
            }
            case "size": {
                if (args.isEmpty()) return list.size();
                throw new RuntimeException("size 不需要参数");
            }
            case "isEmpty": {
                if (args.isEmpty()) return list.isEmpty();
                throw new RuntimeException("isEmpty 不需要参数");
            }
            default:
            throw new RuntimeException("List不支持方法: " + name);
        }
    }
    
    // ---------- Set 方法 ----------
    private static Object callSetMethod(Set<Object> set, String name, List<Object> args) {
        switch (name) {
            case "add": {
                if (args.size() >= 1) {
                    set.add(args.get(0));
                    return set;
                }
                throw new RuntimeException("add 需要1个参数");
            }
            case "remove": {
                if (args.size() >= 1) {
                    return set.remove(args.get(0));
                }
                throw new RuntimeException("remove 需要1个参数");
            }
            case "contains": {
                if (args.size() >= 1) {
                    return set.contains(args.get(0));
                }
                throw new RuntimeException("contains 需要1个参数");
            }
            case "size": {
                if (args.isEmpty()) return set.size();
                throw new RuntimeException("size 不需要参数");
            }
            case "isEmpty": {
                if (args.isEmpty()) return set.isEmpty();
                throw new RuntimeException("isEmpty 不需要参数");
            }
            default:
            throw new RuntimeException("Set不支持方法: " + name);
        }
    }
    
    // ---------- 文件 I/O 辅助 ----------
    private static File getPluginDataFolder() {
        HimDSLPlugin plugin = HimDSLPlugin.getInstance();
        if (plugin == null) {
            throw new RuntimeException("HimDSL 插件未初始化，无法获取数据目录");
        }
        return plugin.getDataFolder();
    }
    
    private static File resolvePath(String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        } else {
            return new File(getPluginDataFolder(), path);
        }
    }
    
    private static void checkPathSafety(File file) {
        try {
            String canonical = file.getCanonicalPath();
            if (!file.isAbsolute()) {
                String pluginCanonical = getPluginDataFolder().getCanonicalPath();
                if (!canonical.startsWith(pluginCanonical + File.separator) && !canonical.equals(pluginCanonical)) {
                    throw new SecurityException("相对路径不能离开插件目录: " + file.getPath());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("路径解析失败: " + e.getMessage());
        }
    }
    private static boolean isMethodSupported(Object target, String name) {
        if ("toJson".equals(name)) return true;
        if ("fromJson".equals(name)) return target instanceof String;
        if (target instanceof String) {
            return Arrays.asList("toUpperCase", "toLowerCase", "trim", "replace", "split",
            "contains", "length", "startsWith", "endsWith").contains(name);
        } else if (target instanceof Stack) {
            return Arrays.asList("push", "pop", "peek", "isEmpty").contains(name);
        } else if (target instanceof Queue) {
            return Arrays.asList("offer", "poll", "peek", "isEmpty").contains(name);
        } else if (target instanceof Map) {
            return Arrays.asList("put", "get", "remove", "containsKey", "isEmpty").contains(name);
        } else if (target instanceof List) {
            return Arrays.asList("add", "get", "remove", "contains", "size", "isEmpty").contains(name);
        } else if (target instanceof Set) {
            return Arrays.asList("add", "remove", "contains", "size", "isEmpty").contains(name);
        }
        return false;
    }
    // ---------- 主调用入口 ----------
    public static Object call(String name, List<Object> args) {
        // 1. 如果第一个参数是支持的目标类型，转为对象式调用
        if (!args.isEmpty()) {
            Object first = args.get(0);
            if (isSupportedTarget(first) && isMethodSupported(first, name)) {
                List<Object> methodArgs = args.subList(1, args.size());
                return callMethod(first, name, methodArgs);
            }
        }
        
        // 2. 函数式调用（全局函数）
        switch (name) {
            // ---------- 随机 & 类型转换 ----------
            case "rand": {
                if (args.isEmpty()) return random.nextDouble();
                if (args.size() >= 2) {
                    Object aObj = args.get(0);
                    Object bObj = args.get(1);
                    if (aObj instanceof Number && bObj instanceof Number) {
                        double a = ((Number) aObj).doubleValue();
                        double b = ((Number) bObj).doubleValue();
                        if (a > b) { double tmp = a; a = b; b = tmp; }
                        boolean isInt = (aObj instanceof Integer || aObj instanceof Long) &&
                        (bObj instanceof Integer || bObj instanceof Long);
                        if (isInt) {
                            int aInt = (int) a;
                            int bInt = (int) b;
                            return random.nextInt(bInt - aInt + 1) + aInt;
                        } else {
                            return a + (b - a) * random.nextDouble();
                        }
                    }
                }
                throw new RuntimeException("rand 参数错误");
            }
            case "randChoose": {
                if (args.isEmpty()) throw new RuntimeException("randChoose 至少需要一个参数");
                int idx = random.nextInt(args.size());
                return args.get(idx);
            }
            case "toInt": return toInt(args.get(0));
            case "toString": return String.valueOf(args.get(0));
            case "toDouble": return toDouble(args.get(0));
            case "toLong": return toLong(args.get(0));
            case "toBool": return toBool(args.get(0));
            case "toFloat": return toFloat(args.get(0));
            
            // ---------- 字符串全局函数（已由对象式覆盖，但保留函数式兼容） ----------
            // 实际上这些函数式调用不会进入这里，因为第一个参数是字符串时会走对象式
            // 但如果只传一个字符串参数（如 length("abc")），这里也能处理
            case "toUpperCase": {
                if (args.size() >= 1) return args.get(0).toString().toUpperCase();
                throw new RuntimeException("toUpperCase 需要1个参数");
            }
            case "toLowerCase": {
                if (args.size() >= 1) return args.get(0).toString().toLowerCase();
                throw new RuntimeException("toLowerCase 需要1个参数");
            }
            case "trim": {
                if (args.size() >= 1) return args.get(0).toString().trim();
                throw new RuntimeException("trim 需要1个参数");
            }
            case "replace": {
                if (args.size() >= 3) {
                    String str = args.get(0).toString();
                    String old = args.get(1).toString();
                    String newStr = args.get(2).toString();
                    return str.replace(old, newStr);
                }
                throw new RuntimeException("replace 需要3个参数");
            }
            case "split": {
                if (args.size() >= 2) {
                    String str = args.get(0).toString();
                    String regex = args.get(1).toString();
                    return str.split(regex);
                }
                throw new RuntimeException("split 需要2个参数");
            }
            case "contains": {
                if (args.size() >= 2) {
                    return args.get(0).toString().contains(args.get(1).toString());
                }
                throw new RuntimeException("contains 需要2个参数");
            }
            case "length": {
                if (args.size() >= 1) return args.get(0).toString().length();
                throw new RuntimeException("length 需要1个参数");
            }
            case "startsWith": {
                if (args.size() >= 2) {
                    return args.get(0).toString().startsWith(args.get(1).toString());
                }
                throw new RuntimeException("startsWith 需要2个参数");
            }
            case "endsWith": {
                if (args.size() >= 2) {
                    return args.get(0).toString().endsWith(args.get(1).toString());
                }
                throw new RuntimeException("endsWith 需要2个参数");
            }
            // ---------- JSON ----------
            case "toJson": {
                if (args.isEmpty()) throw new RuntimeException("toJson 需要1个参数");
                return toJson(args.get(0));
            }
            case "fromJson": {
                if (args.isEmpty()) throw new RuntimeException("fromJson 需要1个参数");
                return parseJson(args.get(0).toString());
            }
            
            // ---------- 命令 & 睡眠 ----------
            case "runcmd": {
                if (args.size() < 2) throw new RuntimeException("runcmd 需要2个参数");
                String executor = args.get(0).toString();
                String command = args.get(1).toString();
                if (executor.equalsIgnoreCase("CONSOLE")) {
                    Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("HimDSL"), () ->
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command));
                } else {
                    Player p = Bukkit.getPlayer(executor);
                    if (p != null) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("HimDSL"), () ->
                        p.performCommand(command));
                    }
                }
                return null;
            }
            case "sleep": {
                if (args.isEmpty()) return null;
                long ms = ((Number) args.get(0)).longValue();
                try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
                return null;
            }
            
            // ---------- 数据结构创建 ----------
            case "stack": return new Stack<Object>();
            case "queue": return new LinkedList<Object>();
            case "map": return new HashMap<Object, Object>();
            case "vector": return new ArrayList<>();
            case "set": return new HashSet<>();
            
            // ---------- 数据结构操作（函数式） ----------
            // 注意：这些函数式调用如果第一个参数是容器，会被上面对象式分支拦截，
            // 所以这里只处理参数不是容器的情况（比如 push("abc", 10) 会报错）
            case "push": {
                if (args.size() >= 2 && args.get(0) instanceof Stack) {
                    ((Stack<Object>) args.get(0)).push(args.get(1));
                    return args.get(0); // 链式
                }
                throw new RuntimeException("push 需要栈对象和元素");
            }
            case "pop": {
                if (args.size() >= 1 && args.get(0) instanceof Stack) {
                    return ((Stack<Object>) args.get(0)).pop();
                }
                throw new RuntimeException("pop 需要栈对象");
            }
            case "peek": {
                if (args.size() >= 1 && args.get(0) instanceof Stack) {
                    return ((Stack<Object>) args.get(0)).peek();
                } else if (args.size() >= 1 && args.get(0) instanceof Queue) {
                    return ((Queue<Object>) args.get(0)).peek();
                }
                throw new RuntimeException("peek 需要栈或队列对象");
            }
            case "isEmpty": {
                if (args.size() >= 1) {
                    Object obj = args.get(0);
                    if (obj instanceof Stack) return ((Stack<?>) obj).isEmpty();
                    if (obj instanceof Queue) return ((Queue<?>) obj).isEmpty();
                    if (obj instanceof Map) return ((Map<?, ?>) obj).isEmpty();
                    if (obj instanceof List) return ((List<?>) obj).isEmpty();
                    if (obj instanceof Set) return ((Set<?>) obj).isEmpty();
                    if (obj instanceof String) return ((String) obj).isEmpty();
                }
                throw new RuntimeException("isEmpty 需要容器或字符串");
            }
            case "offer": {
                if (args.size() >= 2 && args.get(0) instanceof Queue) {
                    ((Queue<Object>) args.get(0)).offer(args.get(1));
                    return args.get(0);
                }
                throw new RuntimeException("offer 需要队列对象和元素");
            }
            case "poll": {
                if (args.size() >= 1 && args.get(0) instanceof Queue) {
                    return ((Queue<Object>) args.get(0)).poll();
                }
                throw new RuntimeException("poll 需要队列对象");
            }
            case "put": {
                if (args.size() >= 3 && args.get(0) instanceof Map) {
                    ((Map<Object, Object>) args.get(0)).put(args.get(1), args.get(2));
                    return args.get(0);
                }
                throw new RuntimeException("put 需要Map对象, key, value");
            }
            case "get": {
                if (args.size() >= 2 && args.get(0) instanceof Map) {
                    return ((Map<?, ?>) args.get(0)).get(args.get(1));
                }
                if (args.size() >= 2 && args.get(0) instanceof List) {
                    int index = ((Number) args.get(1)).intValue();
                    return ((List<?>) args.get(0)).get(index);
                }
                throw new RuntimeException("get 需要Map或List对象及键/索引");
            }
            case "remove": {
                if (args.size() >= 2 && args.get(0) instanceof Map) {
                    return ((Map<Object, Object>) args.get(0)).remove(args.get(1));
                }
                if (args.size() >= 2 && args.get(0) instanceof List) {
                    Object arg = args.get(1);
                    if (arg instanceof Number) {
                        return ((List<Object>) args.get(0)).remove(((Number) arg).intValue());
                    } else {
                        return ((List<Object>) args.get(0)).remove(arg);
                    }
                }
                if (args.size() >= 2 && args.get(0) instanceof Set) {
                    return ((Set<Object>) args.get(0)).remove(args.get(1));
                }
                throw new RuntimeException("remove 需要Map/List/Set对象及键/索引/元素");
            }
            case "containsKey": {
                if (args.size() >= 2 && args.get(0) instanceof Map) {
                    return ((Map<?, ?>) args.get(0)).containsKey(args.get(1));
                }
                throw new RuntimeException("containsKey 需要Map对象和键");
            }
            
            // ---------- start / stop ----------
            case "start": {
                if (args.isEmpty()) throw new RuntimeException("start 需要函数名参数");
                String funcName = args.get(0).toString();
                if (runtimeInstance == null) throw new RuntimeException("运行时未初始化");
                runtimeInstance.startFunction(funcName);
                return null;
            }
            case "stop": {
                if (args.isEmpty()) throw new RuntimeException("stop 需要函数名参数");
                String funcName = args.get(0).toString();
                if (runtimeInstance == null) throw new RuntimeException("运行时未初始化");
                runtimeInstance.stopFunction(funcName);
                return null;
            }
            
            // ---------- 玩家操作 ----------
            case "isOp": {
                if (args.isEmpty()) throw new RuntimeException("isOp 需要一个参数（玩家对象或玩家名）");
                Object obj = args.get(0);
                Player player = null;
                if (obj instanceof Player) {
                    player = (Player) obj;
                } else if (obj instanceof String) {
                    player = Bukkit.getPlayer(obj.toString());
                } else {
                    throw new RuntimeException("isOp 参数必须是 Player 或 String");
                }
                return player != null && player.isOp();
            }
            
            // ---------- 数学函数 ----------
            case "abs": {
                Number n = (Number) args.get(0);
                if (n instanceof Integer || n instanceof Long) {
                    long val = n.longValue();
                    return val < 0 ? -val : val;
                }
                return Math.abs(n.doubleValue());
            }
            case "fabs": return Math.abs(((Number) args.get(0)).doubleValue());
            case "fmod": {
                double a = ((Number) args.get(0)).doubleValue();
                double b = ((Number) args.get(1)).doubleValue();
                return a % b;
            }
            case "max": {
                Number a = (Number) args.get(0);
                Number b = (Number) args.get(1);
                if (a instanceof Integer && b instanceof Integer) {
                    return Math.max(a.intValue(), b.intValue());
                } else if (a instanceof Long && b instanceof Long) {
                    return Math.max(a.longValue(), b.longValue());
                }
                return Math.max(a.doubleValue(), b.doubleValue());
            }
            case "min": {
                Number a = (Number) args.get(0);
                Number b = (Number) args.get(1);
                if (a instanceof Integer && b instanceof Integer) {
                    return Math.min(a.intValue(), b.intValue());
                } else if (a instanceof Long && b instanceof Long) {
                    return Math.min(a.longValue(), b.longValue());
                }
                return Math.min(a.doubleValue(), b.doubleValue());
            }
            case "pow": return Math.pow(((Number) args.get(0)).doubleValue(), ((Number) args.get(1)).doubleValue());
            case "sqrt": return Math.sqrt(((Number) args.get(0)).doubleValue());
            case "cbrt": return Math.cbrt(((Number) args.get(0)).doubleValue());
            case "hypot": return Math.hypot(((Number) args.get(0)).doubleValue(), ((Number) args.get(1)).doubleValue());
            case "log": return Math.log(((Number) args.get(0)).doubleValue());
            case "log10": return Math.log10(((Number) args.get(0)).doubleValue());
            case "log2": return Math.log(((Number) args.get(0)).doubleValue()) / Math.log(2.0);
            case "ceil": return Math.ceil(((Number) args.get(0)).doubleValue());
            case "floor": return Math.floor(((Number) args.get(0)).doubleValue());
            case "round": return Math.round(((Number) args.get(0)).doubleValue());
            case "sin": return Math.sin(((Number) args.get(0)).doubleValue());
            case "cos": return Math.cos(((Number) args.get(0)).doubleValue());
            case "tan": return Math.tan(((Number) args.get(0)).doubleValue());
            case "asin": return Math.asin(((Number) args.get(0)).doubleValue());
            case "acos": return Math.acos(((Number) args.get(0)).doubleValue());
            case "atan": return Math.atan(((Number) args.get(0)).doubleValue());
            
            // ---------- 高精度计算 ----------
            case "bigAdd": {
                if (args.size() < 2) throw new RuntimeException("bigAdd 需要两个参数");
                BigDecimal a = new BigDecimal(args.get(0).toString());
                BigDecimal b = new BigDecimal(args.get(1).toString());
                return a.add(b).toPlainString();
            }
            case "bigSub": {
                if (args.size() < 2) throw new RuntimeException("bigSub 需要两个参数");
                BigDecimal a = new BigDecimal(args.get(0).toString());
                BigDecimal b = new BigDecimal(args.get(1).toString());
                return a.subtract(b).toPlainString();
            }
            case "bigMul": {
                if (args.size() < 2) throw new RuntimeException("bigMul 需要两个参数");
                BigDecimal a = new BigDecimal(args.get(0).toString());
                BigDecimal b = new BigDecimal(args.get(1).toString());
                return a.multiply(b).toPlainString();
            }
            case "bigDiv": {
                if (args.size() < 2) throw new RuntimeException("bigDiv 需要至少两个参数，第三个可选（小数精度位数，默认 34）");
                BigDecimal a = new BigDecimal(args.get(0).toString());
                BigDecimal b = new BigDecimal(args.get(1).toString());
                int scale = 34;
                if (args.size() >= 3) {
                    scale = Integer.parseInt(args.get(2).toString());
                    if (scale < 0) throw new RuntimeException("精度必须非负");
                }
                return a.divide(b, scale, RoundingMode.HALF_UP).toPlainString();
            }
            case "bigDivExact": {
                if (args.size() < 2) throw new RuntimeException("bigDivExact 需要两个参数，要求能整除否则抛出异常");
                BigDecimal a = new BigDecimal(args.get(0).toString());
                BigDecimal b = new BigDecimal(args.get(1).toString());
                return a.divide(b).toPlainString();
            }
            // ---------- 数学扩展 ----------
            case "exp": return Math.exp(d(args, 0));
            case "expm1": return Math.expm1(d(args, 0));
            case "log1p": return Math.log1p(d(args, 0));
            case "atan2": return Math.atan2(d(args, 0), d(args, 1));
            case "sinh": return Math.sinh(d(args, 0));
            case "cosh": return Math.cosh(d(args, 0));
            case "tanh": return Math.tanh(d(args, 0));
            case "toRadians": return Math.toRadians(d(args, 0));
            case "toDegrees": return Math.toDegrees(d(args, 0));
            case "rint": return Math.rint(d(args, 0));
            case "signum": return Math.signum(d(args, 0));
            case "copySign": return Math.copySign(d(args, 0), d(args, 1));
            case "nextAfter": return Math.nextAfter(d(args, 0), d(args, 1));
            case "nextUp": return Math.nextUp(d(args, 0));
            case "nextDown": return Math.nextDown(d(args, 0));
            case "scalb": return Math.scalb(d(args, 0), i(args, 1));
            case "getExponent": return Math.getExponent(d(args, 0));
            case "IEEEremainder": return Math.IEEEremainder(d(args, 0), d(args, 1));
            
            case "addExact": {
                if (args.get(0) instanceof Integer && args.get(1) instanceof Integer)
                    return Math.addExact(i(args, 0), i(args, 1));
                return Math.addExact(l(args, 0), l(args, 1));
            }
            case "subtractExact": {
                if (args.get(0) instanceof Integer && args.get(1) instanceof Integer)
                    return Math.subtractExact(i(args, 0), i(args, 1));
                return Math.subtractExact(l(args, 0), l(args, 1));
            }
            case "multiplyExact": {
                if (args.get(0) instanceof Integer && args.get(1) instanceof Integer)
                    return Math.multiplyExact(i(args, 0), i(args, 1));
                return Math.multiplyExact(l(args, 0), l(args, 1));
            }
            case "incrementExact": {
                if (args.get(0) instanceof Integer)
                    return Math.incrementExact(i(args, 0));
                return Math.incrementExact(l(args, 0));
            }
            case "decrementExact": {
                if (args.get(0) instanceof Integer)
                    return Math.decrementExact(i(args, 0));
                return Math.decrementExact(l(args, 0));
            }
            case "negateExact": {
                if (args.get(0) instanceof Integer)
                    return Math.negateExact(i(args, 0));
                return Math.negateExact(l(args, 0));
            }
            case "floorDiv": {
                if (args.get(0) instanceof Integer && args.get(1) instanceof Integer)
                    return Math.floorDiv(i(args, 0), i(args, 1));
                return Math.floorDiv(l(args, 0), l(args, 1));
            }
            case "floorMod": {
                if (args.get(0) instanceof Integer && args.get(1) instanceof Integer)
                    return Math.floorMod(i(args, 0), i(args, 1));
                return Math.floorMod(l(args, 0), l(args, 1));
            }
            case "toIntExact": return Math.toIntExact(l(args, 0));
            case "random": return Math.random();
            case "fma": return Math.fma(d(args, 0), d(args, 1), d(args, 2)); // Java 9+
            case "ulp": return Math.ulp(d(args, 0));
            // ---------- PAPI ----------
            case "papi": {
                if (args.isEmpty()) throw new RuntimeException("papi 需要一个变量名参数");
                String key = args.get(0).toString();
                Object player = args.size() > 1 ? args.get(1) : null;
                return PapiBridge.get(key, player);
            }
            case "papiSet": {
                if (args.size() < 2) throw new RuntimeException("papiSet 需要变量名和值");
                PapiBridge.set(args.get(0).toString(), args.get(1).toString());
                return null;
            }
            case "papiRegister": {
                if (args.isEmpty()) throw new RuntimeException("papiRegister 需要变量名");
                PapiBridge.register(args.get(0).toString());
                return null;
            }
            case "papiUnregister": {
                if (args.isEmpty()) throw new RuntimeException("papiUnregister 需要变量名");
                PapiBridge.unregister(args.get(0).toString());
                return null;
            }
            case "papiHas": {
                if (args.isEmpty()) throw new RuntimeException("papiHas 需要变量名");
                return PapiBridge.has(args.get(0).toString());
            }
            
            // ---------- 文件 IO ----------
            case "fopen": {
                if (args.size() < 2) throw new RuntimeException("fopen 需要路径和模式");
                String path = args.get(0).toString();
                String mode = args.get(1).toString();
                File file = resolvePath(path);
                checkPathSafety(file);
                file.getParentFile().mkdirs();
                Closeable stream;
                try {
                    switch (mode) {
                        case "r":
                        stream = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8));
                        break;
                        case "w":
                        stream = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8, false));
                        break;
                        case "a":
                        stream = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8, true));
                        break;
                        default:
                        throw new RuntimeException("未知文件模式: " + mode + "，支持: r, w, a");
                    }
                } catch (IOException e) {
                    throw new RuntimeException("打开文件失败: " + e.getMessage());
                }
                int id = fileIdGenerator.getAndIncrement();
                openFiles.put(id, stream);
                return id;
            }
            case "fread": {
                if (args.isEmpty()) throw new RuntimeException("fread 需要文件句柄");
                int id = ((Number) args.get(0)).intValue();
                Closeable stream = openFiles.get(id);
                if (stream == null) throw new RuntimeException("无效的文件句柄: " + id);
                if (!(stream instanceof BufferedReader)) {
                    throw new RuntimeException("句柄不是读模式");
                }
                BufferedReader reader = (BufferedReader) stream;
                StringBuilder sb = new StringBuilder();
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    return sb.toString();
                } catch (IOException e) {
                    throw new RuntimeException("读取文件失败: " + e.getMessage());
                }
            }
            case "fwrite": {
                if (args.size() < 2) throw new RuntimeException("fwrite 需要文件句柄和内容");
                int id = ((Number) args.get(0)).intValue();
                String content = args.get(1).toString();
                Closeable stream = openFiles.get(id);
                if (stream == null) throw new RuntimeException("无效的文件句柄: " + id);
                if (!(stream instanceof BufferedWriter)) {
                    throw new RuntimeException("句柄不是写模式");
                }
                BufferedWriter writer = (BufferedWriter) stream;
                try {
                    writer.write(content);
                    writer.flush();
                    return null;
                } catch (IOException e) {
                    throw new RuntimeException("写入文件失败: " + e.getMessage());
                }
            }
            case "fclose": {
                if (args.isEmpty()) throw new RuntimeException("fclose 需要文件句柄");
                int id = ((Number) args.get(0)).intValue();
                Closeable stream = openFiles.remove(id);
                if (stream == null) return null;
                try {
                    stream.close();
                } catch (IOException e) {
                    throw new RuntimeException("关闭文件失败: " + e.getMessage());
                }
                return null;
            }
            case "fexists": {
                if (args.isEmpty()) throw new RuntimeException("fexists 需要路径");
                String path = args.get(0).toString();
                File file = resolvePath(path);
                checkPathSafety(file);
                return file.exists();
            }
            case "fdelete": {
                if (args.isEmpty()) throw new RuntimeException("fdelete 需要路径");
                String path = args.get(0).toString();
                File file = resolvePath(path);
                checkPathSafety(file);
                return file.delete();
            }
            case "freadlines": {
                if (args.isEmpty()) throw new RuntimeException("freadlines 需要路径");
                String path = args.get(0).toString();
                File file = resolvePath(path);
                checkPathSafety(file);
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("读取文件失败: " + e.getMessage());
                }
                return lines;
            }
            case "fwritelines": {
                if (args.size() < 2) throw new RuntimeException("fwritelines 需要路径和字符串列表");
                String path = args.get(0).toString();
                Object listObj = args.get(1);
                if (!(listObj instanceof List)) throw new RuntimeException("第二个参数必须是字符串列表");
                List<?> rawList = (List<?>) listObj;
                List<String> lines = new ArrayList<>();
                for (Object o : rawList) {
                    lines.add(o.toString());
                }
                File file = resolvePath(path);
                checkPathSafety(file);
                file.getParentFile().mkdirs();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8, false))) {
                    for (String line : lines) {
                        writer.write(line);
                        writer.newLine();
                    }
                } catch (IOException e) {
                    throw new RuntimeException("写入文件失败: " + e.getMessage());
                }
                return null;
            }
            
            default:
            throw new RuntimeException("未知内置函数: " + name);
        }
    }
    // ---------- JSON 序列化 ----------
    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return quoteString((String) obj);
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Character) return quoteString(String.valueOf(obj));
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) obj).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(quoteString(e.getKey().toString()))
                .append(":")
                .append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object v : (List<?>) obj) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(v));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Set) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object v : (Set<?>) obj) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(v));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("[");
            int len = Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(Array.get(obj, i)));
            }
            sb.append("]");
            return sb.toString();
        }
        // 兜底：按字符串处理
        return quoteString(obj.toString());
    }
    
    private static String quoteString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                if (c < 0x20) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
    
    private static Object parseJson(String text) {
        if (text == null) throw new RuntimeException("JSON 字符串为 null");
        JsonParser parser = new JsonParser(text);
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.text.length()) {
            throw new RuntimeException("JSON 解析错误：位置 " + parser.pos + " 处存在多余字符");
        }
        return result;
    }
    
    /** 极简 JSON 解析器，避免额外依赖 */
    private static class JsonParser {
        final String text;
        int pos = 0;
        
        JsonParser(String text) { this.text = text; }
        
        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }
        
        Object parseValue() {
            skipWhitespace();
            if (pos >= text.length()) throw new RuntimeException("JSON 意外结束");
            char c = text.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            return parseNumber();
        }
        
        Map<Object, Object> parseObject() {
            Map<Object, Object> map = new HashMap<>();
            pos++; // 吃掉 '{'
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != '"')
                    throw new RuntimeException("JSON 对象键必须是字符串，位置 " + pos);
                String key = parseString();
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != ':')
                    throw new RuntimeException("JSON 对象缺少冒号，位置 " + pos);
                pos++;
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos >= text.length()) throw new RuntimeException("JSON 对象未闭合");
                char c = text.charAt(pos);
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; break; }
                throw new RuntimeException("JSON 对象缺少 ',' 或 '}'，位置 " + pos);
            }
            return map;
        }
        
        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // 吃掉 '['
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == ']') { pos++; return list; }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                if (pos >= text.length()) throw new RuntimeException("JSON 数组未闭合");
                char c = text.charAt(pos);
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; break; }
                throw new RuntimeException("JSON 数组缺少 ',' 或 ']'，位置 " + pos);
            }
            return list;
        }
        
        String parseString() {
            pos++; // 吃掉开头的引号
            StringBuilder sb = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '"') { pos++; return sb.toString(); }
                if (c == '\\') {
                    pos++;
                    if (pos >= text.length())
                        throw new RuntimeException("JSON 字符串转义不完整");
                    char esc = text.charAt(pos);
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u': {
                            if (pos + 4 >= text.length())
                                throw new RuntimeException("JSON \\u 转义不完整");
                            String hex = text.substring(pos + 1, pos + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        }
                        default:
                        throw new RuntimeException("未知 JSON 转义: \\" + esc);
                    }
                    pos++;
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            throw new RuntimeException("JSON 字符串未闭合");
        }
        
        Object parseNumber() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E'
                || (c >= '0' && c <= '9')) pos++;
                else break;
            }
            String num = text.substring(start, pos);
            if (num.isEmpty()) throw new RuntimeException("无效 JSON 数字，位置 " + start);
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) {
                    return Double.parseDouble(num);
                } else {
                    long l = Long.parseLong(num);
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                    return l;
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException("无效 JSON 数字: " + num);
            }
        }
        
        Boolean parseBoolean() {
            if (text.startsWith("true", pos))  { pos += 4; return Boolean.TRUE; }
            if (text.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new RuntimeException("无效 JSON 布尔值，位置 " + pos);
        }
        
        Object parseNull() {
            if (text.startsWith("null", pos)) { pos += 4; return null; }
            throw new RuntimeException("无效 JSON null，位置 " + pos);
        }
    }
    // ---------- 类型转换辅助 ----------
    private static int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return 0; }
    }
    private static double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try { return Double.parseDouble(obj.toString()); } catch (Exception e) { return 0.0; }
    }
    private static long toLong(Object obj) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return 0L; }
    }
    private static boolean toBool(Object obj) {
        if (obj instanceof Boolean) return (boolean) obj;
        if (obj instanceof Number) return ((Number) obj).intValue() != 0;
        return "true".equalsIgnoreCase(obj.toString());
    }
    private static float toFloat(Object obj) {
    if (obj instanceof Number) return ((Number) obj).floatValue();
    try { return Float.parseFloat(obj.toString()); }
    catch (Exception e) { return 0.0f; }
}
    private static double d(List<Object> args, int index) {
        return ((Number) args.get(index)).doubleValue();
    }
    
    private static int i(List<Object> args, int index) {
        return ((Number) args.get(index)).intValue();
    }
    
    private static long l(List<Object> args, int index) {
        return ((Number) args.get(index)).longValue();
    }
}