package com.himdsl.runtime;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, String> typeInfo = new HashMap<>();
    private final Environment parent;
    
    public Environment() { this(null); }
    public Environment(Environment parent) { this.parent = parent; }
    
    public void set(String name, Object value) {
        if (variables.containsKey(name)) {
            variables.put(name, value);
        } else if (parent != null && parent.has(name)) {
            parent.set(name, value);
        } else {
            variables.put(name, value);
        }
    }
    public Object get(String name) {
        if (variables.containsKey(name)) return variables.get(name);
        if (parent != null) return parent.get(name);
        return null;
    }
    public boolean has(String name) {
        return variables.containsKey(name) || (parent != null && parent.has(name));
    }
    
    // 数组元素存取（用特殊键）
    public void setArrayElement(String baseName, int index, Object value) {
        variables.put(baseName + "[" + index + "]", value);
    }
    public Object getArrayElement(String baseName, int index) {
        return variables.get(baseName + "[" + index + "]");
    }
    
    public Environment createChild() { return new Environment(this); }
    public void declare(String name, Object value) {
        variables.put(name, value);
    }
    /** 记录变量声明类型（作用域委派规则与 set 相同） */
    public void setType(String name, String type) {
        if (type == null) return;
        if (variables.containsKey(name)) {
            typeInfo.put(name, type);
        } else if (parent != null && parent.has(name)) {
            parent.setType(name, type);
        } else {
            typeInfo.put(name, type);
        }
    }
    
    /** 读取变量声明类型；未声明返回 null */
    public String getType(String name) {
        if (typeInfo.containsKey(name)) return typeInfo.get(name);
        if (parent != null) return parent.getType(name);
        return null;
    }
    
    /** 声明：同时记录类型 */
    public void declare(String name, Object value, String type) {
        variables.put(name, value);
        if (type != null) typeInfo.put(name, type);
    }
}