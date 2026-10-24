package com.himdsl.runtime;

/**
 * DSL 类型强制工具。所有需要按声明类型对齐的地方都走这里。
 * 覆盖 int / long / float / double / bool / string，其余原样返回。
 */
public final class TypeCoerce {
    private TypeCoerce() {}

    /** 按声明类型把 value 转换。类型为 null 或非内置类型时原样返回。 */
    public static Object convert(Object value, String type) {
        if (type == null || value == null) return value;
        switch (type) {
            case "int":
                if (value instanceof Integer) return value;
                if (value instanceof Number)  return ((Number) value).intValue();
                if (value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
                if (value instanceof Character) return (int) (char) (Character) value;
                if (value instanceof String) {
                    try { return Integer.parseInt(((String) value).trim()); }
                    catch (Exception e) { return value; }
                }
                return value;
            case "long":
                if (value instanceof Long) return value;
                if (value instanceof Number)  return ((Number) value).longValue();
                if (value instanceof Boolean) return ((Boolean) value) ? 1L : 0L;
                if (value instanceof Character) return (long) (char) (Character) value;
                if (value instanceof String) {
                    try { return Long.parseLong(((String) value).trim()); }
                    catch (Exception e) { return value; }
                }
                return value;
            case "float":
                if (value instanceof Float) return value;
                if (value instanceof Number)  return ((Number) value).floatValue();
                if (value instanceof Boolean) return ((Boolean) value) ? 1f : 0f;
                if (value instanceof Character) return (float) (char) (Character) value;
                if (value instanceof String) {
                    try { return Float.parseFloat(((String) value).trim()); }
                    catch (Exception e) { return value; }
                }
                return value;
            case "double":
                if (value instanceof Double) return value;
                if (value instanceof Number)  return ((Number) value).doubleValue();
                if (value instanceof Boolean) return ((Boolean) value) ? 1d : 0d;
                if (value instanceof Character) return (double) (char) (Character) value;
                if (value instanceof String) {
                    try { return Double.parseDouble(((String) value).trim()); }
                    catch (Exception e) { return value; }
                }
                return value;
            case "bool":
                if (value instanceof Boolean) return value;
                if (value instanceof Number)  return ((Number) value).doubleValue() != 0;
                if (value instanceof String)  return Boolean.parseBoolean((String) value);
                return value;
            case "string":
                return (value instanceof String) ? value : value.toString();
            default:
                return value;   // 结构体 / Entity / 自定义类型：原样
        }
    }

    /** 未显式初始化的默认值 */
    public static Object defaultValue(String type) {
        if (type == null) return 0;
        switch (type) {
            case "int":    return 0;
            case "long":   return 0L;
            case "float":  return 0.0f;
            case "double": return 0.0;
            case "bool":   return false;
            case "string": return "";
            default:       return 0;
        }
    }

    /** 是否为内置标量类型 */
    public static boolean isScalar(String type) {
        return "int".equals(type) || "long".equals(type)
            || "float".equals(type) || "double".equals(type)
            || "bool".equals(type) || "string".equals(type);
    }

    /** 数值二元运算，遵循 Java 提升规则 */
    public static Object numericOp(Number l, Number r, String op) {
        if (l instanceof Double || r instanceof Double) {
            double a = l.doubleValue(), b = r.doubleValue();
            switch (op) {
                case "+": return a + b;
                case "-": return a - b;
                case "*": return a * b;
                case "/": return a / b;
                case "%": return a % b;
            }
        } else if (l instanceof Float || r instanceof Float) {
            float a = l.floatValue(), b = r.floatValue();
            switch (op) {
                case "+": return a + b;
                case "-": return a - b;
                case "*": return a * b;
                case "/": return a / b;
                case "%": return a % b;
            }
        } else if (l instanceof Long || r instanceof Long) {
            long a = l.longValue(), b = r.longValue();
            switch (op) {
                case "+": return a + b;
                case "-": return a - b;
                case "*": return a * b;
                case "/": if (b == 0) throw new ArithmeticException("/ by zero"); return a / b;
                case "%": if (b == 0) throw new ArithmeticException("% by zero"); return a % b;
            }
        } else {
            int a = l.intValue(), b = r.intValue();
            switch (op) {
                case "+": return a + b;
                case "-": return a - b;
                case "*": return a * b;
                case "/": if (b == 0) throw new ArithmeticException("/ by zero"); return a / b;
                case "%": if (b == 0) throw new ArithmeticException("% by zero"); return a % b;
            }
        }
        return 0;
    }

    /** 通用二元算术：支持字符串拼接回退 */
    public static Object arith(Object left, Object right, String op) {
        if (op.equals("+") && (left instanceof String || right instanceof String)) {
            return left.toString() + right.toString();
        }
        if (left instanceof Number && right instanceof Number) {
            return numericOp((Number) left, (Number) right, op);
        }
        // 回退：能当数字当数字，否则视 + 为拼接
        if (op.equals("+")) return left.toString() + right.toString();
        try {
            double a = Double.parseDouble(left.toString());
            double b = Double.parseDouble(right.toString());
            switch (op) {
                case "-": return a - b;
                case "*": return a * b;
                case "/": return a / b;
                case "%": return a % b;
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    /** 数值取负，保持原类型 */
    public static Object negate(Object val) {
        if (!(val instanceof Number)) {
            try { return -Double.parseDouble(val.toString()); }
            catch (NumberFormatException e) { return 0; }
        }
        Number n = (Number) val;
        if (n instanceof Double)  return -n.doubleValue();
        if (n instanceof Float)   return -n.floatValue();
        if (n instanceof Long)    return -n.longValue();
        if (n instanceof Integer) return -n.intValue();
        if (n instanceof Short)   return (short) -n.shortValue();
        if (n instanceof Byte)    return (byte) -n.byteValue();
        return -n.doubleValue();
    }

    /** 自增/自减，保持原类型 */
    public static Object incDec(Object val, int delta) {
        if (val == null) return delta;
        if (val instanceof Integer)   return (Integer) val + delta;
        if (val instanceof Long)      return (Long) val + delta;
        if (val instanceof Double)    return (Double) val + delta;
        if (val instanceof Float)     return (Float) val + delta;
        if (val instanceof Short)     return (short) ((Short) val + delta);
        if (val instanceof Byte)      return (byte) ((Byte) val + delta);
        if (val instanceof Character) return (char) (((Character) val) + delta);
        if (val instanceof Number)    return ((Number) val).doubleValue() + delta;
        throw new RuntimeException("++/-- 不支持类型: " + val.getClass().getSimpleName());
    }
}