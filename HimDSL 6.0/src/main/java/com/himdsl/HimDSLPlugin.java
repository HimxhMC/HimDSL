package com.himdsl;

import com.himdsl.api.HimDSLAPI;
import com.himdsl.api.impl.HimDSLAPIImpl;
import com.himdsl.runtime.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;

public class HimDSLPlugin extends JavaPlugin {
    
    private static HimDSLPlugin instance;
    private HimDSLInterpreter interpreter;
    private EventListener eventListener;
    public static boolean DEBUG_MODE = false;
    public static boolean CODE_MODE = false;
    
    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();
        interpreter = new HimDSLInterpreter(getDataFolder());
        // 直接使用 runtime 中的 eventListener，确保与占位符查询使用同一实例
        eventListener = interpreter.getRuntime().eventListener;
        Bukkit.getPluginManager().registerEvents(eventListener, this);
        getServer().getServicesManager().register(HimDSLAPI.class, new HimDSLAPIImpl(interpreter), this, ServicePriority.Normal);
        getCommand("himdsl").setExecutor(this);
        getLogger().info("HimDSL enabled.");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("HimDSL disabled.");
    }
    
    public static HimDSLPlugin getInstance() { return instance; }
    public HimDSLInterpreter getInterpreter() { return interpreter; }
    public EventListener getEventListener() { return eventListener; }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /himdsl <compile|run|debug> <参数>");
            return false;
        }
        String subCmd = args[0].toLowerCase();
        
        // ---- debug 子命令 ----
        if (subCmd.equals("debug")) {
            String state = args[1].toLowerCase();
            if (state.equals("on")) {
                DEBUG_MODE = true;
                sender.sendMessage("§a调试日志已开启");
            } else if (state.equals("off")) {
                DEBUG_MODE = false;
                sender.sendMessage("§a调试日志已关闭");
            } else if (state.equals("codeon")){
                CODE_MODE = true;
                sender.sendMessage("§a代码展示启动");
            }else if (state.equals("codeoff")){
                CODE_MODE = false;
                sender.sendMessage("§a代码展示关闭");
            } else {
                sender.sendMessage("§c参数错误，请使用 on 或 off");
            }
            return true;
        }
        
        // ---- compile / run 子命令 ----
        String relPath = args[1];
        File baseDir = getDataFolder();
        File targetFile = new File(baseDir, relPath).getAbsoluteFile();
        try {
            if (!targetFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
                sender.sendMessage("§c文件路径必须在插件目录内！");
                return false;
            }
        } catch (Exception e) {
            sender.sendMessage("§c路径解析错误: " + e.getMessage());
            return false;
        }
        if (!targetFile.exists()) {
            sender.sendMessage("§c文件不存在: " + targetFile.getPath());
            return false;
        }
        switch (subCmd) {
            case "compile":
            HimDSLInterpreter.CompileResult cr = interpreter.compile(targetFile);
            if (cr.isSuccess()) sender.sendMessage("§a编译通过！");
            else sender.sendMessage("§c编译错误: " + cr.getErrorMessage());
            break;
            case "run":
            String[] runArgs = new String[args.length - 2];
            System.arraycopy(args, 2, runArgs, 0, runArgs.length);
            if (HimDSLPlugin.DEBUG_MODE)System.out.println("收到 run 命令，文件: " + targetFile.getPath() + "，参数: " + Arrays.toString(runArgs));
            HimDSLInterpreter.ExecutionResult er = interpreter.run(targetFile, runArgs);
            if (er.isSuccess()) {
                sender.sendMessage("§a执行完成。");
                if (HimDSLPlugin.DEBUG_MODE)System.out.println("执行结果: 成功");
            } else {
                sender.sendMessage("§c执行错误: " + er.getErrorMessage());
                if (HimDSLPlugin.DEBUG_MODE)System.err.println("执行结果: 失败, 原因: " + er.getErrorMessage());
            }
            break;
            default:
            sender.sendMessage("§c未知子命令: " + subCmd);
            return false;
        }
        return true;
    }
}