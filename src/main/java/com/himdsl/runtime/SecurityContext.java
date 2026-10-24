package com.himdsl.runtime;

public class SecurityContext {
    public final String scriptId;
    public final SecurityLevel level;

    public SecurityContext(String scriptId, SecurityLevel level) {
        this.scriptId = (scriptId == null || scriptId.isEmpty()) ? "<inline>" : scriptId;
        this.level    = (level == null) ? SecurityLevel.NORMAL : level;
    }

    @Override
    public String toString() {
        return scriptId + "(" + level.name().toLowerCase() + ")";
    }
}