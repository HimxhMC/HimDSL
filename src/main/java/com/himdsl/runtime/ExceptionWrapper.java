package com.himdsl.runtime;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * 脚本可见的异常包装。实现 RefLike 便于统一字段访问。
 * 只读：任何 setField 都会抛异常。
 */
public class ExceptionWrapper implements RefLike {

    private final Throwable throwable;

    public ExceptionWrapper(Throwable t) {
        this.throwable = t;
    }

    public Throwable getThrowable() { return throwable; }

    @Override
    public boolean hasField(String name) {
        switch (name) {
            case "message":
            case "type":
            case "fulltype":
            case "stacktrace":
                return true;
            default:
                return false;
        }
    }

    @Override
    public Object getField(String name) {
        switch (name) {
            case "message":
                return throwable.getMessage() == null ? "" : throwable.getMessage();
            case "type":
                return throwable.getClass().getSimpleName();
            case "fulltype":
                return throwable.getClass().getName();
            case "stacktrace":
                return stackTrace();
            default:
                return null;
        }
    }

    @Override
    public void setField(String name, Object value) {
        throw new RuntimeException("异常字段 " + name + " 是只读的");
    }

    @Override
    public Object invoke(String method, List<Object> args) {
        switch (method) {
            case "printStackTrace":
                throwable.printStackTrace();
                return null;
            case "toString":
                return throwable.toString();
            case "getMessage":
                return throwable.getMessage() == null ? "" : throwable.getMessage();
            default:
                throw new RuntimeException("异常对象不支持方法: " + method);
        }
    }

    @Override
    public String toString() {
        return "[Exception " + throwable.getClass().getSimpleName()
                + ": " + throwable.getMessage() + "]";
    }

    private String stackTrace() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}