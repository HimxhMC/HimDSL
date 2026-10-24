package com.himdsl.runtime;

import java.util.Objects;

/**
 * DSL 层自定义枚举值。用于和字符串区分，同时支持 == 比较。
 */
public class DSLEnumValue {
    public final String enumType;
    public final String constant;

    public DSLEnumValue(String enumType, String constant) {
        this.enumType = enumType;
        this.constant = constant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DSLEnumValue)) return false;
        DSLEnumValue that = (DSLEnumValue) o;
        return enumType.equals(that.enumType) && constant.equals(that.constant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enumType, constant);
    }

    @Override
    public String toString() {
        return enumType + "." + constant;
    }
}