package com.yuex.mobile.customer.agent;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级流式任务管理（停止生成）
 */
@Slf4j
@Component
public class MallCustomerTaskManager {

    public static class TaskInfo {
        private final Sinks.Many<String> sink;
        private Disposable disposable;

        public TaskInfo(Sinks.Many<String> sink) {
            this.sink = sink;
        }

        public Sinks.Many<String> getSink() {
            return sink;
        }

        public Disposable getDisposable() {
            return disposable;
        }

        public void setDisposable(Disposable disposable) {
            this.disposable = disposable;
        }
    }

    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();

    public TaskInfo registerTask(String conversationId, Sinks.Many<String> sink) {
        if (conversationId == null) {
            return null;
        }
        TaskInfo existing = taskMap.get(conversationId);
        if (existing != null) {
            log.warn("会话 {} 已有任务在执行", conversationId);
            return null;
        }
        TaskInfo taskInfo = new TaskInfo(sink);
        taskMap.put(conversationId, taskInfo);
        return taskInfo;
    }

    public void setDisposable(String conversationId, Disposable disposable) {
        TaskInfo taskInfo = taskMap.get(conversationId);
        if (taskInfo != null) {
            taskInfo.setDisposable(disposable);
        }
    }

    public boolean stopTask(String conversationId) {
        TaskInfo taskInfo = taskMap.get(conversationId);
        if (taskInfo == null) {
            return false;
        }
        try {
            Disposable disposable = taskInfo.getDisposable();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
            Sinks.Many<String> sink = taskInfo.getSink();
            if (sink != null) {
                sink.tryEmitNext(stopMessage());
                sink.tryEmitComplete();
            }
        } catch (Exception e) {
            log.warn("停止任务异常: {}", conversationId, e);
        } finally {
            taskMap.remove(conversationId);
        }
        return true;
    }

    public void removeTask(String conversationId) {
        taskMap.remove(conversationId);
    }

    public boolean hasRunningTask(String conversationId) {
        return conversationId != null && taskMap.containsKey(conversationId);
    }

    private static String stopMessage() {
        JSONObject obj = new JSONObject();
        obj.put("type", "text");
        obj.put("content", "已停止生成\n");
        return obj.toJSONString();
    }
}
