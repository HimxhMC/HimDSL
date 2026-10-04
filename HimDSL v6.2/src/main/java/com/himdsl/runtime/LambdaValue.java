package com.himdsl.runtime;

import com.himdsl.parser.HimDSLVisitorImpl;
import org.antlr.v4.runtime.tree.ParseTree;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.List;

/**
* DSL 层 lambda 值。
* 捕获定义处环境引用（闭包），调用时新建子环境绑定参数。
*/
public class LambdaValue {
    public final List<String> paramNames;
    public final List<String> paramTypes;   // 元素可为 null，表示未声明
    public final ParseTree body;            // BlockContext 或 ExpressionContext
    public final boolean isExprBody;
    public final Environment capturedEnv;
    public final HimDSLRuntime runtime;
    private static final Map<Class<?>, Method> SAM_CACHE = new ConcurrentHashMap<>();
    
    public LambdaValue(List<String> paramNames, List<String> paramTypes,
        ParseTree body, boolean isExprBody,
        Environment capturedEnv, HimDSLRuntime runtime) {
            this.paramNames = paramNames;
            this.paramTypes = paramTypes;
            this.body = body;
            this.isExprBody = isExprBody;
            this.capturedEnv = capturedEnv;
            this.runtime = runtime;
        }
        
public Object apply(List<Object> args) {
    return applyWithBindings(args, null);
}

public Object applyWithBindings(List<Object> args, Map<String, Object> extraBindings) {
    if (args.size() != paramNames.size()) {
        throw new RuntimeException("lambda 期望 " + paramNames.size()
                + " 个参数，实际 " + args.size() + " 个");
    }
    Environment env = new Environment(capturedEnv);

    // 注入额外绑定（如 _trigger_player）
    if (extraBindings != null) {
        for (Map.Entry<String, Object> e : extraBindings.entrySet()) {
            env.declare(e.getKey(), e.getValue());
        }
    }

    for (int i = 0; i < paramNames.size(); i++) {
        Object v = args.get(i);
        String declared = paramTypes.get(i);
        if (declared != null) v = checkType(v, declared);
        env.declare(paramNames.get(i), v);
    }

    HimDSLVisitorImpl visitor = new HimDSLVisitorImpl(runtime);
    visitor.setEnvironment(env);
    return visitor.invokeLambdaBody(body, isExprBody);
}
        
        /** 类型声明存在时的粗检查 + 转换 */
        private Object checkType(Object v, String typeName) {
            if (v == null) return null;
            switch (typeName) {
                case "int":
                if (!(v instanceof Number)) {
                    throw new RuntimeException("lambda 参数类型不匹配: 期望 int，实际 "
                    + v.getClass().getSimpleName());
                }
                return ((Number) v).intValue();
                case "double":
                if (!(v instanceof Number)) {
                    throw new RuntimeException("lambda 参数类型不匹配: 期望 double，实际 "
                    + v.getClass().getSimpleName());
                }
                return ((Number) v).doubleValue();
                case "bool":
                if (!(v instanceof Boolean)) {
                    throw new RuntimeException("lambda 参数类型不匹配: 期望 bool，实际 "
                    + v.getClass().getSimpleName());
                }
                return v;
                case "string":
                return v.toString();
                case "void":
                return v;
                default:
                // 结构体名 / 类名 / 自定义类型：不做深检查
                return v;
            }
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("<lambda (");
            for (int i = 0; i < paramNames.size(); i++) {
                if (i > 0) sb.append(", ");
                String t = paramTypes.get(i);
                if (t != null) sb.append(t).append(' ');
                sb.append(paramNames.get(i));
            }
            sb.append(") -> ...>");
            return sb.toString();
        }
        public static Method findSAM(Class<?> iface) {
            Method cached = SAM_CACHE.get(iface);
            if (cached != null) return cached;
            Method sam = null;
            for (Method m : iface.getMethods()) {
                if (m.getDeclaringClass() == Object.class) continue;
                if (m.isDefault()) continue;
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (!Modifier.isAbstract(m.getModifiers())) continue;
                if (sam != null) { SAM_CACHE.put(iface, null); return null; }
                sam = m;
            }
            if (sam != null) SAM_CACHE.put(iface, sam);
            return sam;
        }
        
        /** 把本 lambda 适配成函数式接口实例 */
        public Object adaptTo(Class<?> iface) {
            Method sam = findSAM(iface);
            if (sam == null) throw new RuntimeException(iface.getName() + " 不是函数式接口");
            return Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        switch (method.getName()) {
                            case "toString": return "<lambda for " + iface.getSimpleName() + ">";
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals":   return proxy == args[0];
                        }
                    }
                    if (method.isDefault()) {
                        throw new RuntimeException("不支持调用 default 方法: " + method.getName());
                    }
                    if (!method.equals(sam)) {
                        throw new RuntimeException("非 SAM 方法: " + method.getName());
                    }
                    List<Object> argList = args == null
                    ? new java.util.ArrayList<>()
                    : new java.util.ArrayList<>(Arrays.asList(args));
                    Object result = apply(argList);
                    return convertReturn(result, method.getReturnType());
                });
            }
            
            /** lambda 返回值 → Java 方法声明返回类型 */
            private static Object convertReturn(Object result, Class<?> target) {
                if (target == void.class || target == Void.class) return null;
                if (result == null) return null;
                if (target.isAssignableFrom(result.getClass())) return result;
                if (target == boolean.class || target == Boolean.class) return toBool(result);
                if (target == int.class     || target == Integer.class) return ((Number) result).intValue();
                if (target == long.class    || target == Long.class)    return ((Number) result).longValue();
                if (target == double.class  || target == Double.class)  return ((Number) result).doubleValue();
                if (target == float.class   || target == Float.class)   return ((Number) result).floatValue();
                if (target == short.class   || target == Short.class)   return ((Number) result).shortValue();
                if (target == byte.class    || target == Byte.class)    return ((Number) result).byteValue();
                if (target == char.class    || target == Character.class) return result.toString().charAt(0);
                if (target == String.class) return result.toString();
                if (target.isEnum() && result instanceof String) {
                    for (Object c : target.getEnumConstants()) {
                        if (((Enum<?>) c).name().equals(result)) return c;
                    }
                    for (Object c : target.getEnumConstants()) {
                        if (((Enum<?>) c).name().equalsIgnoreCase((String) result)) return c;
                    }
                }
                return result;
            }
            
            private static boolean toBool(Object o) {
                if (o instanceof Boolean) return (Boolean) o;
                if (o instanceof Number) return ((Number) o).doubleValue() != 0;
                return true;
            }
        }