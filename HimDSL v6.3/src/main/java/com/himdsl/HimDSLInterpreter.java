package com.himdsl;

import com.himdsl.parser.HimDSLLexer;
import com.himdsl.parser.HimDSLParser;
import com.himdsl.parser.HimDSLVisitorImpl;
import com.himdsl.parser.HimDSLLexer;
import com.himdsl.parser.HimDSLParser;
import com.himdsl.parser.HimDSLVisitorImpl;
import com.himdsl.HimDSLPlugin;
import com.himdsl.runtime.HimDSLRuntime;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import com.himdsl.runtime.HimDSLRuntime;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class HimDSLInterpreter {

    private final File workingDirectory;
    private final HimDSLRuntime runtime;

    public HimDSLInterpreter(File dataFolder) {
        this.workingDirectory = dataFolder;
        this.runtime = new HimDSLRuntime(dataFolder);
    }

    public File getWorkingDirectory() { return workingDirectory; }
    public HimDSLRuntime getRuntime() { return runtime; }

    public CompileResult compile(File scriptFile) {
        try {
            String code = new String(Files.readAllBytes(scriptFile.toPath()));
            HimDSLLexer lexer = new HimDSLLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            HimDSLParser parser = new HimDSLParser(tokens);
            parser.setErrorHandler(new BailErrorStrategy());
            parser.program();
            return new CompileResult(true, null);
        } catch (IOException e) {
            return new CompileResult(false, "文件读取失败: " + e.getMessage());
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return new CompileResult(false, "语法错误: " + msg);
        }
    }

public ExecutionResult run(File scriptFile) {
    if (HimDSLPlugin.DEBUG_MODE) Bukkit.getServer().getLogger().info("===== HimDSL Interpreter run() 开始 =====");
    runtime.functions.clear();
    Bukkit.getServer().getLogger().info("脚本路径: " + scriptFile.getAbsolutePath());
    runtime.clearStructs();
    runtime.clearEventCache();
    runtime.clearUserEnums();  
    try {
        String code = new String(Files.readAllBytes(scriptFile.toPath()));
        if (HimDSLPlugin.DEBUG_MODE || HimDSLPlugin.CODE_MODE)Bukkit.getServer().getLogger().info("脚本内容:\n" + code);
        HimDSLLexer lexer = new HimDSLLexer(CharStreams.fromString(code));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HimDSLParser parser = new HimDSLParser(tokens);
        ParseTree tree = parser.program();
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getServer().getLogger().info("解析树生成成功，根节点: " + tree.getClass().getSimpleName());
        
        HimDSLVisitorImpl visitor = new HimDSLVisitorImpl(runtime);
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getServer().getLogger().info("开始 collectDefinitions...");
        visitor.collectDefinitions(tree);
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getServer().getLogger().info("collectDefinitions 完成，已注册函数: " + runtime.functions.keySet());
        
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getServer().getLogger().info("开始 executeMain...");
        visitor.executeMain();
        if (HimDSLPlugin.DEBUG_MODE)Bukkit.getServer().getLogger().info("executeMain 完成");
        
        Bukkit.getServer().getLogger().info("===== 执行成功 =====");
        return new ExecutionResult(true, null);
    } catch (IOException e) {
        System.err.println("文件读取失败: " + e.getMessage());
        return new ExecutionResult(false, "文件读取失败: " + e.getMessage());
    } catch (Exception e) {
        e.printStackTrace();
        System.err.println("执行异常: " + e.getMessage());
        return new ExecutionResult(false, "执行错误: " + e.getMessage());
    }
}
public ExecutionResult run(File scriptFile, String... args) {
    if (HimDSLPlugin.DEBUG_MODE) Bukkit.getServer().getLogger().info("===== HimDSL Interpreter run() 开始 =====");
    runtime.functions.clear();
    runtime.clearStructs(); // 清空结构体定义（若需清空）
    runtime.clearEventCache();
    runtime.clearUserEnums();  
    if (HimDSLPlugin.DEBUG_MODE)Bukkit.getServer().getLogger().info("脚本路径: " + scriptFile.getAbsolutePath());
    try {
        String code = new String(Files.readAllBytes(scriptFile.toPath()));
        if (HimDSLPlugin.DEBUG_MODE || HimDSLPlugin.CODE_MODE) Bukkit.getServer().getLogger().info("脚本内容:\n" + code);
        HimDSLLexer lexer = new HimDSLLexer(CharStreams.fromString(code));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HimDSLParser parser = new HimDSLParser(tokens);
        ParseTree tree = parser.program();
        if (HimDSLPlugin.DEBUG_MODE) Bukkit.getServer().getLogger().info("解析树生成成功，根节点: " + tree.getClass().getSimpleName());
        
        HimDSLVisitorImpl visitor = new HimDSLVisitorImpl(runtime);
        if (HimDSLPlugin.DEBUG_MODE) Bukkit.getServer().getLogger().info("开始 collectDefinitions...");
        visitor.collectDefinitions(tree);
        if (HimDSLPlugin.DEBUG_MODE) Bukkit.getServer().getLogger().info("collectDefinitions 完成，已注册函数: " + runtime.functions.keySet());
        
        if (HimDSLPlugin.DEBUG_MODE) Bukkit.getServer().getLogger().info("开始 executeMain...");
        // 将字符串参数转换为 Object 列表
        List<Object> argObjects = new ArrayList<>();
        for (String arg : args) {
            argObjects.add(arg);
        }
        visitor.executeMain(argObjects);
        if (HimDSLPlugin.DEBUG_MODE) Bukkit.getServer().getLogger().info("executeMain 完成");
        
        Bukkit.getServer().getLogger().info("===== 执行成功 =====");
        return new ExecutionResult(true, null);
    } catch (IOException e) {
        System.err.println("文件读取失败: " + e.getMessage());
        return new ExecutionResult(false, "文件读取失败: " + e.getMessage());
    } catch (Exception e) {
        e.printStackTrace();
        System.err.println("执行异常: " + e.getMessage());
        return new ExecutionResult(false, "执行错误: " + e.getMessage());
    }
}
    public static class CompileResult {
        private final boolean success;
        private final String errorMessage;
        public CompileResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class ExecutionResult {
        private final boolean success;
        private final String errorMessage;
        public ExecutionResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}