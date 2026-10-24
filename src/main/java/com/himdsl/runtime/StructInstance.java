package com.himdsl.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StructInstance {
    private final String structName;
    private final Map<String, Object> fields = new HashMap<>();
    private final Set<String> declaredFields = new HashSet<>();
    private final Map<String, String> fieldTypes = new HashMap<>();
    
    public StructInstance(String structName) {
        this.structName = structName;
    }
    
    public void set(String field, Object value) {
        String t = fieldTypes.get(field);
        if (t != null) value = TypeCoerce.convert(value, t);
        fields.put(field, value);
    }
    
    public Object get(String field) {
        return fields.get(field);
    }
    
    public String getStructName() { return structName; }
    
    public void declareField(String name) {
        declaredFields.add(name);
    }
    
    /** 是否为该结构体声明过的字段（含未赋值的） */
    public boolean hasField(String name) {
        return declaredFields.contains(name) || fields.containsKey(name);
    }
    
    public void declareField(String name, String type) {
        declaredFields.add(name);
        if (type != null) fieldTypes.put(name, type);
    }
    
    public String getFieldType(String name) { return fieldTypes.get(name); }
}