package com.himdsl.runtime;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.*;

public class StructDef {
    public final String name;
    public final List<StructField> fields;
    public final List<StructFunction> functions;

    private final Map<String, StructFunction> functionIndex = new HashMap<>();
    private final Set<String> fieldNameSet = new HashSet<>();

    public StructDef(String name, List<StructField> fields, List<StructFunction> functions) {
        this.name = name;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
        this.functions = Collections.unmodifiableList(new ArrayList<>(functions));
        for (StructField f : fields) fieldNameSet.add(f.name);
        for (StructFunction fn : functions) functionIndex.put(fn.name, fn);
    }

    /** 兼容旧的 2 参构造（无成员函数的结构体） */
    public StructDef(String name, List<StructField> fields) {
        this(name, fields, new ArrayList<>());
    }

    public StructFunction findFunction(String methodName) {
        return functionIndex.get(methodName);
    }

    public boolean hasField(String fieldName) {
        return fieldNameSet.contains(fieldName);
    }

    public static class StructField {
        public final String type;
        public final String name;
        public StructField(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    public static class StructFunction {
        public final String name;
        public final List<String> parameters;
        public final ParseTree body;
        public final boolean isVoid;
        public final boolean isStatic;

        public StructFunction(String name, List<String> parameters, ParseTree body,
                              boolean isVoid, boolean isStatic) {
            this.name = name;
            this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
            this.body = body;
            this.isVoid = isVoid;
            this.isStatic = isStatic;
        }
    }
}