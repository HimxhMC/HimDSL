package com.himdsl.runtime;

import org.antlr.v4.runtime.tree.ParseTree;
import java.util.List;

public class FunctionDef {
    public final String name;
    public final List<String> parameters;   // 参数名列表（不含事件参数）
    public final ParseTree body;
    public final boolean isVoid;
    public final String annotation;        // 如 "BukkitRunnable" 或 "EventHandler"
    public final String schedule;
    public final String eventType;         // 若为事件监听函数，事件类名（如 "PlayerJoinEvent"）
    public Object stopHandle;

    public FunctionDef(String name, List<String> parameters, ParseTree body,
                       boolean isVoid, String annotation, String schedule, String eventType) {
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.isVoid = isVoid;
        this.annotation = annotation;
        this.schedule = schedule;
        this.eventType = eventType;
        this.stopHandle = null;
    }
}