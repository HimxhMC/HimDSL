package com.himdsl.api;

import java.io.File;
import java.util.List;

public interface HimDSLAPI {
    CompileResult compile(File file);
    ExecutionResult run(File file);
    boolean saveScript(String relativePath, String content);

    /**
     * 注册自定义占位符。
     * @param name 占位符名称（例如 "myplugin"）
     * @param handler 处理逻辑
     */
    void registerPlaceholder(String name, PlaceholderHandler handler);

    class CompileResult {
        private final boolean success;
        private final String errorMessage;
        public CompileResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    class ExecutionResult {
        private final boolean success;
        private final String errorMessage;
        public ExecutionResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}