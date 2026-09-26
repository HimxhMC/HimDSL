package com.himdsl.parser;

import com.himdsl.HimDSLPlugin;
import com.himdsl.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
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
    
    public HimDSLVisitorImpl(HimDSLRuntime runtime) {
        this.runtime = runtime;
        this.currentEnv = runtime.globalEnv;
    }
    
    public void collectDefinitions(ParseTree tree) {
        visit(tree);
    }
    
    public void executeMain() {
        FunctionDef mainFunc = runtime.functions.get("main");
        if (mainFunc == null) throw new RuntimeException("未找到 main 函数");
        Environment oldEnv = currentEnv;
        currentEnv = new Environment(runtime.globalEnv);
        try {
            visit(mainFunc.body);
        } finally {
            currentEnv = oldEnv;
        }
    }
    public void executeMain(List<Object> arguments) {
        FunctionDef mainFunc = runtime.functions.get("main");
        if (mainFunc == null) {
            throw new RuntimeException("未找到 main 函数");
        }
        
        // 检查参数数量是否匹配
        int paramCount = mainFunc.parameters.size();
        int argCount = arguments.size();
        if (paramCount != argCount) {
            throw new RuntimeException("main 函数需要 " + paramCount + " 个参数，但提供了 " + argCount + " 个");
        }
        
        Environment oldEnv = currentEnv;
        currentEnv = new Environment(runtime.globalEnv);
        try {
            // 绑定参数
            for (int i = 0; i < paramCount; i++) {
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
    public Object visitFunctionDef(HimDSLParser.FunctionDefContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info(">>> visitFunctionDef: " + name);
        List<String> params = new ArrayList<>();
        String eventType = null;
        if (ctx.paramList() != null) {
            for (HimDSLParser.ParamContext p : ctx.paramList().param()) {
                if (p instanceof HimDSLParser.EventParamContext) {
                    HimDSLParser.EventParamContext ep = (HimDSLParser.EventParamContext) p;
                    String first = ep.IDENTIFIER().getText(); // 获取参数名（如 "event"）
                    if (first.equals("event")) {
                        // 获取事件类型字符串（可能是 IDENTIFIER 或 STRING）
                        String typeText = ep.eventType().getText();
                        // 如果带双引号，去除首尾
                        if (typeText.startsWith("\"") && typeText.endsWith("\"")) {
                            eventType = typeText.substring(1, typeText.length() - 1);
                        } else {
                            eventType = typeText;
                        }
                    } else {
                        // 如果参数名不是 "event"，则当作普通参数加入列表（虽然目前规范要求用 "event"）
                        params.add(first);
                    }
                } else if (p instanceof HimDSLParser.NormalParamContext) {
                    HimDSLParser.NormalParamContext np = (HimDSLParser.NormalParamContext) p;
                    params.add(np.IDENTIFIER().getText());
                }
            }
        }
        boolean isVoid = ctx.returnType().getText().equals("void");
        String annotation = null;
        if (ctx.annotation() != null && !ctx.annotation().isEmpty()) {
            annotation = ctx.annotation().get(0).IDENTIFIER().getText();
            if (HimDSLPlugin.DEBUG_MODE) Bukkit.getLogger().info("  注解: " + annotation);
        }
        String schedule = ctx.timeSpec() != null ? ctx.timeSpec().getText() : null;
        if (HimDSLPlugin.DEBUG_MODE)if (schedule != null) Bukkit.getLogger().info("  定时: " + schedule);
        
        FunctionDef func = new FunctionDef(name, params, ctx.block(), isVoid, annotation, schedule, eventType);
        runtime.functions.put(name, func);
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("  已注册函数 '" + name + "' 到 runtime.functions，当前函数表: " + runtime.functions.keySet());
        
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("<<< visitFunctionDef 退出");
        return null;
    }
    
    @Override
    public Object visitBlock(HimDSLParser.BlockContext ctx) {
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("  进入 block，语句数量: " + ctx.statement().size());
        Environment oldEnv = currentEnv;
        currentEnv = new Environment(oldEnv);
        try {
            int idx = 0;
            for (HimDSLParser.StatementContext stmt : ctx.statement()) {
                if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("    执行语句 #" + idx + ", 类型: " + stmt.getClass().getSimpleName());
                visit(stmt);
                if (returnFlag || breakFlag || continueFlag) {
                    if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("      遇到 return/break/continue，停止当前 block");
                    break;
                }
                idx++;
            }
        } finally {
            currentEnv = oldEnv;
        }
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("  退出 block");
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
private void doAssignment(LvalueInfo lvalueInfo, Object value, String op) {
    // 1. 获取基础对象
    Object base = currentEnv.get(lvalueInfo.baseName);
    if (base == null) {
        throw new RuntimeException("变量 " + lvalueInfo.baseName + " 未定义");
    }

    // 2. 先处理字段路径，获取最终存储位置（可能是一个结构体实例 + 字段名，或直接是变量）
    // 为了支持链式，我们需要知道最终写入的容器对象和键/字段
    Object container = base;          // 最外层容器
    Object target = base;             // 当前对象，经过字段和索引后指向的值
    StructInstance finalStruct = null;
    String finalField = null;
    List<Object> finalKeys = new ArrayList<>(); // 用于索引的键（整数或字符串）

    // 2.1 处理字段路径
    if (!lvalueInfo.fields.isEmpty()) {
        // 从 base 开始逐层进入结构体
        Object currentObj = base;
        for (int i = 0; i < lvalueInfo.fields.size(); i++) {
            String field = lvalueInfo.fields.get(i);
            if (!(currentObj instanceof StructInstance)) {
                throw new RuntimeException("无法在非结构体上访问字段: " + field);
            }
            StructInstance struct = (StructInstance) currentObj;
            Object fieldValue = struct.get(field);
            if (i == lvalueInfo.fields.size() - 1) {
                // 最后一个字段，记录最终写入位置
                finalStruct = struct;
                finalField = field;
                target = fieldValue;
            } else {
                // 中间字段，继续深入
                if (fieldValue == null) {
                    throw new RuntimeException("嵌套字段 " + field + " 未初始化");
                }
                if (!(fieldValue instanceof StructInstance)) {
                    throw new RuntimeException("字段 " + field + " 不是结构体");
                }
                currentObj = fieldValue;
            }
        }
    } else {
        // 没有字段路径，最终写入的容器是当前环境，字段名为 baseName
        // 但我们需要区分是否包含索引
    }

    // 2.2 处理索引（如果存在）
    boolean isStringIndex = false;
    int stringIndex = -1;
    if (!lvalueInfo.indices.isEmpty()) {
        // 索引只能应用于最终值（target），不能先索引再字段（因为字段在索引前面）
        // 所以这里 target 应该是最终值（可能是 String、List、Map、数组等）
        if (target instanceof String) {
            if (lvalueInfo.indices.size() != 1) {
                throw new RuntimeException("字符串索引只能是一维的");
            }
            isStringIndex = true;
            stringIndex = lvalueInfo.indices.get(0);
        } else {
            // 非字符串索引：逐步深入
            Object currentTarget = target;
            for (int idx : lvalueInfo.indices) {
                if (currentTarget instanceof List) {
                    currentTarget = ((List<?>) currentTarget).get(idx);
                } else if (currentTarget instanceof Map) {
                    currentTarget = ((Map<?, ?>) currentTarget).get(idx);
                } else if (currentTarget != null && currentTarget.getClass().isArray()) {
                    currentTarget = java.lang.reflect.Array.get(currentTarget, idx);
                } else {
                    throw new RuntimeException("无法索引类型: " +
                            (currentTarget == null ? "null" : currentTarget.getClass().getSimpleName()));
                }
            }
            target = currentTarget; // 更新 target 为索引后的值
            // 注意：对于非字符串索引，我们最终需要把新值写回容器，所以需要记录容器和键
            // 但因为我们无法直接从 target 知道容器，这里采用另一种方法：使用 setArrayElement
            // 我们将在后面通过 getArrayElement 和 setArrayElement 处理，而不是直接操作 target
        }
    }

    // 3. 执行赋值
    if (isStringIndex) {
        // ----- 字符串索引赋值 -----
        if (!op.equals("=")) {
            throw new RuntimeException("字符串索引不支持复合赋值操作: " + op);
        }
        if (target == null) {
            // 如果目标为 null，视为空字符串
            target = "";
        }
        if (!(target instanceof String)) {
            throw new RuntimeException("预期字符串，实际类型: " + target.getClass().getSimpleName());
        }
        String str = (String) target;
        if (stringIndex < 0 || stringIndex >= str.length()) {
            throw new RuntimeException("字符串索引越界: " + stringIndex + "，长度: " + str.length());
        }
        // 转换右侧值
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
            throw new RuntimeException("字符串索引赋值要求右侧为字符或整数，实际类型: " +
                    (value == null ? "null" : value.getClass().getSimpleName()));
        }
        // 构造新字符串
        char[] chars = str.toCharArray();
        chars[stringIndex] = newChar;
        String newStr = new String(chars);

        // 写回
        if (finalStruct != null && finalField != null) {
            finalStruct.set(finalField, newStr);
        } else {
            // 无字段路径，直接写回变量
            currentEnv.set(lvalueInfo.baseName, newStr);
        }
    } else {
        // ----- 非字符串索引赋值（包括普通变量、结构体字段、数组元素等） -----
        // 情况1：有字段路径，但没有索引（或索引已处理为非字符串）
        // 情况2：没有字段路径，可能有索引或没有索引
        if (lvalueInfo.fields.isEmpty()) {
            // 普通变量赋值（可能是数组元素）
            if (op.equals("=")) {
                if (!lvalueInfo.indices.isEmpty()) {
                    // 对容器/数组进行元素赋值
                    setArrayElement(base, lvalueInfo.indices, value);
                } else {
                    currentEnv.set(lvalueInfo.baseName, value);
                }
            } else {
                // 复合赋值
                Object oldVal;
                if (!lvalueInfo.indices.isEmpty()) {
                    oldVal = getArrayElement(base, lvalueInfo.indices);
                } else {
                    oldVal = currentEnv.get(lvalueInfo.baseName);
                    if (oldVal == null) oldVal = 0;
                }
                Object result = computeCompound(oldVal, value, op);
                if (!lvalueInfo.indices.isEmpty()) {
                    setArrayElement(base, lvalueInfo.indices, result);
                } else {
                    currentEnv.set(lvalueInfo.baseName, result);
                }
            }
        } else {
            // 结构体字段赋值（无索引或索引已处理为非字符串，但这里我们不支持字段+索引组合的非字符串情况）
            // 因为索引已经在上面的分支中处理了（如果 target 不是字符串），且我们暂时只支持字符串索引，
            // 所以这里简单处理为直接赋值字段。
            if (finalStruct == null || finalField == null) {
                throw new RuntimeException("内部错误：finalStruct 或 finalField 为空");
            }
            if (op.equals("=")) {
                finalStruct.set(finalField, value);
            } else {
                Object oldVal = finalStruct.get(finalField);
                if (oldVal == null) oldVal = 0;
                Object result = computeCompound(oldVal, value, op);
                finalStruct.set(finalField, result);
            }
        }
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
            LvalueInfo inner = resolveLvalue(actx.lvalue());
            int index = ((Number) visit(actx.expression())).intValue();
            inner.indices.add(index);
            return inner;
        } else if (ctx instanceof HimDSLParser.FieldLvalueContext) {
            HimDSLParser.FieldLvalueContext fctx = (HimDSLParser.FieldLvalueContext) ctx;
            LvalueInfo inner = resolveLvalue(fctx.lvalue());
            inner.fields.add(fctx.IDENTIFIER().getText());
            return inner;
        }
        throw new RuntimeException("未知 lvalue 类型");
    }
    
    private static class LvalueInfo {
        String baseName;
        List<Integer> indices;
        List<String> fields;
        
        // 无参构造器（初始化空列表）
        public LvalueInfo() {
            this.indices = new ArrayList<>();
            this.fields = new ArrayList<>();
        }
        
        // 可选：保留三参数构造器（如果其他地方使用）
        public LvalueInfo(String baseName, List<Integer> indices, List<String> fields) {
            this.baseName = baseName;
            this.indices = indices;
            this.fields = fields;
        }
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
        HimDSLParser.AssignNoSemiContext updateAssign = ctx.forUpdate() != null ? ctx.forUpdate().assignNoSemi() : null;
        
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
            
            if (updateAssign != null) {
                visitAssignNoSemi(updateAssign);
                if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().info("  for 更新 (assignNoSemi)");
            } else {
                if (HimDSLPlugin.DEBUG_MODE)Bukkit.getLogger().warning("  for 没有更新部分，退出循环");
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
    public Object visitUnary(HimDSLParser.UnaryContext ctx) {
        if (ctx.getChildCount() == 1) {
            // 只有一个子节点，直接访问 postfix
            return visit(ctx.postfix());
        }
        String op = ctx.getChild(0).getText();
        Object val = visit(ctx.unary());
        if (op.equals("!")) return !toBoolean(val);
        if (op.equals("-")) {
            if (val instanceof Number) {
                double d = ((Number) val).doubleValue();
                return (val instanceof Integer) ? (int) -d : -d;
            } else {
                try {
                    double d = Double.parseDouble(val.toString());
                    return -d;
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        if (op.equals("~")) {
            if (val instanceof Number) {
                int i = ((Number) val).intValue();
                return ~i;
            }
        }
        return val;
    }
@Override
public Object visitPostfix(HimDSLParser.PostfixContext ctx) {
    // 1. 求 primary 值，并记录其文本（用于函数名）
    Object result = visit(ctx.primary());
    String primaryText = ctx.primary().getText();

    // 裸标识符若是结构体名，直接视为 StructDef（用于静态方法调用，如 MathUtil.doubleIt(2)）
    if (result == null && runtime.getStruct(primaryText) != null) {
        result = runtime.getStruct(primaryText);
    }

    String funcName = null;
    if (ctx.primary().IDENTIFIER() != null) {
        funcName = primaryText;
    }

    String pendingMethod = null;           // 待调用的方法名
    StructInstance pendingInstance = null; // 若为结构体实例方法，暂存实例
    StructDef pendingStructDef = null;     // 若为静态方法，暂存结构体定义

    int childCount = ctx.getChildCount();

    for (int i = 1; i < childCount; i++) {
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

            if (result instanceof EntityRef) {
                EntityRef ref = (EntityRef) result;
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
                HimDSLParser.ArgumentListContext argCtx = (HimDSLParser.ArgumentListContext) argsNode;
                for (HimDSLParser.ExpressionContext expr : argCtx.expression()) {
                    args.add(visit(expr));
                }
            }

            if (pendingInstance != null) {
                // ---------- 结构体实例方法 ----------
                result = invokeStructMethod(pendingInstance, pendingMethod, args);
                pendingInstance = null;
                pendingMethod = null;
            } else if (pendingStructDef != null) {
                // ---------- 结构体静态方法 ----------
                result = invokeStaticStructMethod(pendingStructDef, pendingMethod, args);
                pendingStructDef = null;
                pendingMethod = null;
            } else if (pendingMethod != null) {
                // ---------- 对象式方法调用 ----------
                if (result instanceof EntityRef) {
                    // EntityRef 反射调用 Bukkit Entity 方法
                    result = ((EntityRef) result).invoke(pendingMethod, args);
                } else if (BuiltinFunctions.isSupportedTarget(result)
                        && BuiltinFunctions.isBuiltin(pendingMethod)) {
                    List<Object> callArgs = new ArrayList<>();
                    callArgs.add(result);
                    callArgs.addAll(args);
                    result = BuiltinFunctions.call(pendingMethod, callArgs);
                } else {
                    result = invokeMethod(result, pendingMethod, args);
                }
                pendingMethod = null;
            } else if (result instanceof EventWrapper) {
                // ---------- EventWrapper 特殊调用 ----------
                EventWrapper wrapper = (EventWrapper) result;
                result = wrapper.invokePendingMethod(args.toArray());
                wrapper.setPendingMethod(null);
            } else {
                // ---------- 普通函数调用 ----------
                if (funcName == null) {
                    throw new RuntimeException("无法调用函数，因为 primary 不是标识符");
                }
                if (BuiltinFunctions.isBuiltin(funcName)) {
                    result = BuiltinFunctions.call(funcName, args);
                } else {
                    FunctionDef func = runtime.functions.get(funcName);
                    if (func == null) {
                        throw new RuntimeException("未定义函数: " + funcName);
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
        Bukkit.getLogger().warning("未匹配的 primary: " + ctx.getText());
        return null;
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
        FunctionDef func = runtime.functions.get(name);
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
        Class<?> clazz = target.getClass();
        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if (!m.getName().equals(methodName)) continue;
            if (m.getParameterCount() != args.size()) continue;
            Class<?>[] paramTypes = m.getParameterTypes();
            boolean match = true;
            for (int i = 0; i < args.size(); i++) {
                Object arg = args.get(i);
                if (arg == null) {
                    if (paramTypes[i].isPrimitive()) { match = false; break; }
                } else {
                    Class<?> argType = arg.getClass();
                    if (!paramTypes[i].isAssignableFrom(argType)) {
                        if (!isPrimitiveMatch(paramTypes[i], argType)) {
                            match = false;
                            break;
                        }
                    }
                }
            }
            if (match) {
                Object[] converted = convertArgs(args, paramTypes);
                try {
                    return m.invoke(target, converted);
                } catch (Exception e) {
                    throw new RuntimeException("调用方法 " + methodName + " 失败: " + e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("找不到匹配的方法: " + methodName + " with " + args.size() + " parameters");
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
    
    private Object[] convertArgs(List<Object> args, Class<?>[] targetTypes) {
        Object[] converted = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            Object arg = args.get(i);
            if (arg == null) {
                converted[i] = null;
                continue;
            }
            Class<?> target = targetTypes[i];
            if (target.isAssignableFrom(arg.getClass())) {
                converted[i] = arg;
            } else if (target == int.class) {
                converted[i] = ((Number) arg).intValue();
            } else if (target == boolean.class) {
                converted[i] = arg;
            } else if (target == double.class) {
                converted[i] = ((Number) arg).doubleValue();
            } else if (target == long.class) {
                converted[i] = ((Number) arg).longValue();
            } else if (target == float.class) {
                converted[i] = ((Number) arg).floatValue();
            } else if (target == short.class) {
                converted[i] = ((Number) arg).shortValue();
            } else if (target == byte.class) {
                converted[i] = ((Number) arg).byteValue();
            } else if (target == char.class) {
                converted[i] = arg.toString().charAt(0);
            } else {
                converted[i] = arg;
            }
        }
        return converted;
    }
}