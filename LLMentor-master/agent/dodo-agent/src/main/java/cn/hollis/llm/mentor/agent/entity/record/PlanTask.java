package cn.hollis.llm.mentor.agent.entity.record;

/**
 * 计划任务记录
 */
public record PlanTask(
        String id,
        String instruction,
        int order
) {
}
