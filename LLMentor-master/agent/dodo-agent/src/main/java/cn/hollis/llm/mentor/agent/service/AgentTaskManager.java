package cn.hollis.llm.mentor.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent任务管理器
 * 用于管理流式输出的停止和中断
 */
@Slf4j
@Component
public class AgentTaskManager {

    /**
     * 任务信息
     */
    public static class TaskInfo {
        private final Sinks.Many<String> sink;
        private Disposable disposable;
        private final long createTime;
        private String agentType;

        public TaskInfo(Sinks.Many<String> sink, String agentType) {
            this.sink = sink;
            this.agentType = agentType;
            this.createTime = System.currentTimeMillis();
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

        public long getCreateTime() {
            return createTime;
        }

        public String getAgentType() {
            return agentType;
        }
    }

    /**
     * 会话ID -> 任务信息的映射
     */
    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();

    /**
     * 任务ID计数器
     */
    private final AtomicLong taskIdCounter = new AtomicLong(0);

    /**
     * 注册任务
     *
     * @param conversationId 会话ID
     * @param sink           输出流
     * @param agentType      Agent类型（websearch/file/pptx）
     * @return 任务信息，如果已存在任务则返回null
     */
    public TaskInfo registerTask(String conversationId, Sinks.Many<String> sink, String agentType) {
        TaskInfo existing = taskMap.get(conversationId);
        if (existing != null) {
            log.warn("会话 {} 已有任务在执行，拒绝注册新任务", conversationId);
            return null;
        }

        TaskInfo taskInfo = new TaskInfo(sink, agentType);
        taskMap.put(conversationId, taskInfo);
        log.info("注册任务: conversationId={}, agentType={}", conversationId, agentType);
        return taskInfo;
    }

    /**
     * 设置任务的Disposable
     *
     * @param conversationId 会话ID
     * @param disposable     Disposable对象
     */
    public void setDisposable(String conversationId, Disposable disposable) {
        TaskInfo taskInfo = taskMap.get(conversationId);
        if (taskInfo != null) {
            taskInfo.setDisposable(disposable);
        }
    }

    /**
     * 停止任务
     *
     * @param conversationId 会话ID
     * @return 是否成功停止
     */
    public boolean stopTask(String conversationId) {
        TaskInfo taskInfo = taskMap.get(conversationId);
        if (taskInfo == null) {
            log.warn("会话 {} 没有正在执行的任务", conversationId);
            return false;
        }

        try {
            // 1. 中断底层调用
            Disposable disposable = taskInfo.getDisposable();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
                log.info("已中断底层调用: conversationId={}", conversationId);
            }

            // 2. 发送停止消息
            Sinks.Many<String> sink = taskInfo.getSink();
            if (sink != null) {
                try {
                    sink.tryEmitNext(createStopMessage());
                    sink.tryEmitComplete();
                    log.info("已发送停止消息: conversationId={}", conversationId);
                } catch (Exception e) {
                    log.warn("发送停止消息失败: conversationId={}", conversationId, e);
                }
            }
            taskMap.remove(conversationId);
            return true;
        } catch (Exception e) {
            log.error("停止任务失败: conversationId={}", conversationId, e);
            return false;
        }
    }

    /**
     * 移除任务
     *
     * @param conversationId 会话ID
     */
    public void removeTask(String conversationId) {
        TaskInfo removed = taskMap.remove(conversationId);
        if (removed != null) {
            log.info("移除任务: conversationId={}", conversationId);
        }
    }

    /**
     * 检查会话是否有正在执行的任务
     *
     * @param conversationId 会话ID
     * @return 是否有正在执行的任务
     */
    public boolean hasRunningTask(String conversationId) {
        return taskMap.containsKey(conversationId);
    }

    /**
     * 获取任务信息
     *
     * @param conversationId 会话ID
     * @return 任务信息
     */
    public TaskInfo getTask(String conversationId) {
        return taskMap.get(conversationId);
    }

    /**
     * 创建停止消息
     */
    private String createStopMessage() {
        JSONObject obj = new JSONObject();
        obj.put("type", "text");
        obj.put("content", "⏹ 用户已停止生成\n");
        return JSON.toJSONString(obj);
    }
}
