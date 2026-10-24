package com.himdsl.api.impl;

import com.himdsl.HimDSLInterpreter;
import com.himdsl.api.HimDSLAPI;
import com.himdsl.api.PlaceholderHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HimDSLAPIImpl implements HimDSLAPI {
    private final HimDSLInterpreter interpreter;
    private final File workingDir;

    public HimDSLAPIImpl(HimDSLInterpreter interpreter) {
        this.interpreter = interpreter;
        this.workingDir = interpreter.getWorkingDirectory();
    }

    @Override
    public CompileResult compile(File file) {
        HimDSLInterpreter.CompileResult res = interpreter.compile(file);
        return new CompileResult(res.isSuccess(), res.getErrorMessage());
    }

    @Override
    public ExecutionResult run(File file) {
        HimDSLInterpreter.ExecutionResult res = interpreter.run(file);
        return new ExecutionResult(res.isSuccess(), res.getErrorMessage());
    }

    @Override
    public boolean saveScript(String relativePath, String content) {
        Path target = Paths.get(workingDir.getAbsolutePath(), relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content.getBytes());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void registerPlaceholder(String name, PlaceholderHandler handler) {
        interpreter.getRuntime().getResolver().registerCustomPlaceholder(name, handler);
    }
}