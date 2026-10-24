package com.himdsl.runtime;

public enum SecurityLevel {
    TRUSTED, NORMAL, SANDBOX;

    public static SecurityLevel parse(String s) {
        if (s == null) return NORMAL;
        switch (s.trim().toLowerCase()) {
            case "trusted": return TRUSTED;
            case "sandbox": return SANDBOX;
            case "normal":
            default:        return NORMAL;
        }
    }
}