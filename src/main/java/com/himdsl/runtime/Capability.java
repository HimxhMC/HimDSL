package com.himdsl.runtime;

/**
 * 安全能力枚举。
 * 每个能力对应一类可能产生副作用的操作，由 SecurityConfig 决定各级别是否允许。
 */
public enum Capability {
    // 文件系统
    FS_READ, FS_WRITE, FS_DELETE, FS_ABSOLUTE,
    // 执行与调度
    CMD_CONSOLE, CMD_PLAYER, SCHEDULER, SLEEP,
    // 类加载 / 反射
    CLASS_LOAD, NEW_OBJECT, STATIC_ACCESS,
    // Bukkit 副作用（预留，暂未硬拦截）
    WORLD_WRITE, ENTITY_MODIFY, INVENTORY_MODIFY, PLAYER_ADMIN,
    // GUI / 事件
    GUI, EVENT_HANDLER,
    // 选择器
    SELECTOR_GLOBAL,
    // 外部桥
    PAPI_READ, PAPI_REGISTER, BOSS_API
}