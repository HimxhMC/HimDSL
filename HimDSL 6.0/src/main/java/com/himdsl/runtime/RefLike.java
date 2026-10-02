package com.himdsl.runtime;

import java.util.List;

/**
 * 通用"引用对象"接口。
 * EntityRef / GuiRef / GuiSlotRef 等都实现它，
 * 让 visitPostfix 与 doAssignment 可以用一套逻辑处理字段与方法。
 */
public interface RefLike {
    boolean hasField(String name);
    Object  getField(String name);
    void    setField(String name, Object value);
    Object  invoke(String method, List<Object> args);
}