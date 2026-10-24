package com.himdsl.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本资源注册表。
 * 每个脚本执行期间注册的可清理资源（定时任务、事件监听器、文件句柄等）
 * 都挂在 scriptId 下；脚本出错时按 id 一次性清理。
 */
public class ScriptResourceRegistry {

    @FunctionalInterface
    public interface Disposable {
        void dispose() throws Exception;
    }

    private final Map<String, List<Disposable>> map = new HashMap<>();

    public void register(String scriptId, Disposable d) {
        if (scriptId == null || d == null) return;
        synchronized (map) {
            map.computeIfAbsent(scriptId, k -> new ArrayList<>()).add(d);
        }
    }

    /** 清理某脚本的全部资源；清理过程中的异常被吞掉，不影响其他资源 */
    public void clear(String scriptId) {
        List<Disposable> list;
        synchronized (map) {
            list = map.remove(scriptId);
        }
        if (list == null) return;
        for (Disposable d : list) {
            try { d.dispose(); } catch (Throwable ignored) {}
        }
    }

    public void clearAll() {
        List<Disposable> all = new ArrayList<>();
        synchronized (map) {
            for (List<Disposable> l : map.values()) all.addAll(l);
            map.clear();
        }
        for (Disposable d : all) {
            try { d.dispose(); } catch (Throwable ignored) {}
        }
    }
}