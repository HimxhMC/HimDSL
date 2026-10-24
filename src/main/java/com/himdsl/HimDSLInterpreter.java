package com.himdsl;

import com.himdsl.parser.HimDSLLexer;
import com.himdsl.parser.HimDSLParser;
import com.himdsl.parser.HimDSLVisitorImpl;
import com.himdsl.runtime.HimDSLRuntime;
import com.himdsl.runtime.Security;
import com.himdsl.runtime.SecurityConfig;
import com.himdsl.runtime.SecurityContext;
import com.himdsl.runtime.SecurityLevel;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
        return runInternal(scriptFile, new ArrayList<>());
    }

    public ExecutionResult run(File scriptFile, String... args) {
        List<Object> argObjects = new ArrayList<>();
        if (args != null) for (String a : args) argObjects.add(a);
        return runInternal(scriptFile, argObjects);
    }

    private ExecutionResult runInternal(File scriptFile, List<Object> argObjects) {
        if (HimDSLPlugin.DEBUG_MODE) System.out.println("===== HimDSL Interpreter run() 开始 =====");

        // ★ 计算 scriptId 与安全级别
        String scriptId = relativize(scriptFile);
        SecurityConfig cfg = Security.getConfig();
        SecurityLevel level = (cfg != null) ? cfg.levelFor(scriptId) : SecurityLevel.NORMAL;
        SecurityContext ctx = new SecurityContext(scriptId, level);

        // 清空上一次脚本的运行时定义
        runtime.functions.clear();
        runtime.clearStructs();
        runtime.clearEventCache();
        runtime.clearUserEnums();
        com.himdsl.runtime.ImportRegistry.clear();

        System.out.println("脚本路径: " + scriptFile.getAbsolutePath() + "（级别: "
            + level.name().toLowerCase() + "）");

        Security.clearWarnings();
        Security.set(ctx);
        try {
            String code = new String(Files.readAllBytes(scriptFile.toPath()));
            if (HimDSLPlugin.DEBUG_MODE || HimDSLPlugin.CODE_MODE)
                System.out.println("脚本内容:\n" + code);

            HimDSLLexer lexer = new HimDSLLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            HimDSLParser parser = new HimDSLParser(tokens);
            ParseTree tree = parser.program();
            if (HimDSLPlugin.DEBUG_MODE)
                System.out.println("解析树生成成功，根节点: " + tree.getClass().getSimpleName());

            HimDSLVisitorImpl visitor = new HimDSLVisitorImpl(runtime);
            if (HimDSLPlugin.DEBUG_MODE) System.out.println("开始 collectDefinitions...");
            visitor.collectDefinitions(tree);
            if (HimDSLPlugin.DEBUG_MODE)
                System.out.println("collectDefinitions 完成，已注册函数: " + runtime.functions.keySet());

            if (HimDSLPlugin.DEBUG_MODE) System.out.println("开始 executeMain...");
            visitor.executeMain(argObjects);
            if (HimDSLPlugin.DEBUG_MODE) System.out.println("executeMain 完成");

            System.out.println("===== 执行成功 =====");
            return new ExecutionResult(true, null);
        } catch (Throwable e) {
            // ★ 出错：WARNING + 中止 + 清理该脚本注册的所有资源
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            runtime.resources.clear(scriptId);
            org.bukkit.Bukkit.getLogger().warning(
                "[HimDSL] 脚本 " + scriptId + " 执行失败，已中止并清理资源：" + msg);
            if (HimDSLPlugin.DEBUG_MODE) e.printStackTrace();
            return new ExecutionResult(false, "执行错误: " + msg);
        } finally {
            Security.clear();
        }
    }

    /** 相对 dataFolder 的路径（用 / 归一） */
    private String relativize(File f) {
        try {
            String base = workingDirectory.getCanonicalPath();
            String path = f.getCanonicalPath();
            if (path.startsWith(base)) {
                String rel = path.substring(base.length());
                if (rel.startsWith(File.separator)) rel = rel.substring(1);
                return rel.replace(File.separatorChar, '/');
            }
            return f.getName();
        } catch (Exception e) {
            return f.getName();
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