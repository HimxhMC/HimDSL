package com.himdsl.runtime;

public class PermissionDeniedException extends RuntimeException {
    public final Capability capability;

    public PermissionDeniedException(Capability cap, String message) {
        super(message);
        this.capability = cap;
    }
}