package com.himdsl.parser;

import com.himdsl.HimDSLPlugin;
import com.himdsl.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;

import java.util.*;
import java.lang.reflect.Method;
import java.lang.reflect.Array;

public class HimDSLVisitorImpl extends HimDSLBaseVisitor<Object> {
    private final HimDSLRuntime runtime;
    private Environment currentEnv;
    private boolean returnFlag = false;
    private Object returnValue = null;
    private boolean breakFlag = false;
    private boolean continueFlag = false;
    private static final Object STATIC_FIELD_MISS = new Object();
    
    public HimDSLVisitorImpl(HimDSLRuntime runtime) {
        this.runtime = runtime;
        this.currentEnv = runtime.globalEnv;
    }
    
    public void collectDefinitions(ParseTree tree) {
        visit(tree);
    }
    
public void executeMain() {
    FunctionDef mainFunc = runtime.resolveFunction("main", java.util.Collections.emptyList());
    if (mainFunc == null) throw new RuntimeException("未找到 0 参 main 函数");
    Environment oldEnv = currentEnv;
    currentEnv = new Environment(runtime.globalEnv);
    try {
        visit(mainFunc.body);
    } finally {
        currentEnv = oldEnv;
    }
}

public void executeMain(List<Object> arguments) {
    FunctionDef mainFunc = runtime.resolveFunction("main", arguments);
    if (mainFunc == null) {
        throw new RuntimeException("未找到与参数个数匹配的 main 函数（参数 " + arguments.size() + " 个）");
    }
    Environment oldEnv = currentEnv;
    currentEnv = new Environment(runtime.globalEnv);
    try {
        for (int i = 0; i < mainFunc.parameters.size(); i++) {
            currentEnv.set(mainFunc.parameters.get(i), arguments.get(i));
        }
        visit(mainFunc.body);
    } finally {
        currentEnv = oldEnv;
    }
}
    @Override
    public Object visitProgram(HimDSLParser.ProgramContext ctx) {
        if (HimDSLPlugin.DEBUG_MODE) {
            Bukkit.getLogger().info(">>> visitProgram 进入，structDef 数量: " + ctx.structDef().size() + 
            ", declaration 数量: " + ctx.declaration().size() + 
            ", functionDef 数量: " + ctx.functionDef().size());
        }
        
        // 1. 先处理结构体定义（注册类型）
        for (HimDSLParser.StructDefContext struct : ctx.structDef()) {
            visit(struct);
        }
        for (HimDSLParser.EnumDefContext enumCtx : ctx.enumDef()) {
            visit(enumCtx);
        }
        
        // 2. 再处理声明（变量、玩家、数组）
        for (HimDSLParser.DeclarationContext decl : ctx.declaration()) {
            visit(decl);
        }
        
        // 3. 最后处理函数定义
        for (HimDSLParser.FunctionDefContext func : ctx.functionDef()) {
            if (HimDSLPlugin.DEBUG_MODE) {
                Bukkit.getLogger().info("  遇到函数定义: " + func.IDENTIFIER().getText());
            }
            visit(func);
        }
        
        if (HimDSLPlugin.DEBUG_MODE) {
            Bukkit.getLogger().info("<<< visitProgram 退出");
        }
        return null;
    }
@Override
public Object visitEnumDef(HimDSLParser.EnumDefContext ctx) {
    String name = ctx.IDENTIFIER().getText();
    Set<String> constants = new LinkedHashSet<>();
    for (HimDSLParser.EnumMemberContext m : ctx.enumMember()) {
        constants.add(m.IDENTIFIER().getText());
    }
    EnumRegistry.registerUserEnum(name, constants);
    if (HimDSLPlugin.DEBUG_MODE) {
        Bukkit.getLogger().info("注册用户枚举: " + name + " = " + constants);
    }
    return null;
}
@Override
public Object visitFunctionDef(HimDSLParser.FunctionDefContext ctx) {
    String name = ctx.IDENTIFIER().getText();
    List<String> params = new ArrayList<>();
    List<String> paramTypes = new ArrayList<>();
    String eventType = null;

    if (ctx.paramList() != null) {
        for (HimDSLParser.ParamContext p : ctx.paramList().param()) {
            if (p instanceof HimDSLParser.EventParamContext) {
                HimDSLParser.EventParamContext ep = (HimDSLParser.EventParamContext) p;
                String first = ep.IDENTIFIER().getText();
                if (first.equals("event")) {
                    String typeText = ep.eventType().getText();
                    if (typeText.startsWith("\"") && typeText.endsWith("\"")) {
                        eventType = typeText.substring(1, typeText.length() - 1);
                    } else {
                        eventType = typeText;
                    }
                } else {
                    params.add(first);
                    paramTypes.add(null);
                }
            } else if (p instanceof HimDSLParser.NormalParamContext) {
                HimDSLParser.NormalParamContext np = (HimDSLParser.NormalParamContext) p;
                params.add(np.IDENTIFIER().getText());
                paramTypes.add(np.type().getText());
            }
        }
    }

    boolean isVoid = ctx.returnType().getText().equals("void");
    String annotation = null;
    if (ctx.annotation() != null && !ctx.annotation().isEmpty()) {
        annotation = ctx.annotation().get(0).IDENTIFIER().getText();
    }
    String schedule = ctx.timeSpec() != null ? ctx.timeSpec().getText() : null;

    FunctionDef func = new FunctionDef(name, params, paramTypes, ctx.block(),
            isVoid, annotation, schedule, eventType);
    runtime.registerFunction(func);
    if (HimDSLPlugin.DEBUG_MODE) {
        Bukkit.getLogger().info("  已注册函数 " + name + "/" + params.size()
                + "，当前同名重载数: " + runtime.functions.get(name).size());
    }
    return null;
}
    
@Override
public Object visitBlock(HimDSLParser.BlockContext ctx) {
    if (HimDSLPlugin.DEBUG_MODE) Bukkit.getLogger().info("  进入 block，语句数量: " + ctx.statement().size());
    Environment oldEnv = currentEnv;
    currentEnv = new Environment(oldEnv);

    // 块级枚举作用域：进入时快照，退出时恢复
    Map<String, Set<String>> enumSnapshot = EnumRegistry.snapshotUserEnums();

    try {
        int idx = 0;
        for (HimDSLParser.StatementContext stmt : ctx.statement()) {
            if (HimDSLPlugin.DEBUG_MODE) Bukkit.getLogger().info("    执行语句 #" + idx + ", 类型: " + stmt.getClass().getSimpleName());
            visit(stmt);
            if (returnFlag || breakFlag || continueFlag) {
                if (HimDSLPlugin.DEBUG_MODE) Bukkit.getLogger().info("      遇到 return/break/continue，停止当前 block");
                break;
            }
            idx++;
        }
    } finally {
        currentEnv = oldEnv;
        EnumRegistry.restoreUserEnums(enumSnapshot);
    }
    if (HimDSLPlugin.DEBUG_MODE) Bukkit.getLogger().info("  退出 block");
    return null;
}
    
    @Override
    public Object visitStatement(HimDSLParser.StatementContext ctx) {
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("       visitStatement 进入: " + ctx.getText());
        return visitChildren(ctx);
    }
    
@Override
public Object visitVariableDecl(HimDSLParser.VariableDeclContext ctx) {
    String name = ctx.IDENTIFIER().getText();
    Object value = 0;
    boolean isStructType = false;
    String typeName = null;

    if (ctx.type() != null) {
        typeName = ctx.type().getText();
        if (runtime.getStruct(typeName) != null) {
            isStructType = true;
        }
    }

    if (ctx.expression() != null) {
        value = visit(ctx.expression());
    } else if (isStructType) {
        if ("Entity".equals(typeName)) {
            // Entity 现在是引用类型，默认置空，等后续赋值为 @s / @p 等选择器
            value = null;
        } else {
            StructDef sd = runtime.getStruct(typeName);
            StructInstance inst = new StructInstance(typeName);
            if (sd != null) {
                for (StructDef.StructField f : sd.fields) {
                    inst.declareField(f.name);
                }
            }
            value = inst;
        }
    }

    currentEnv.set(name, value);
    if (HimDSLPlugin.DEBUG_MODE) {
        Bukkit.getLogger().info("变量声明: " + name + " = " + value +
                " (类型: " + (value == null ? "null" : value.getClass().getSimpleName()) + ")");
    }
    return null;
}
    @Override
    public Object visitVarDeclNoSemi(HimDSLParser.VarDeclNoSemiContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        Object value = 0;
        if (ctx.expression() != null) {
            String exprText = ctx.expression().getText();
            value = visit(ctx.expression());
            if (value instanceof Boolean && exprText.matches("\\d+(\\.\\d+)?")) {
                if (exprText.contains(".")) value = Double.parseDouble(exprText);
                else value = Integer.parseInt(exprText);
            }
        }
        currentEnv.set(name, value);
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("for 变量声明: " + name + " = " + value);
        return null;
    }
    
    @Override
    public Object visitPlayerDecl(HimDSLParser.PlayerDeclContext ctx) {
        String varName = ctx.IDENTIFIER().getText();
        String selectorText = ctx.selector().getText();
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info(">>> visitPlayerDecl: varName=" + varName + ", selector=" + selectorText);
        
        Location origin = (Location) currentEnv.get("__origin__");
        Object player = SelectorParser.parse(selectorText, currentEnv, origin);
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("    SelectorParser 返回: " + player + (player instanceof Player ? " (Player)" : " (非Player)"));
        
        currentEnv.set(varName, player);
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("    已设置变量 '" + varName + "' = " + player);
        return null;
    }
    
    @Override
    public Object visitArrayDecl(HimDSLParser.ArrayDeclContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        // 获取长度表达式（括号内的）
        HimDSLParser.ExpressionContext sizeExpr = ctx.expression(); // 只有一个
        HimDSLParser.ArrayInitializerContext initCtx = ctx.arrayInitializer();
        
        List<Object> array = null;
        int size = 0;
        
        // 解析长度
        if (sizeExpr != null) {
            Object sizeVal = visit(sizeExpr);
            if (sizeVal instanceof Number) {
                size = ((Number) sizeVal).intValue();
            } else {
                throw new RuntimeException("数组长度必须是数字");
            }
        }
        
        if (initCtx != null) {
            // 有初始化列表
            List<Object> initList = new ArrayList<>();
            for (HimDSLParser.ExpressionContext expr : initCtx.expression()) {
                initList.add(visit(expr));
            }
            int initSize = initList.size();
            if (size == 0) {
                // 如果没有指定长度，使用初始化列表的长度
                size = initSize;
                array = new ArrayList<>(initList);
            } else {
                // 指定了长度，截取或补0
                array = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    if (i < initSize) {
                        array.add(initList.get(i));
                    } else {
                        array.add(0);
                    }
                }
            }
        } else {
            // 无初始化列表，必须指定长度
            if (size == 0) {
                throw new RuntimeException("数组声明必须指定长度或初始化列表");
            }
            array = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                array.add(0);
            }
        }
        
        currentEnv.set(name, array);
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("数组声明: " + name + " = " + array + " (大小: " + array.size() + ")");
        return null;
    }
    
    @Override
    public Object visitAssignment(HimDSLParser.AssignmentContext ctx) {
        LvalueInfo lvalueInfo = resolveLvalue(ctx.lvalue());
        Object value = visit(ctx.expression());
        String op = ctx.assignmentOp().getText();
        doAssignment(lvalueInfo, value, op);
        return null;
    }
    
    @Override
    public Object visitAssignNoSemi(HimDSLParser.AssignNoSemiContext ctx) {
        LvalueInfo lvalueInfo = resolveLvalue(ctx.lvalue());
        Object value = visit(ctx.expression());
        String op = ctx.assignmentOp().getText();
        doAssignment(lvalueInfo, value, op);
        return null;
    }
    
/**
 * 执行赋值操作，支持：
 * - 普通变量赋值
 * - 数组/列表/Map元素赋值（如 arr[0] = 5）
 * - 结构体字段赋值（如 s.name = "张三"）
 * - 结构体嵌套字段赋值（如 s.address.city = "北京"）
 * - 字符串索引赋值（如 str[0] = 'a' 或 str[0] = 65）
 * - 结构体字符串字段索引赋值（如 p.name[0] = 'X'）
 * 复合赋值（+=, -=, *=, /=, %=）仅支持数值类型，不支持字符串索引。
 */
private void doAssignment(LvalueInfo info, Object value, String op) {
    Object base = currentEnv.get(info.baseName);
    if (base == null) {
        throw new RuntimeException("变量 " + info.baseName + " 未定义");
    }

    // 1. 无 path：直接给变量赋值
    if (info.path.isEmpty()) {
        if (op.equals("=")) {
            currentEnv.set(info.baseName, value);
        } else {
            Object old = currentEnv.get(info.baseName);
            if (old == null) old = 0;
            currentEnv.set(info.baseName, computeCompound(old, value, op));
        }
        return;
    }

    // 2. 走到倒数第二层容器
    Object container = base;
    for (int i = 0; i < info.path.size() - 1; i++) {
        container = readKey(container, info.path.get(i));
        if (container == null) {
            throw new RuntimeException("路径 " + info.path.subList(0, i + 1) + " 为 null");
        }
    }

    Object lastKey = info.path.get(info.path.size() - 1);

    // 3. 字段写入
    if (lastKey instanceof String) {
        String field = (String) lastKey;
        if (container instanceof StructInstance) {
            StructInstance s = (StructInstance) container;
            if (op.equals("=")) {
                s.set(field, value);
            } else {
                Object old = s.get(field);
                if (old == null) old = 0;
                s.set(field, computeCompound(old, value, op));
            }
        } else if (container instanceof RefLike) {
            RefLike r = (RefLike) container;
            if (op.equals("=")) {
                r.setField(field, value);
            } else {
                Object old = r.getField(field);
                if (old == null) old = 0;
                r.setField(field, computeCompound(old, value, op));
            }
        } else {
            throw new RuntimeException("无法在非结构体/引用上设置字段: " + field);
        }
        return;
    }

    // 4. 索引写入
    int index = ((Number) lastKey).intValue();

    // 4.1 字符串索引：读改写（String 不可变，需要重建并写回父容器）
    if (container instanceof String) {
        if (!op.equals("=")) {
            throw new RuntimeException("字符串索引不支持复合赋值: " + op);
        }
        String s = (String) container;
        if (index < 0 || index >= s.length()) {
            throw new RuntimeException("字符串索引越界: " + index + "，长度: " + s.length());
        }
        char newChar;
        if (value instanceof Character) {
            newChar = (Character) value;
        } else if (value instanceof Number) {
            int code = ((Number) value).intValue();
            if (code < 0 || code > 0xFFFF) {
                throw new RuntimeException("无效的 Unicode 码点: " + code);
            }
            newChar = (char) code;
        } else {
            throw new RuntimeException("字符串索引赋值要求字符或整数，实际: "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        char[] chars = s.toCharArray();
        chars[index] = newChar;
        String newStr = new String(chars);

        // 写回 newStr
        if (info.path.size() == 1) {
            // s 就是 base 变量
            currentEnv.set(info.baseName, newStr);
        } else {
            // 定位到 s 的父容器与父 key
            Object parentContainer = base;
            for (int i = 0; i < info.path.size() - 2; i++) {
                parentContainer = readKey(parentContainer, info.path.get(i));
            }
            Object parentKey = info.path.get(info.path.size() - 2);
            writeKey(parentContainer, parentKey, newStr);
        }
        return;
    }

    // 4.2 List / Map / 数组索引
    if (op.equals("=")) {
        writeKey(container, index, value);
    } else {
        Object old = readKey(container, index);
        if (old == null) old = 0;
        writeKey(container, index, computeCompound(old, value, op));
    }
}
/**
 * 将字符串字面量（包含首尾引号）中的转义序列转换为实际字符。
 * 支持: \n, \t, \r, \", \\，其他保留反斜杠。
 */
private String unescapeString(String quoted) {
    if (quoted == null || quoted.length() < 2) return quoted;
    // 去掉首尾双引号
    String inner = quoted.substring(1, quoted.length() - 1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < inner.length(); i++) {
        char c = inner.charAt(i);
        if (c == '\\' && i + 1 < inner.length()) {
            char next = inner.charAt(i + 1);
            switch (next) {
                case 'n': sb.append('\n'); i++; break;
                case 't': sb.append('\t'); i++; break;
                case 'r': sb.append('\r'); i++; break;
                case '"': sb.append('"'); i++; break;
                case '\\': sb.append('\\'); i++; break;
                default: sb.append(c); break;
            }
        } else {
            sb.append(c);
        }
    }
    return sb.toString();
}   
    /**
    * 从容器中获取数组元素（支持 List/Map/原生数组）
    */
    private Object getArrayElement(Object container, List<Integer> indices) {
        Object current = container;
        for (int idx : indices) {
            if (current instanceof List) {
                current = ((List<?>) current).get(idx);
            } else if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(idx);
            } else if (current != null && current.getClass().isArray()) {
                current = java.lang.reflect.Array.get(current, idx);
            } else {
                throw new RuntimeException("无法获取数组元素");
            }
        }
        return current;
    }
    
    /**
    * 设置数组元素（支持 List/Map/原生数组）
    */
    private void setArrayElement(Object container, List<Integer> indices, Object value) {
        Object current = container;
        for (int i = 0; i < indices.size(); i++) {
            int idx = indices.get(i);
            if (i == indices.size() - 1) {
                // 最后一层，直接设置
                if (current instanceof List) {
                    ((List) current).set(idx, value);
                } else if (current instanceof Map) {
                    ((Map) current).put(idx, value);
                } else if (current != null && current.getClass().isArray()) {
                    java.lang.reflect.Array.set(current, idx, value);
                } else {
                    throw new RuntimeException("无法设置数组元素");
                }
            } else {
                // 中间层，继续向下
                if (current instanceof List) {
                    current = ((List<?>) current).get(idx);
                } else if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(idx);
                } else if (current != null && current.getClass().isArray()) {
                    current = java.lang.reflect.Array.get(current, idx);
                } else {
                    throw new RuntimeException("无法索引到中间层");
                }
            }
        }
    }
    
    /**
    * 计算复合赋值结果
    */
    private Object computeCompound(Object left, Object right, String op) {
        // 转换为数字
        double leftNum, rightNum;
        boolean leftIsNum = left instanceof Number;
        boolean rightIsNum = right instanceof Number;
        if (leftIsNum && rightIsNum) {
            leftNum = ((Number) left).doubleValue();
            rightNum = ((Number) right).doubleValue();
            double result;
            switch (op) {
                case "+=": result = leftNum + rightNum; break;
                case "-=": result = leftNum - rightNum; break;
                case "*=": result = leftNum * rightNum; break;
                case "/=": result = leftNum / rightNum; break;
                case "%=": result = leftNum % rightNum; break;
                default: throw new RuntimeException("未知复合赋值操作: " + op);
            }
            // 返回类型尽量保持原类型（整数+整数→整数）
            if (left instanceof Integer && right instanceof Integer) {
                return (int) result;
            } else {
                return result;
            }
        } else {
            // 字符串拼接（仅支持 +=）
            if (op.equals("+=")) {
                return left.toString() + right.toString();
            } else {
                throw new RuntimeException("不支持非数字类型的复合赋值: " + op);
            }
        }
    }
    
private LvalueInfo resolveLvalue(HimDSLParser.LvalueContext ctx) {
    if (ctx instanceof HimDSLParser.IdentifierLvalueContext) {
        LvalueInfo info = new LvalueInfo();
        info.baseName = ((HimDSLParser.IdentifierLvalueContext) ctx).IDENTIFIER().getText();
        return info;
    } else if (ctx instanceof HimDSLParser.ArrayLvalueContext) {
        HimDSLParser.ArrayLvalueContext actx = (HimDSLParser.ArrayLvalueContext) ctx;
        LvalueInfo info = resolveLvalue(actx.lvalue());
        Object idx = visit(actx.expression());
        if (!(idx instanceof Number)) {
            throw new RuntimeException("数组索引必须是数字，实际: "
                    + (idx == null ? "null" : idx.getClass().getSimpleName()));
        }
        info.path.add(((Number) idx).intValue());
        return info;
    } else if (ctx instanceof HimDSLParser.FieldLvalueContext) {
        HimDSLParser.FieldLvalueContext fctx = (HimDSLParser.FieldLvalueContext) ctx;
        LvalueInfo info = resolveLvalue(fctx.lvalue());
        info.path.add(fctx.IDENTIFIER().getText());
        return info;
    }
    throw new RuntimeException("未知 lvalue 类型");
}
/** 从 container 按 key 取值：key 是 String → 字段；Integer → 索引 */
private Object readKey(Object container, Object key) {
    if (key instanceof String) {
        String field = (String) key;
        if (container instanceof StructInstance) {
            return ((StructInstance) container).get(field);
        }
        if (container instanceof RefLike) {
            RefLike r = (RefLike) container;
            if (!r.hasField(field)) {
                throw new RuntimeException("字段不存在: " + field);
            }
            return r.getField(field);
        }
        throw new RuntimeException("无法在非结构体/引用上访问字段: " + field);
    }
    int index = ((Number) key).intValue();
    if (container instanceof String) {
        String s = (String) container;
        if (index < 0 || index >= s.length()) {
            throw new RuntimeException("字符串索引越界: " + index + "，长度: " + s.length());
        }
        return s.charAt(index);
    }
    if (container instanceof List) {
        List<?> list = (List<?>) container;
        if (index < 0 || index >= list.size()) {
            throw new RuntimeException("列表索引越界: " + index + "，大小: " + list.size());
        }
        return list.get(index);
    }
    if (container instanceof Map) {
        return ((Map<?, ?>) container).get(index);
    }
    if (container != null && container.getClass().isArray()) {
        return java.lang.reflect.Array.get(container, index);
    }
    throw new RuntimeException("无法索引类型: "
            + (container == null ? "null" : container.getClass().getSimpleName()));
}

/** 把 value 写入 container 的 key 位置 */
private void writeKey(Object container, Object key, Object value) {
    if (key instanceof String) {
        String field = (String) key;
        if (container instanceof StructInstance) {
            ((StructInstance) container).set(field, value);
            return;
        }
        if (container instanceof RefLike) {
            ((RefLike) container).setField(field, value);
            return;
        }
        throw new RuntimeException("无法在非结构体/引用上设置字段: " + field);
    }
    int index = ((Number) key).intValue();
    if (container instanceof List) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) container;
        if (index < 0 || index >= list.size()) {
            throw new RuntimeException("列表索引越界: " + index + "，大小: " + list.size());
        }
        list.set(index, value);
    } else if (container instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) container;
        map.put(index, value);
    } else if (container != null && container.getClass().isArray()) {
        java.lang.reflect.Array.set(container, index, value);
    } else {
        throw new RuntimeException("无法在类型上设置索引: "
                + (container == null ? "null" : container.getClass().getSimpleName()));
    }
}
    
    private static class LvalueInfo {
        String baseName;
        /** 保序路径：Integer 表示索引，String 表示字段 */
        List<Object> path = new ArrayList<>();
    }
    
    @Override
    public Object visitIfStatement(HimDSLParser.IfStatementContext ctx) {
        boolean cond = toBoolean(visit(ctx.expression()));
        if (cond) visit(ctx.statement(0));
        else if (ctx.statement().size() > 1) visit(ctx.statement(1));
        return null;
    }
    
    @Override
    public Object visitWhileStatement(HimDSLParser.WhileStatementContext ctx) {
        while (true) {
            if (!toBoolean(visit(ctx.expression()))) break;
            visit(ctx.statement());
            if (breakFlag) { breakFlag = false; break; }
            if (returnFlag) break;
            if (continueFlag) { continueFlag = false; }
        }
        return null;
    }
    
    @Override
    public Object visitDoWhileStatement(HimDSLParser.DoWhileStatementContext ctx) {
        do {
            visit(ctx.statement());
            if (breakFlag) { breakFlag = false; break; }
            if (returnFlag) break;
            if (continueFlag) { continueFlag = false; }
        } while (toBoolean(visit(ctx.expression())));
        return null;
    }
    
    @Override
    public Object visitForStatement(HimDSLParser.ForStatementContext ctx) {
        // 初始化
        if (ctx.forInit() != null) {
            HimDSLParser.ForInitContext init = ctx.forInit();
            if (init.varDeclNoSemi() != null) {
                visitVarDeclNoSemi(init.varDeclNoSemi());
                if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("  for 初始化 (varDecl)");
            } else if (init.assignNoSemi() != null) {
                visitAssignNoSemi(init.assignNoSemi());
                if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("  for 初始化 (assignNoSemi)");
            }
        } else {
            if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("  for 初始化为空");
        }
        
        // 条件
        HimDSLParser.ExpressionContext condExpr = ctx.forCondition() != null ? ctx.forCondition().expression() : null;
        
        // 更新
        HimDSLParser.ForUpdateContext updateCtx = ctx.forUpdate();
        
        while (true) {
            if (condExpr != null) {
                Object condVal = visit(condExpr);
                if (!toBoolean(condVal)) break;
            }
            
            visit(ctx.statement());
            
            if (breakFlag) {
                breakFlag = false;
                break;
            }
            if (returnFlag) break;
            if (continueFlag) {
                continueFlag = false;
            }
            
            if (updateCtx != null) {
                if (updateCtx.assignNoSemi() != null) {
                    visitAssignNoSemi(updateCtx.assignNoSemi());
                    if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("  for 更新 (assignNoSemi)");
                } else if (updateCtx.expression() != null) {
                    visit(updateCtx.expression());
                    if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("  for 更新 (expression)");
                }
            } else {
                if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().warning("  for 没有更新部分，退出循环。你可以绕过，前提是你知道自己在做什么！");
                break;
            }
        }
        return null;
    }
    @Override
    public Object visitReturnStatement(HimDSLParser.ReturnStatementContext ctx) {
        returnValue = ctx.expression() != null ? visit(ctx.expression()) : null;
        returnFlag = true;
        return null;
    }
    
    // ---------- 表达式部分 ----------
    @Override
    public Object visitExpression(HimDSLParser.ExpressionContext ctx) {
        return visit(ctx.logicalOr());
    }
    
    @Override
    public Object visitLogicalOr(HimDSLParser.LogicalOrContext ctx) {
        // 如果只有一个 logicalAnd，直接返回其值（不强制转布尔）
        if (ctx.logicalAnd().size() == 1) {
            return visit(ctx.logicalAnd(0));
        }
        // 多个 logicalAnd 才进行逻辑或运算
        boolean result = toBoolean(visit(ctx.logicalAnd(0)));
        for (int i = 1; i < ctx.logicalAnd().size(); i++) {
            result = result || toBoolean(visit(ctx.logicalAnd(i)));
        }
        return result;
    }
    
    @Override
    public Object visitLogicalAnd(HimDSLParser.LogicalAndContext ctx) {
        // 如果只有一个 equality，直接返回其值（不强制转布尔）
        if (ctx.equality().size() == 1) {
            return visit(ctx.equality(0));
        }
        // 多个 equality 才进行逻辑与运算
        boolean result = toBoolean(visit(ctx.equality(0)));
        for (int i = 1; i < ctx.equality().size(); i++) {
            result = result && toBoolean(visit(ctx.equality(i)));
        }
        return result;
    }
    
    @Override
    public Object visitEquality(HimDSLParser.EqualityContext ctx) {
        Object left = visit(ctx.relational(0));
        for (int i = 1; i < ctx.relational().size(); i++) {
            Object right = visit(ctx.relational(i));
            String op = ctx.getChild(i * 2 - 1).getText();
            if (op.equals("==")) {
                return compare(left, right);
            } else {
                return !compare(left, right);
            }
        }
        return left;
    }
    
    @Override
    public Object visitRelational(HimDSLParser.RelationalContext ctx) {
        Object left = visit(ctx.additive(0));
        for (int i = 1; i < ctx.additive().size(); i++) {
            Object right = visit(ctx.additive(i));
            String op = ctx.getChild(i * 2 - 1).getText();
            
            // 将左右操作数转为数字进行比较
            double leftNum, rightNum;
            try {
                leftNum = Double.parseDouble(left.toString());
                rightNum = Double.parseDouble(right.toString());
            } catch (NumberFormatException e) {
                // 非数字则按字符串比较
                int comp = left.toString().compareTo(right.toString());
                switch (op) {
                    case "<": return comp < 0;
                    case ">": return comp > 0;
                    case "<=": return comp <= 0;
                    case ">=": return comp >= 0;
                }
                return left;
            }
            
            switch (op) {
                case "<": return leftNum < rightNum;
                case ">": return leftNum > rightNum;
                case "<=": return leftNum <= rightNum;
                case ">=": return leftNum >= rightNum;
            }
        }
        return left;
    }
    
    @Override
    public Object visitAdditive(HimDSLParser.AdditiveContext ctx) {
        // 先处理第一个操作数
        Object left = visit(ctx.multiplicative(0));
        // 如果只有一个操作数，直接返回（可能是数字或变量）
        if (ctx.multiplicative().size() == 1) {
            return left;
        }
        // 否则进行运算
        for (int i = 1; i < ctx.multiplicative().size(); i++) {
            Object right = visit(ctx.multiplicative(i));
            String op = ctx.getChild(i * 2 - 1).getText(); // 获取运算符
            if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("加法运算: " + left + " " + op + " " + right);
            // 尝试将左右操作数转为数字
            double leftNum, rightNum;
            boolean leftIsNum = left instanceof Number;
            boolean rightIsNum = right instanceof Number;
            
            if (op.equals("+")) {
                // 如果任一是字符串，进行字符串拼接
                if (left instanceof String || right instanceof String) {
                    left = left.toString() + right.toString();
                } else if (leftIsNum && rightIsNum) {
                    leftNum = ((Number) left).doubleValue();
                    rightNum = ((Number) right).doubleValue();
                    // 根据原始类型决定返回类型
                    if (left instanceof Integer && right instanceof Integer) {
                        left = (int) (leftNum + rightNum);
                    } else {
                        left = leftNum + rightNum;
                    }
                } else {
                    // 尝试转换为数字
                    try {
                        leftNum = Double.parseDouble(left.toString());
                        rightNum = Double.parseDouble(right.toString());
                        left = leftNum + rightNum;
                    } catch (NumberFormatException e) {
                        // 否则当作字符串拼接
                        left = left.toString() + right.toString();
                    }
                }
            } else if (op.equals("-")) {
                if (leftIsNum && rightIsNum) {
                    leftNum = ((Number) left).doubleValue();
                    rightNum = ((Number) right).doubleValue();
                    if (left instanceof Integer && right instanceof Integer) {
                        left = (int) (leftNum - rightNum);
                    } else {
                        left = leftNum - rightNum;
                    }
                } else {
                    try {
                        leftNum = Double.parseDouble(left.toString());
                        rightNum = Double.parseDouble(right.toString());
                        left = leftNum - rightNum;
                    } catch (NumberFormatException e) {
                        left = 0;
                    }
                }
            }
            if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("加法结果: " + left);
        }
        return left;
    }
    
    @Override
    public Object visitMultiplicative(HimDSLParser.MultiplicativeContext ctx) {
        Object left = visit(ctx.unary(0));
        if (ctx.unary().size() == 1) {
            return left;
        }
        for (int i = 1; i < ctx.unary().size(); i++) {
            Object right = visit(ctx.unary(i));
            String op = ctx.getChild(i * 2 - 1).getText();
            if (left instanceof Number && right instanceof Number) {
                double leftNum = ((Number) left).doubleValue();
                double rightNum = ((Number) right).doubleValue();
                switch (op) {
                    case "*":
                    left = (left instanceof Integer && right instanceof Integer) ? (int) (leftNum * rightNum) : leftNum * rightNum;
                    break;
                    case "/":
                    left = leftNum / rightNum;
                    break;
                    case "%":
                    left = (left instanceof Integer && right instanceof Integer) ? (int) (leftNum % rightNum) : leftNum % rightNum;
                    break;
                }
            } else {
                try {
                    double leftNum = Double.parseDouble(left.toString());
                    double rightNum = Double.parseDouble(right.toString());
                    switch (op) {
                        case "*": left = leftNum * rightNum; break;
                        case "/": left = leftNum / rightNum; break;
                        case "%": left = leftNum % rightNum; break;
                    }
                } catch (NumberFormatException e) {
                    left = 0;
                }
            }
        }
        return left;
    }
    
@Override
public Object visitUnaryOp(HimDSLParser.UnaryOpContext ctx) {
    String op = ctx.getChild(0).getText();
    Object val = visit(ctx.unary());
    if (op.equals("!")) return !toBoolean(val);
    if (op.equals("-")) {
        if (val instanceof Number) {
            double d = ((Number) val).doubleValue();
            return (val instanceof Integer) ? (int) -d : -d;
        }
        try { return -Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0; }
    }
    if (op.equals("~")) {
        if (val instanceof Number) return ~((Number) val).intValue();
    }
    return val;
}

@Override
public Object visitPreIncrement(HimDSLParser.PreIncrementContext ctx) {
    LvalueInfo info = resolveLvalue(ctx.lvalue());
    Object newVal = incDec(readLvalue(info), +1);
    doAssignment(info, newVal, "=");
    if (HimDSLPlugin.DEBUG_MODE) {
        Bukkit.getLogger().info("前缀 ++" + info.baseName + " → " + newVal);
    }
    return newVal;
}

@Override
public Object visitPreDecrement(HimDSLParser.PreDecrementContext ctx) {
    LvalueInfo info = resolveLvalue(ctx.lvalue());
    Object newVal = incDec(readLvalue(info), -1);
    doAssignment(info, newVal, "=");
    if (HimDSLPlugin.DEBUG_MODE) {
        Bukkit.getLogger().info("前缀 --" + info.baseName + " → " + newVal);
    }
    return newVal;
}

@Override
public Object visitPostfixUnary(HimDSLParser.PostfixUnaryContext ctx) {
    return visit(ctx.postfix());
}

/** 把 primary 及其后连续 .IDENTIFIER 解析为枚举值 / 枚举类 / 普通类 */
private ClassPrefixResult tryResolveClassPrefix(HimDSLParser.PostfixContext ctx) {
    if (ctx.primary().IDENTIFIER() == null) return null;
    String primaryText = ctx.primary().getText();
    if (currentEnv.has(primaryText)) return null;
    if (ctx.getChildCount() < 2 || !".".equals(ctx.getChild(1).getText())) {
        return null;
    }

    List<String> segments = new ArrayList<>();
    List<Integer> segEnd = new ArrayList<>();
    segments.add(primaryText);
    segEnd.add(0);

    for (int i = 1; i + 1 < ctx.getChildCount(); i++) {
        ParseTree dot = ctx.getChild(i);
        if (!".".equals(dot.getText())) break;
        ParseTree nameNode = ctx.getChild(i + 1);
        if (!(nameNode instanceof TerminalNode)) break;
        Token tok = ((TerminalNode) nameNode).getSymbol();
        if (tok.getType() != HimDSLParser.IDENTIFIER) break;
        // 若该段后面紧跟 '('，说明它是方法名，不能并入类路径
        boolean followedByParen = (i + 2 < ctx.getChildCount())
                && "(".equals(ctx.getChild(i + 2).getText());
        if (followedByParen) break;
        segments.add(nameNode.getText());
        segEnd.add(i + 1);
        i += 1;
    }

    // 1) 最长优先：枚举路径（>=2 段）
    for (int len = segments.size(); len >= 2; len--) {
        String[] parts = segments.subList(0, len).toArray(new String[0]);
        Object val = EnumRegistry.resolveEnumPath(parts);
        if (val != null) return new ClassPrefixResult(val, segEnd.get(len - 1));
    }

    // 2) 最长优先：类路径（>=1 段）
    for (int len = segments.size(); len >= 1; len--) {
        String className = String.join(".", segments.subList(0, len));
        Class<?> cls = EnumRegistry.resolveClass(className);
        if (cls != null) return new ClassPrefixResult(cls, segEnd.get(len - 1));
    }
    return null;
}
@Override
public Object visitPostfix(HimDSLParser.PostfixContext ctx) {
    for (int i = 1; i < ctx.getChildCount(); i++) {
        String t = ctx.getChild(i).getText();
        if (t.equals("++") || t.equals("--")) {
            if (i != ctx.getChildCount() - 1) {
                throw new RuntimeException("++/-- 之后不能再跟其它操作");
            }
            LvalueInfo info = extractLvalueFromPostfix(ctx, i);
            if (info == null) {
                throw new RuntimeException("++/-- 只能作用于变量、字段或数组元素");
            }
            Object oldVal = readLvalue(info);
            Object newVal = incDec(oldVal, t.equals("++") ? +1 : -1);
            doAssignment(info, newVal, "=");
            if (HimDSLPlugin.DEBUG_MODE) {
                Bukkit.getLogger().info("后缀 " + info.baseName + t + " → "
                        + "旧值=" + oldVal + " 新值=" + newVal);
            }
            return oldVal;                 // 后缀返回旧值
        }
    }
    // ---------- 预扫描：枚举路径 / 类路径（最长优先） ----------
    ClassPrefixResult prefix = tryResolveClassPrefix(ctx);

    Object result;
    int startIdx;
    String funcName = null;
    String primaryText = ctx.primary().getText();

    if (prefix != null) {
        result = prefix.value;
        startIdx = prefix.consumeEndChild + 1;
        if (HimDSLPlugin.DEBUG_MODE) {
            Bukkit.getLogger().info("类前缀预扫描: " + primaryText + " → " + result
                    + "，consumeEnd=" + prefix.consumeEndChild);
        }
    } else {
        result = visit(ctx.primary());
        startIdx = 1;
        // 裸标识符若是结构体名，视为 StructDef（静态方法调用）
        if (result == null && runtime.getStruct(primaryText) != null) {
            result = runtime.getStruct(primaryText);
        }
        if (ctx.primary().IDENTIFIER() != null) {
            funcName = primaryText;
        }
    }

    String pendingMethod = null;
    StructInstance pendingInstance = null;
    StructDef pendingStructDef = null;
    Class<?> pendingStaticClass = null;   // 待调用的静态类（普通类或枚举类）

    int childCount = ctx.getChildCount();

    for (int i = startIdx; i < childCount; i++) {
        ParseTree child = ctx.getChild(i);
        String text = child.getText();

        // ---------- 数组索引 ----------
        if (text.equals("[")) {
            ParseTree exprNode = ctx.getChild(i + 1);
            Object indexObj = visit(exprNode);
            int index;
            if (indexObj instanceof Number) {
                index = ((Number) indexObj).intValue();
            } else if (indexObj instanceof Boolean) {
                index = ((Boolean) indexObj) ? 1 : 0;
            } else {
                throw new RuntimeException("数组索引必须是数字或布尔值，实际类型: " +
                        (indexObj == null ? "null" : indexObj.getClass().getSimpleName()));
            }
            result = getIndexedElement(result, index);
            i += 2; // 跳过 '[' expr ']'
        }

        // ---------- 成员访问（.identifier） ----------
        else if (text.equals(".")) {
            String identifier = ctx.getChild(i + 1).getText();

            // 任意 Bukkit Entity 都自动包装成 EntityRef 引用
            if (result instanceof Entity && !(result instanceof EntityRef)) {
                result = new EntityRef((Entity) result);
            }

            if (result instanceof RefLike) {
                RefLike ref = (RefLike) result;
                if (ref.hasField(identifier)) {
                    result = ref.getField(identifier);
                } else {
                    // 可能是方法名，等 '(' 触发
                    pendingMethod = identifier;
                }
            } else if (result instanceof StructInstance) {
                StructInstance inst = (StructInstance) result;
                if (inst.hasField(identifier)) {
                    result = inst.get(identifier);
                } else {
                    pendingInstance = inst;
                    pendingMethod = identifier;
                    result = null;
                }
            } else if (result instanceof StructDef) {
                pendingStructDef = (StructDef) result;
                pendingMethod = identifier;
                result = null;
            
            } else if (result instanceof EventWrapper) {
                ((EventWrapper) result).setPendingMethod(identifier);
            }
            // ★ 静态类：先试静态字段，否则等 '(' 触发静态方法
            else if (result instanceof Class<?>) {
                Class<?> cls = (Class<?>) result;
                Object fieldVal = readStaticFieldOrMiss(cls, identifier);
                if (fieldVal != STATIC_FIELD_MISS) {
                    result = wrapIfEntity(fieldVal);
                } else {
                    pendingStaticClass = cls;
                    pendingMethod = identifier;
                    result = null;
                }
            } else {
                pendingMethod = identifier;
            }
            i += 1;
        }

        // ---------- 函数调用 / 方法调用 ----------
        else if (text.equals("(")) {
            ParseTree argsNode = ctx.getChild(i + 1);
            List<Object> args = new ArrayList<>();
            if (argsNode instanceof HimDSLParser.ArgumentListContext) {
                HimDSLParser.ArgumentListContext argCtx =
                        (HimDSLParser.ArgumentListContext) argsNode;
                for (HimDSLParser.ExpressionContext expr : argCtx.expression()) {
                    args.add(visit(expr));
                }
            }

            // ★ 静态类调用优先（含枚举类的 values/valueOf 等）
            if (pendingStaticClass != null) {
                if (pendingStaticClass.isEnum()) {
                    result = invokeEnumStaticMethod(pendingStaticClass, pendingMethod, args);
                } else {
                    result = invokeStaticMethod(pendingStaticClass, pendingMethod, args);
                }
                pendingStaticClass = null;
                pendingMethod = null;
            }
            // ---------- 结构体实例方法 ----------
            else if (pendingInstance != null) {
                result = invokeStructMethod(pendingInstance, pendingMethod, args);
                pendingInstance = null;
                pendingMethod = null;
            }
            // ---------- 结构体静态方法 ----------
            else if (pendingStructDef != null) {
                result = invokeStaticStructMethod(pendingStructDef, pendingMethod, args);
                pendingStructDef = null;
                pendingMethod = null;
            }
            // ---------- 对象式方法调用 ----------
            else if (pendingMethod != null) {
                if (result instanceof RefLike) {
                    result = ((RefLike) result).invoke(pendingMethod, args);
                } else if (result instanceof Enum) {
                    // Java 枚举常量固有方法：name / ordinal / toString / compareTo / equals 等
                    result = invokeEnumInstanceMethod((Enum<?>) result, pendingMethod, args);
                } else if (result instanceof DSLEnumValue) {
                    // 用户枚举值方法：name / type / toString / equals
                    result = invokeDSLEnumMethod((DSLEnumValue) result, pendingMethod, args);
                } else if (BuiltinFunctions.isSupportedTarget(result)
                        && BuiltinFunctions.isBuiltin(pendingMethod)) {
                    List<Object> callArgs = new ArrayList<>();
                    callArgs.add(result);
                    callArgs.addAll(args);
                    result = BuiltinFunctions.call(pendingMethod, callArgs);
                } else {
                    // 普通对象反射调用；返回值若是 Entity 自动包成 EntityRef
                    result = invokeMethod(result, pendingMethod, args);
                }
                pendingMethod = null;
            }
             else if (result instanceof LambdaValue) {
                result = ((LambdaValue) result).apply(args);
            }
            // ---------- EventWrapper 特殊调用 ----------
            else if (result instanceof EventWrapper) {
                EventWrapper wrapper = (EventWrapper) result;
                result = wrapper.invokePendingMethod(args.toArray());
                wrapper.setPendingMethod(null);
            }
            // ---------- 普通函数调用 ----------
            else {
                if (funcName == null) {
                    throw new RuntimeException("无法调用函数，因为 primary 不是标识符");
                }
                if (BuiltinFunctions.isBuiltin(funcName)) {
                    result = BuiltinFunctions.call(funcName, args);
                } else {
                    FunctionDef func = runtime.resolveFunction(funcName,args);
                    if (func == null) {
                        throw new RuntimeException("未定义函数或者参数不匹配: " + funcName);
                    }
                    boolean oldReturnFlag = returnFlag;
                    Object oldReturnValue = returnValue;
                    returnFlag = false;
                    returnValue = null;

                    Environment oldEnv = currentEnv;
                    currentEnv = new Environment(runtime.globalEnv);
                    try {
                        for (int j = 0; j < func.parameters.size(); j++) {
                            currentEnv.set(func.parameters.get(j),
                                    j < args.size() ? args.get(j) : null);
                        }
                        visit(func.body);
                    } finally {
                        currentEnv = oldEnv;
                    }
                    result = returnValue;
                    returnFlag = oldReturnFlag;
                    returnValue = oldReturnValue;
                }
            }
            // '(' 后可能跟 ArgumentList 再跟 ')'，也可能直接是 ')'
            // 目标：让 i 指向 ')'，由 for 末尾的 i++ 推进到下一个 child
            i += (argsNode instanceof HimDSLParser.ArgumentListContext) ? 2 : 1;
        }
    }
    return result;
}
/** 枚举解析结果：value + 已消费到的 child 索引（inclusive） */
private static class EnumResolveResult {
    final Object value;
    final int consumeEndChild;
    EnumResolveResult(Object value, int consumeEndChild) {
        this.value = value;
        this.consumeEndChild = consumeEndChild;
    }
}

/**
 * 在 postfix 开头做一次"枚举路径预扫描"：
 *   - 收集 primary(IDENTIFIER) 起始的所有连续 ".IDENTIFIER"（遇到紧跟 "(" 的段停止）
 *   - 从最长到最短尝试 EnumRegistry.resolveEnumPath（>=2 段）
 *   - 否则尝试把第一段解析为枚举类（单段）
 * 首段是当前环境变量时直接跳过。
 */
private EnumResolveResult tryResolveEnumPostfix(HimDSLParser.PostfixContext ctx) {
    if (ctx.primary().IDENTIFIER() == null) return null;
    String primaryText = ctx.primary().getText();
    if (currentEnv.has(primaryText)) return null;

    List<String> segments = new ArrayList<>();
    List<Integer> segEnd = new ArrayList<>();
    segments.add(primaryText);
    segEnd.add(0);

    for (int i = 1; i + 1 < ctx.getChildCount(); i++) {
        ParseTree dot = ctx.getChild(i);
        if (!".".equals(dot.getText())) break;
        ParseTree nameNode = ctx.getChild(i + 1);
        if (!(nameNode instanceof TerminalNode)) break;
        Token tok = ((TerminalNode) nameNode).getSymbol();
        if (tok.getType() != HimDSLParser.IDENTIFIER) break;
        // 若 nameNode 后面紧跟 "("，说明它是方法名，不属于枚举路径
        boolean followedByParen = (i + 2 < ctx.getChildCount())
                && "(".equals(ctx.getChild(i + 2).getText());
        if (followedByParen) break;
        segments.add(nameNode.getText());
        segEnd.add(i + 1);
        i += 1;
    }

    // 最长匹配：>=2 段
    for (int len = segments.size(); len >= 2; len--) {
        String[] parts = segments.subList(0, len).toArray(new String[0]);
        Object val = EnumRegistry.resolveEnumPath(parts);
        if (val != null) {
            return new EnumResolveResult(val, segEnd.get(len - 1));
        }
    }

    // 单段：枚举类（供 .values() / .valueOf() 使用）
    Class<?> cls = EnumRegistry.resolveEnumClass(segments.get(0));
    if (cls != null && cls.isEnum()) {
        return new EnumResolveResult(cls, 0);
    }
    return null;
}
/** 枚举类静态方法：values / valueOf / 其他静态方法 */
private Object invokeEnumStaticMethod(Class<?> enumClass, String name, List<Object> args) {
    Object[] constants = enumClass.getEnumConstants();

    if ("values".equals(name)) {
        if (!args.isEmpty()) throw new RuntimeException("values() 不接受参数");
        return new ArrayList<>(Arrays.asList(constants));
    }
    if ("valueOf".equals(name)) {
        if (args.size() != 1) throw new RuntimeException("valueOf() 需要一个字符串参数");
        String n = args.get(0).toString();
        for (Object c : constants) {
            if (((Enum<?>) c).name().equals(n)) return c;
        }
        for (Object c : constants) {
            if (((Enum<?>) c).name().equalsIgnoreCase(n)) return c;
        }
        return null;
    }
    if ("name".equals(name) || "ordinal".equals(name) || "toString".equals(name)) {
        throw new RuntimeException(name + "() 只能在枚举常量上调用");
    }
    // 其他静态方法：反射
// 其他静态方法：反射
for (Method m : enumClass.getMethods()) {
    if (!m.getName().equals(name)) continue;
    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
    if (!canConvertArgs(args, m.getParameterTypes(), m.isVarArgs())) continue;   // ★
    try {
        Object[] converted = convertArgs(args, m.getParameterTypes(), m.isVarArgs()); // ★
        m.setAccessible(true);
        return wrapIfEntity(m.invoke(null, converted));
    } catch (Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        throw new RuntimeException("调用枚举静态方法 " + name + " 失败: "
                + cause.getMessage(), cause);
    }
}
throw new RuntimeException("枚举类 " + enumClass.getSimpleName() + " 无静态方法: " + name);
}

/** Java 枚举常量固有方法 */
private Object invokeEnumInstanceMethod(Enum<?> e, String name, List<Object> args) {
    switch (name) {
        case "name":              return e.name();
        case "ordinal":           return e.ordinal();
        case "toString":          return e.toString();
        case "getDeclaringClass": return e.getDeclaringClass();
        case "hashCode":          return e.hashCode();
        case "equals": {
            if (args.size() != 1) throw new RuntimeException("equals() 需要一个参数");
            Object other = args.get(0);
            if (other instanceof String) {
                return e.name().equals(other) || e.toString().equals(other);
            }
            return e.equals(other);
        }
        case "compareTo": {
            if (args.size() != 1) throw new RuntimeException("compareTo() 需要一个参数");
            Object other = args.get(0);
            Enum<?> otherEnum = null;
            if (other instanceof Enum) {
                otherEnum = (Enum<?>) other;
            } else if (other instanceof String) {
                String n = (String) other;
                for (Object c : e.getDeclaringClass().getEnumConstants()) {
                    if (((Enum<?>) c).name().equals(n)
                            || ((Enum<?>) c).name().equalsIgnoreCase(n)) {
                        otherEnum = (Enum<?>) c;
                        break;
                    }
                }
            }
            if (otherEnum == null) {
                throw new RuntimeException("compareTo() 参数必须是本枚举的常量或名称: " + other);
            }
            return compareEnums(e, otherEnum);
        }
        default:
            return invokeMethod(e, name, args);
    }
}
@SuppressWarnings({"unchecked", "rawtypes"})
private int compareEnums(Enum a, Enum b) {
    return a.compareTo(b);
}
/** 用户枚举（DSLEnumValue）支持的方法 */
private Object invokeDSLEnumMethod(DSLEnumValue e, String name, List<Object> args) {
    switch (name) {
        case "name":     return e.constant;
        case "type":     return e.enumType;
        case "toString": return e.toString();
        case "equals": {
            if (args.size() != 1) throw new RuntimeException("equals() 需要一个参数");
            Object o = args.get(0);
            if (o instanceof String) return e.constant.equals(o);
            return e.equals(o);
        }
        default:
            throw new RuntimeException("用户枚举不支持方法: " + name);
    }
}

/** String → 枚举（忽略大小写兜底） */
private Object tryConvertToEnum(Class<?> enumType, String name) {
    if (enumType == null || !enumType.isEnum()) return null;
    for (Object c : enumType.getEnumConstants()) {
        if (((Enum<?>) c).name().equals(name)) return c;
    }
    for (Object c : enumType.getEnumConstants()) {
        if (((Enum<?>) c).name().equalsIgnoreCase(name)) return c;
    }
    return null;
}
@Override
public Object visitStructDef(HimDSLParser.StructDefContext ctx) {
    String name = ctx.IDENTIFIER().getText();
    if (runtime.getStruct(name) != null) {
        throw new RuntimeException("结构体 " + name + " 已存在");
    }

    List<StructDef.StructField> fields = new ArrayList<>();
    List<StructDef.StructFunction> functions = new ArrayList<>();

    for (HimDSLParser.StructMemberContext memberCtx : ctx.structMember()) {
        if (memberCtx instanceof HimDSLParser.StructFieldMemberContext) {
            HimDSLParser.StructFieldMemberContext fm =
                    (HimDSLParser.StructFieldMemberContext) memberCtx;
            String type = fm.type().getText();
            String fieldName = fm.fieldName().getText();
            fields.add(new StructDef.StructField(type, fieldName));
        } else if (memberCtx instanceof HimDSLParser.StructMethodMemberContext) {
            HimDSLParser.StructMethodMemberContext mm =
                    (HimDSLParser.StructMethodMemberContext) memberCtx;
            String funcName = mm.IDENTIFIER().getText();
            List<String> params = new ArrayList<>();
            if (mm.paramList() != null) {
                for (HimDSLParser.ParamContext p : mm.paramList().param()) {
                    if (p instanceof HimDSLParser.NormalParamContext) {
                        params.add(((HimDSLParser.NormalParamContext) p).IDENTIFIER().getText());
                    } else {
                        throw new RuntimeException(
                            "结构体方法不支持事件参数: " + funcName);
                    }
                }
            }
            boolean isVoid = mm.returnType().getText().equals("void");
            boolean isStatic = mm.STATIC() != null;
            functions.add(new StructDef.StructFunction(
                    funcName, params, mm.block(), isVoid, isStatic));
        }
    }

    runtime.registerStruct(new StructDef(name, fields, functions));
    if (HimDSLPlugin.DEBUG_MODE) {
        Bukkit.getLogger().info("注册结构体: " + name
                + "，字段 " + fields.size() + "，方法 " + functions.size());
    }
    return null;
}
    @Override
    public Object visitSelector(HimDSLParser.SelectorContext ctx) {
        String selectorText = ctx.getText();
        Location origin = (Location) currentEnv.get("__origin__");
        Object result = SelectorParser.parse(selectorText, currentEnv, origin);
        if (result == null) {
            throw new RuntimeException("选择器 " + selectorText + " 没有匹配到任何实体");
        }
        return result;
    }
    @Override
    public Object visitPrimary(HimDSLParser.PrimaryContext ctx) {
        // 优先处理 NUMBER
        if (ctx.NUMBER() != null) {
            String num = ctx.NUMBER().getText();
            if (num.contains(".")) {
                double val = Double.parseDouble(num);
                if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("解析数字 (double): " + val);
                return val;
            } else {
                int val = Integer.parseInt(num);
                if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("解析数字 (int): " + val);
                return val;
            }
        }
        
if (ctx.STRING() != null) {
    String unquoted = unescapeString(ctx.STRING().getText());
    if (HimDSLPlugin.DEBUG_MODE) Bukkit.getLogger().info("ANTLR 解析字符串: " + unquoted);
    return unquoted;
}
        
        // 手动解析字符串（应急）
        String text = ctx.getText();
        if (text.startsWith("\"") && text.endsWith("\"")) {
            String unquoted = text.substring(1, text.length() - 1);
            unquoted = unquoted.replace("\\\"", "\"").replace("\\\\", "\\");
            if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("手动解析字符串: " + unquoted);
            return unquoted;
        }
        
        if (ctx.TRUE() != null) return true;
        if (ctx.FALSE() != null) return false;
        if (ctx.NULL() != null) return null;
        
        if (ctx.IDENTIFIER() != null) {
            String name = ctx.IDENTIFIER().getText();
            Object val = currentEnv.get(name);
            if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("读取变量 " + name + " = " + val);
            return val;
        }
        if (ctx.CHAR() != null) {
            String raw = ctx.CHAR().getText();
            // 去掉首尾单引号，处理转义
            String charContent = raw.substring(1, raw.length() - 1);
            // 处理转义字符（如 \n, \t, \\, \' 等）
            if (charContent.startsWith("\\")) {
                switch (charContent) {
                    case "\\n": return '\n';
                    case "\\t": return '\t';
                    case "\\r": return '\r';
                    case "\\'": return '\'';
                    case "\\\\": return '\\';
                    default: return charContent.charAt(0); // 其他转义序列直接取字符（如 \a）
                }
            }
            return charContent.charAt(0); // 返回 Character 对象
        }
        if (ctx.placeholder() != null) return visit(ctx.placeholder());
        if (ctx.expression() != null) return visit(ctx.expression());
        if (ctx.arrayInitializer() != null) {
            List<Object> list = new ArrayList<>();
            for (HimDSLParser.ExpressionContext expr : ctx.arrayInitializer().expression()) {
                list.add(visit(expr));
            }
            return list;
        }
        if (ctx.selector() != null) {
            return visit(ctx.selector());
        }
        if (ctx.newExpr() != null) return visitNewExpr(ctx.newExpr());
        if (ctx.lambdaExpr() != null) return visit(ctx.lambdaExpr());
        Bukkit.getLogger().warning("未匹配的 primary: " + ctx.getText());
        return null;
    }
@Override
public Object visitNewExpr(HimDSLParser.NewExprContext ctx) {
    String className = ctx.classRef().getText();
    List<Object> args = new ArrayList<>();
    if (ctx.argumentList() != null) {
        for (HimDSLParser.ExpressionContext expr : ctx.argumentList().expression()) {
            args.add(visit(expr));
        }
    }
        // ★ GUI 拦截
    if ("GUI".equals(className)) {
        if (args.isEmpty() || !(args.get(0) instanceof Number)) {
            throw new RuntimeException("new GUI(rows[, title]) 需要行数（1~6）");
        }
        int rows = ((Number) args.get(0)).intValue();
        String title = (args.size() >= 2 && args.get(1) != null) ? args.get(1).toString() : "";
        if (HimDSLPlugin.DEBUG_MODE)
            Bukkit.getLogger().info("new GUI → rows=" + rows + ", title=" + title);
        return new GuiRef(rows, title, runtime);
    }
    // ★ GUISlot 拦截
    if ("GUISlot".equals(className)) {
        if (args.isEmpty()) return new GuiSlotRef();
        return new GuiSlotRef(args.get(0));
    }

    Class<?> clazz = EnumRegistry.resolveClass(className);
    if (clazz == null) {
        throw new RuntimeException("找不到类: " + className);
    }

    // ★ 函数式接口 + lambda → JDK Proxy
    if (clazz.isInterface()) {
        if (args.size() == 1 && args.get(0) instanceof LambdaValue) {
            if (LambdaValue.findSAM(clazz) == null) {
                throw new RuntimeException(clazz.getName() + " 不是函数式接口");
            }
            return ((LambdaValue) args.get(0)).adaptTo(clazz);
        }
        throw new RuntimeException("不能 new 接口 " + clazz.getName()
                + "（除非传 lambda 给函数式接口）");
    }

    // 常规构造器
    Constructor<?> best = null;
    for (Constructor<?> c : clazz.getConstructors()) {
        if (!canConvertArgs(args, c.getParameterTypes(), c.isVarArgs())) continue;
        best = c;
        break;
    }
    if (best == null) {
        throw new RuntimeException("找不到匹配的构造器: " + className
                + " / " + args.size() + " 个参数");
    }

    Object[] converted = convertArgs(args, best.getParameterTypes(), best.isVarArgs());
    try {
        best.setAccessible(true);
        Object instance = best.newInstance(converted);
        if (HimDSLPlugin.DEBUG_MODE) {
            Bukkit.getLogger().info("new " + className + " → " + instance);
        }
        return wrapIfEntity(instance);
    } catch (Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        throw new RuntimeException("实例化 " + className + " 失败: "
                + cause.getMessage(), cause);
    }
}
/** 反射结果若是 Bukkit Entity，统一包成 EntityRef */
private Object wrapIfEntity(Object obj) {
    if (obj == null) return null;
    if (obj instanceof EntityRef) return obj;
    if (obj instanceof Entity) return new EntityRef((Entity) obj);
    return obj;
}
@Override
public Object visitSingleParamLambda(HimDSLParser.SingleParamLambdaContext ctx) {
    List<String> params = new ArrayList<>();
    List<String> types = new ArrayList<>();
    params.add(ctx.IDENTIFIER().getText());
    types.add(null);
    return buildLambda(params, types, ctx.lambdaBody());
}

@Override
public Object visitParenLambda(HimDSLParser.ParenLambdaContext ctx) {
    List<String> params = new ArrayList<>();
    List<String> types = new ArrayList<>();
    if (ctx.lambdaParamList() != null) {
        for (HimDSLParser.LambdaParamContext p : ctx.lambdaParamList().lambdaParam()) {
            params.add(p.IDENTIFIER().getText());
            types.add(p.type() != null ? p.type().getText() : null);
        }
    }
    return buildLambda(params, types, ctx.lambdaBody());
}

private LambdaValue buildLambda(List<String> params, List<String> types,
                                HimDSLParser.LambdaBodyContext bodyCtx) {
    boolean isExpr = bodyCtx instanceof HimDSLParser.ExprLambdaBodyContext;
    ParseTree bodyTree = isExpr
            ? ((HimDSLParser.ExprLambdaBodyContext) bodyCtx).expression()
            : ((HimDSLParser.BlockLambdaBodyContext) bodyCtx).block();
    if (HimDSLPlugin.DEBUG_MODE) {
        Bukkit.getLogger().info("构造 lambda，参数: " + params + "，体类型: "
                + (isExpr ? "表达式" : "块"));
    }
    return new LambdaValue(params, types, bodyTree, isExpr, currentEnv, runtime);
}
/** 判断 args 能否转换成 types（用于构造器 / 方法重载筛选） */
/** 判断 args 能否转换成 types（含 varargs） */
/**
 * 判断 args 能否转换成 types。
 * 固定参数：个数精确匹配、逐个可转。
 * varargs：最后一个是 T[]，允许
 *   A) 剩余 1 个参数本身就是 T[] / List / 空 → 直接当数组
 *   B) 剩余 N 个参数逐个可转成 T → 打包成 T[N]
 */
/**
 * 判断 args 能否转换成 types。
 * 固定参数：个数精确匹配、逐个可转。
 * varargs：最后一个是 T[]，允许
 *   A) 剩余 1 个参数本身就是 T[] / List / 空 → 直接当数组
 *   B) 剩余 N 个参数逐个可转成 T → 打包成 T[N]
 */
private boolean canConvertArgs(List<Object> args, Class<?>[] types, boolean isVarArgs) {
    if (!isVarArgs) {
        if (args.size() != types.length) return false;
        for (int i = 0; i < types.length; i++) {
            if (!canConvertOne(args.get(i), types[i])) return false;
        }
        return true;
    }
    int fixed = types.length - 1;
    if (args.size() < fixed) return false;
    for (int i = 0; i < fixed; i++) {
        if (!canConvertOne(args.get(i), types[i])) return false;
    }
    Class<?> arrayType = types[fixed];
    Class<?> elemType  = arrayType.getComponentType();

    // 情形 A：剩余恰 1 个且本身是数组/List/null
    if (args.size() == types.length) {
        Object last = args.get(fixed);
        if (last == null) return true;
        if (arrayType.isAssignableFrom(last.getClass())) return true;
        if (last.getClass().isArray()) return true;
        if (last instanceof List) return true;
    }
    // 情形 B：剩余 N 个逐元素判断
    for (int i = fixed; i < args.size(); i++) {
        if (!canConvertOne(args.get(i), elemType)) return false;
    }
    return true;
}
/** 单参数可转换性判断 */
/** 单参数可转换性判断 */
private boolean canConvertOne(Object arg, Class<?> t) {
    if (arg == null) return !t.isPrimitive();
    // lambda → 函数式接口
    // lambda → 函数式接口
if (arg instanceof LambdaValue && t.isInterface()) {
    java.lang.reflect.Method sam = LambdaValue.findSAM(t);
    if (sam != null) {
        LambdaValue lv = (LambdaValue) arg;
        // SAM 方法声明的参数个数必须与 lambda 形参个数一致
        if (sam.getParameterCount() == lv.paramNames.size()) {
            return true;
        }
    }
}
    if (t.isAssignableFrom(arg.getClass())) return true;
    if (Entity.class.isAssignableFrom(t) && arg instanceof EntityRef) return true;
    if (t.isPrimitive() && isPrimitiveMatch(t, arg.getClass())) return true;
    if (t.isEnum() && arg instanceof String
            && tryConvertToEnum(t, (String) arg) != null) return true;
    if (t == String.class) return true;
    if (t.isArray() && (arg instanceof List || arg.getClass().isArray())) return true;
    return false;
}
    @Override
    public Object visitPlaceholder(HimDSLParser.PlaceholderContext ctx) {
        String type = ctx.placeholderType().getText();
        List<Object> args = new ArrayList<>();
        if (ctx.argumentList() != null) {
            List<HimDSLParser.ExpressionContext> exprs = ctx.argumentList().expression();
            for (int i = 0; i < exprs.size(); i++) {
                HimDSLParser.ExpressionContext expr = exprs.get(i);
                String text = expr.getText();
                if (i == 0) {
                    // 第一个参数总是作为字符串（变量名）
                    args.add(text);
                } else {
                    // 后续参数：如果是单一标识符且当前环境中有该变量，则求值；否则取文本
                    if (expr.getChildCount() == 1 && text.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        if (currentEnv.has(text)) {
                            args.add(visit(expr));  // 变量 → 求值
                        } else {
                            args.add(text);         // 未定义 → 作为字符串常量
                        }
                    } else {
                        args.add(visit(expr));       // 复杂表达式 → 正常求值
                    }
                }
            }
        }
        return runtime.resolver.resolve(type, args, currentEnv);
    }
    @Override
    public Object visitFunctionCall(HimDSLParser.FunctionCallContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("        >>> visitFunctionCall: " + name);
        List<Object> args = new ArrayList<>();
        if (ctx.argumentList() != null) {
            for (HimDSLParser.ExpressionContext expr : ctx.argumentList().expression()) {
                String text = expr.getText();
                if (text.startsWith("\"") && text.endsWith("\"")) {
    String unquoted = unescapeString(text);
    if (HimDSLPlugin.DEBUG_MODE) Bukkit.getLogger().info("            直接提取字符串参数: " + unquoted);
    args.add(unquoted);
} else {
                    args.add(visit(expr));
                }
            }
        }
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("           参数个数: " + args.size() + ", 参数值: " + args);
        if (BuiltinFunctions.isBuiltin(name)) {
            if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("           调用内置函数: " + name);
            Object result = BuiltinFunctions.call(name, args);
            if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("           内置函数返回: " + result);
            return result;
        }
        
        // 调用用户函数
        FunctionDef func = runtime.resolveFunction(name,args);
        if (func == null) {
            throw new RuntimeException("未定义函数: " + name);
        }
        // 保存当前的 return 状态
        boolean oldReturnFlag = returnFlag;
        Object oldReturnValue = returnValue;
        // 重置，以免影响函数执行
        returnFlag = false;
        returnValue = null;
        
        Environment oldEnv = currentEnv;
        currentEnv = new Environment(runtime.globalEnv);
        try {
            for (int i = 0; i < func.parameters.size(); i++) {
                currentEnv.set(func.parameters.get(i), i < args.size() ? args.get(i) : null);
            }
            visit(func.body);
        } finally {
            currentEnv = oldEnv;
        }
        // 获取函数返回值（在恢复前）
        Object result = returnValue;
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("result:"+result);
        // 恢复之前的 return 状态
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("returnFlag1:"+returnFlag);
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("returnValue1:"+returnValue);
        returnFlag = oldReturnFlag;
        returnValue = oldReturnValue;
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("returnFlag2:"+returnFlag);
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("returnValue2:"+returnValue);
        
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("           用户函数返回: " + result);
        return result;
    }
    
    // ---------- 辅助方法 ----------
    private boolean toBoolean(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number) return ((Number) obj).doubleValue() != 0;
        return true;
    }
    
private boolean compare(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    if (a instanceof Number && b instanceof Number)
        return ((Number) a).doubleValue() == ((Number) b).doubleValue();

    // ---- 用户枚举 <-> 字符串 ----
    if (a instanceof DSLEnumValue && b instanceof String) {
        return ((DSLEnumValue) a).constant.equals(b);
    }
    if (a instanceof String && b instanceof DSLEnumValue) {
        return a.equals(((DSLEnumValue) b).constant);
    }

    return a.equals(b);
}
    public void setEnvironment(Environment env) {
        this.currentEnv = env;
    }
    // 处理索引访问（数组、List、Map）
    private Object getIndexedElement(Object target, int index) {
        if (target instanceof String) {
            String str = (String) target;
            if (index >= 0 && index < str.length()) {
                return str.charAt(index); // 返回字符
            } else {
                throw new RuntimeException("字符串索引越界: " + index + "，长度: " + str.length());
            }
        }else if (target instanceof List) {
            List<?> list = (List<?>) target;
            if (index >= 0 && index < list.size()) {
                return list.get(index);
            } else {
                throw new RuntimeException("列表索引越界: " + index + "，大小: " + list.size());
            }
        } else if (target instanceof Map) {
            return ((Map<?, ?>) target).get(index);
        } else if (target != null && target.getClass().isArray()) {
            try {
                return java.lang.reflect.Array.get(target, index);
            } catch (Exception e) {
                throw new RuntimeException("数组访问失败: " + e.getMessage());
            }
        } else {
            // 尝试从环境变量获取数组
            Object array = currentEnv.get(target.toString());
            if (array instanceof List) {
                List<?> list = (List<?>) array;
                if (index >= 0 && index < list.size()) {
                    return list.get(index);
                } else {
                    throw new RuntimeException("数组索引越界: " + index + "，大小: " + list.size());
                }
            } else if (array != null && array.getClass().isArray()) {
                try {
                    return java.lang.reflect.Array.get(array, index);
                } catch (Exception e) {
                    throw new RuntimeException("数组访问失败: " + e.getMessage());
                }
            } else {
                throw new RuntimeException("不支持的类型进行数组访问: " +
                (target == null ? "null" : target.getClass().getSimpleName()));
            }
        }
    }
    
    // 反射调用对象方法（支持自动拆箱）
private Object invokeMethod(Object target, String methodName, List<Object> args) {
    if (target == null) throw new RuntimeException("无法调用null对象的方法");

    Method best = null;
    for (Method m : target.getClass().getMethods()) {
        if (!m.getName().equals(methodName)) continue;
        if (!canConvertArgs(args, m.getParameterTypes(), m.isVarArgs())) continue;
        best = m;
        break;
    }
    if (best == null) {
        throw new RuntimeException("找不到匹配的方法: " + methodName
                + " with " + args.size() + " parameters");
    }
    Object[] converted = convertArgs(args, best.getParameterTypes(), best.isVarArgs());
    try {
        best.setAccessible(true);
        return wrapIfEntity(best.invoke(target, converted));
    } catch (Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        throw new RuntimeException("调用方法 " + methodName + " 失败: "
                + cause.getMessage(), cause);
    }
}
    /**
 * 调用实例成员方法。
 * 策略：把实例字段“拷贝进”方法环境（作为局部变量），方法执行完后再“拷贝回”实例。
 * 这样不同实例完全隔离，且无需 this。
 */
private Object invokeStructMethod(StructInstance inst, String methodName, List<Object> args) {
    StructDef def = runtime.getStruct(inst.getStructName());
    if (def == null) {
        throw new RuntimeException("未注册的结构体: " + inst.getStructName());
    }
    StructDef.StructFunction func = def.findFunction(methodName);
    if (func == null) {
        throw new RuntimeException("结构体 " + def.name + " 没有方法: " + methodName);
    }
    if (func.isStatic) {
        throw new RuntimeException("方法 " + def.name + "." + methodName
                + " 是静态的，请用 " + def.name + "." + methodName + "() 调用");
    }

    Environment methodEnv = new Environment(runtime.globalEnv);
    // 字段作为局部变量先落地，防止与全局同名变量串通
    for (StructDef.StructField f : def.fields) {
        methodEnv.declare(f.name, inst.get(f.name));
    }

    boolean oldReturnFlag = returnFlag;
    Object oldReturnValue = returnValue;
    returnFlag = false;
    returnValue = null;

    Environment oldEnv = currentEnv;
    currentEnv = methodEnv;
    try {
        for (int i = 0; i < func.parameters.size(); i++) {
            methodEnv.declare(func.parameters.get(i),
                    i < args.size() ? args.get(i) : null);
        }
        visit(func.body);
    } finally {
        currentEnv = oldEnv;
        // 写回字段（跳过与参数同名的字段，避免污染）
        Set<String> paramNames = new HashSet<>(func.parameters);
        for (StructDef.StructField f : def.fields) {
            if (paramNames.contains(f.name)) continue;
            inst.set(f.name, methodEnv.get(f.name));
        }
    }

    Object rv = returnValue;
    returnFlag = oldReturnFlag;
    returnValue = oldReturnValue;
    return rv;
}

/** 调用静态方法。静态方法看不到实例字段，只能访问全局变量与参数。 */
private Object invokeStaticStructMethod(StructDef def, String methodName, List<Object> args) {
    StructDef.StructFunction func = def.findFunction(methodName);
    if (func == null) {
        throw new RuntimeException("结构体 " + def.name + " 没有方法: " + methodName);
    }
    if (!func.isStatic) {
        throw new RuntimeException("方法 " + def.name + "." + methodName
                + " 不是静态的，请通过实例调用");
    }

    Environment methodEnv = new Environment(runtime.globalEnv);

    boolean oldReturnFlag = returnFlag;
    Object oldReturnValue = returnValue;
    returnFlag = false;
    returnValue = null;

    Environment oldEnv = currentEnv;
    currentEnv = methodEnv;
    try {
        for (int i = 0; i < func.parameters.size(); i++) {
            methodEnv.declare(func.parameters.get(i),
                    i < args.size() ? args.get(i) : null);
        }
        visit(func.body);
    } finally {
        currentEnv = oldEnv;
    }

    Object rv = returnValue;
    returnFlag = oldReturnFlag;
    returnValue = oldReturnValue;
    return rv;
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
    
private Object[] convertArgs(List<Object> args, Class<?>[] targetTypes, boolean isVarArgs) {
    if (!isVarArgs) {
        Object[] out = new Object[targetTypes.length];
        for (int i = 0; i < targetTypes.length; i++) {
            out[i] = convertOne(args.get(i), targetTypes[i]);
        }
        return out;
    }
    int fixed = targetTypes.length - 1;
    Object[] out = new Object[targetTypes.length];
    for (int i = 0; i < fixed; i++) {
        out[i] = convertOne(args.get(i), targetTypes[i]);
    }
    Class<?> arrayType = targetTypes[fixed];
    Class<?> elemType  = arrayType.getComponentType();

    // 情形 A：剩余 1 个且本身是数组/List
    if (args.size() == targetTypes.length) {
        Object last = args.get(fixed);
        if (last == null) {
            out[fixed] = java.lang.reflect.Array.newInstance(elemType, 0);
            return out;
        }
        if (arrayType.isAssignableFrom(last.getClass())) {
            out[fixed] = last;
            return out;
        }
        if (last.getClass().isArray()) {
            out[fixed] = last;
            return out;
        }
        if (last instanceof List) {
            List<?> list = (List<?>) last;
            Object arr = java.lang.reflect.Array.newInstance(elemType, list.size());
            for (int i = 0; i < list.size(); i++) {
                java.lang.reflect.Array.set(arr, i, convertOne(list.get(i), elemType));
            }
            out[fixed] = arr;
            return out;
        }
    }
    // 情形 B：剩余 N 个打包成 T[N]
    int count = args.size() - fixed;
    Object arr = java.lang.reflect.Array.newInstance(elemType, count);
    for (int i = 0; i < count; i++) {
        java.lang.reflect.Array.set(arr, i, convertOne(args.get(fixed + i), elemType));
    }
    out[fixed] = arr;
    return out;
}
/** 单参数转换 */
/** 单参数转换 */
private Object convertOne(Object arg, Class<?> target) {
    if (arg == null) return null;
    // lambda → 函数式接口
// lambda → 函数式接口
if (arg instanceof LambdaValue && target.isInterface()) {
    java.lang.reflect.Method sam = LambdaValue.findSAM(target);
    if (sam != null) {
        LambdaValue lv = (LambdaValue) arg;
        if (sam.getParameterCount() == lv.paramNames.size()) {
            return lv.adaptTo(target);
        }
    }
}
    if (target.isAssignableFrom(arg.getClass())) return arg;
    if (Entity.class.isAssignableFrom(target) && arg instanceof EntityRef)
        return ((EntityRef) arg).getEntity();
    if (target == int.class)     return ((Number) arg).intValue();
    if (target == boolean.class) return arg;
    if (target == double.class)  return ((Number) arg).doubleValue();
    if (target == long.class)    return ((Number) arg).longValue();
    if (target == float.class)   return ((Number) arg).floatValue();
    if (target == short.class)   return ((Number) arg).shortValue();
    if (target == byte.class)    return ((Number) arg).byteValue();
    if (target == char.class)    return arg.toString().charAt(0);
    if (target.isEnum() && arg instanceof String) {
        Object v = tryConvertToEnum(target, (String) arg);
        return (v != null) ? v : arg;
    }
    if (target == String.class) return arg.toString();
    return arg;
}
private Object readStaticFieldOrMiss(Class<?> cls, String name) {
    try {
        java.lang.reflect.Field f = cls.getField(name);
        if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) return STATIC_FIELD_MISS;
        f.setAccessible(true);
        return f.get(null);
    } catch (Throwable t) {
        return STATIC_FIELD_MISS;
    }
}

private Object invokeStaticMethod(Class<?> clazz, String methodName, List<Object> args) {
    Method best = null;
    for (Method m : clazz.getMethods()) {
        if (!m.getName().equals(methodName)) continue;
        if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
        if (!canConvertArgs(args, m.getParameterTypes(), m.isVarArgs())) continue;
        best = m;
        break;
    }
    if (best == null) {
        throw new RuntimeException("找不到静态方法: " + clazz.getSimpleName()
                + "." + methodName + " / " + args.size() + " 个参数");
    }
    try {
        Object[] converted = convertArgs(args, best.getParameterTypes(), best.isVarArgs());
        best.setAccessible(true);
        return wrapIfEntity(best.invoke(null, converted));
    } catch (Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        throw new RuntimeException("调用静态方法 " + methodName + " 失败: "
                + cause.getMessage(), cause);
    }
}
/** 供 LambdaValue 调用；表达式体直接求值，块体隔离 return 状态 */
public Object invokeLambdaBody(ParseTree body, boolean isExpr) {
    if (isExpr) return visit(body);

    boolean oldReturnFlag = returnFlag;
    Object oldReturnValue = returnValue;
    returnFlag = false;
    returnValue = null;
    try {
        visit(body);
        return returnValue;
    } finally {
        returnFlag = oldReturnFlag;
        returnValue = oldReturnValue;
    }
}
/** 找出接口的唯一抽象方法（SAM），非函数式接口返回 null */
private Method findSAM(Class<?> iface) {
    Method sam = null;
    for (Method m : iface.getMethods()) {
        if (m.getDeclaringClass() == Object.class) continue;
        if (m.isDefault()) continue;
        if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
        if (!java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
        if (sam != null) return null;
        sam = m;
    }
    return sam;
}

/** 用 Proxy 把 LambdaValue 适配成函数式接口实例 */
private Object createFunctionalProxy(Class<?> iface, Method sam, LambdaValue lambda) {
    return java.lang.reflect.Proxy.newProxyInstance(
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
            List<Object> argList = args == null ? new ArrayList<>()
                    : new ArrayList<>(java.util.Arrays.asList(args));
            Object result = lambda.apply(argList);
            return convertReturn(result, method.getReturnType());
        });
}

/** lambda 返回值 → Java 方法声明返回类型 */
private Object convertReturn(Object result, Class<?> target) {
    if (target == void.class || target == Void.class) return null;
    if (result == null) return null;
    if (target.isAssignableFrom(result.getClass())) return result;
    if (target == boolean.class || target == Boolean.class) return toBoolean(result);
    if (target == int.class     || target == Integer.class) return ((Number) result).intValue();
    if (target == long.class    || target == Long.class)    return ((Number) result).longValue();
    if (target == double.class  || target == Double.class)  return ((Number) result).doubleValue();
    if (target == float.class   || target == Float.class)   return ((Number) result).floatValue();
    if (target == short.class   || target == Short.class)   return ((Number) result).shortValue();
    if (target == byte.class    || target == Byte.class)    return ((Number) result).byteValue();
    if (target == char.class    || target == Character.class) return result.toString().charAt(0);
    if (target == String.class) return result.toString();
    if (target.isEnum() && result instanceof String) {
        Object v = tryConvertToEnum(target, (String) result);
        if (v != null) return v;
    }
    return result;
}
/** 从 postfix 的 primary 到 endExclusive 之间抽取出 lvalue 路径 */
private LvalueInfo extractLvalueFromPostfix(HimDSLParser.PostfixContext ctx, int endExclusive) {
    if (ctx.primary().IDENTIFIER() == null) return null;
    LvalueInfo info = new LvalueInfo();
    info.baseName = ctx.primary().IDENTIFIER().getText();
    int i = 1;
    while (i < endExclusive) {
        String t = ctx.getChild(i).getText();
        if (t.equals("[")) {
            Object idx = visit(ctx.getChild(i + 1));
            if (!(idx instanceof Number)) return null;
            info.path.add(((Number) idx).intValue());
            i += 3;
        } else if (t.equals(".")) {
            info.path.add(ctx.getChild(i + 1).getText());
            i += 2;
        } else {
            return null;    // 遇到 '(' 等非 lvalue 结构
        }
    }
    return info;
}

/** 读取 lvalue 的当前值（不写回） */
private Object readLvalue(LvalueInfo info) {
    Object current = currentEnv.get(info.baseName);
    if (current == null) {
        throw new RuntimeException("变量 " + info.baseName + " 未定义或为 null");
    }
    for (Object key : info.path) {
        current = readKey(current, key);
    }
    return current;
}

/** 数值自增/自减；null 视为 0 */
private Object incDec(Object val, int delta) {
    if (val == null) return delta;
    if (val instanceof Integer) return (Integer) val + delta;
    if (val instanceof Long)    return (Long) val + delta;
    if (val instanceof Double)  return (Double) val + delta;
    if (val instanceof Float)   return (Float) val + delta;
    if (val instanceof Short)   return (short) ((Short) val + delta);
    if (val instanceof Byte)    return (byte) ((Byte) val + delta);
    if (val instanceof Character) return (char) (((Character) val) + delta);
    if (val instanceof Number)  return ((Number) val).doubleValue() + delta;
    throw new RuntimeException("++/-- 不支持类型: " + val.getClass().getSimpleName());
}
/** 类前缀 / 枚举路径解析结果：value + 已消费到的 child 索引（inclusive） */
private static class ClassPrefixResult {
    final Object value;
    final int consumeEndChild;
    ClassPrefixResult(Object value, int consumeEndChild) {
        this.value = value;
        this.consumeEndChild = consumeEndChild;
    }
}
}