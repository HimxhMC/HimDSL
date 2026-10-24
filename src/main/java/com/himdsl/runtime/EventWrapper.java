package com.himdsl.runtime;

import org.bukkit.event.Event;
import java.lang.reflect.Method;
import java.util.Arrays;

public class EventWrapper {
    private final Event event;
    private String pendingMethod; // 暂存方法名（在 '.' 后）

    public EventWrapper(Event event) {
        this.event = event;
    }

    public Event getEvent() {
        return event;
    }

    public String getPendingMethod() {
        return pendingMethod;
    }

    public void setPendingMethod(String method) {
        this.pendingMethod = method;
    }

    /**
     * 调用暂存的方法（由 visitPostfix 中的 '(' 触发）
     */
    public Object invokePendingMethod(Object... args) {
        if (pendingMethod == null) {
            throw new RuntimeException("没有待执行的事件方法");
        }
        try {
            // 查找匹配的方法（根据方法名和参数类型）
            Method[] methods = event.getClass().getMethods();
            for (Method m : methods) {
                if (!m.getName().equals(pendingMethod)) continue;
                if (m.getParameterCount() != args.length) continue;
                
                // 检查参数类型是否匹配
                boolean match = true;
                for (int i = 0; i < args.length; i++) {
                    Class<?> paramType = m.getParameterTypes()[i];
                    if (args[i] == null) {
                        // 如果参数为 null，只能匹配对象类型
                        if (paramType.isPrimitive()) {
                            match = false;
                            break;
                        }
                    } else if (!paramType.isAssignableFrom(args[i].getClass())) {
                        // 尝试自动拆箱（例如 int -> Integer）
                        if (paramType.isPrimitive()) {
                            // 简单转换：int, boolean, double, etc.
                            if (!isPrimitiveMatch(paramType, args[i].getClass())) {
                                match = false;
                                break;
                            }
                        } else {
                            match = false;
                            break;
                        }
                    }
                }
                if (match) {
                    // 如果参数是基本类型，需要转换
                    Object[] convertedArgs = convertArgs(args, m.getParameterTypes());
                    return m.invoke(event, convertedArgs);
                }
            }
            throw new RuntimeException("找不到匹配的方法: " + pendingMethod + " 参数个数: " + args.length);
        } catch (Exception e) {
            throw new RuntimeException("调用事件方法失败: " + pendingMethod, e);
        }
    }

    private boolean isPrimitiveMatch(Class<?> primitive, Class<?> wrapper) {
        if (primitive == int.class) return wrapper == Integer.class;
        if (primitive == boolean.class) return wrapper == Boolean.class;
        if (primitive == double.class) return wrapper == Double.class;
        if (primitive == long.class) return wrapper == Long.class;
        if (primitive == float.class) return wrapper == Float.class;
        if (primitive == short.class) return wrapper == Short.class;
        if (primitive == byte.class) return wrapper == Byte.class;
        if (primitive == char.class) return wrapper == Character.class;
        return false;
    }

    private Object[] convertArgs(Object[] args, Class<?>[] targetTypes) {
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                converted[i] = null;
                continue;
            }
            Class<?> target = targetTypes[i];
            if (target.isAssignableFrom(args[i].getClass())) {
                converted[i] = args[i];
            } else if (target == int.class) {
                converted[i] = ((Number) args[i]).intValue();
            } else if (target == boolean.class) {
                converted[i] = args[i];
            } else if (target == double.class) {
                converted[i] = ((Number) args[i]).doubleValue();
            } else if (target == long.class) {
                converted[i] = ((Number) args[i]).longValue();
            } else {
                converted[i] = args[i];
            }
        }
        return converted;
    }
}