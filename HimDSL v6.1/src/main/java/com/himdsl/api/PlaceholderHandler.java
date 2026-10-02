package com.himdsl.api;

import java.util.List;

/**
 * 自定义占位符处理器。
 * 其他插件可以通过实现此接口来注册自定义占位符。
 */
@FunctionalInterface
public interface PlaceholderHandler {
    /**
     * 处理占位符并返回结果。
     * @param args 占位符的参数列表（已求值）
     * @return 处理结果（可以是任意类型）
     */
    Object resolve(List<Object> args);
}