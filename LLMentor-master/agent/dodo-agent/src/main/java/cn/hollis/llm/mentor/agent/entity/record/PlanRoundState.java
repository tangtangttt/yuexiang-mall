package cn.hollis.llm.mentor.agent.entity.record;

import java.util.List;

/**
 * 计划执行轮次状态
 */
public record PlanRoundState(
        int round,
        List<PlanTask> plan,
        java.util.Map<String, TaskResult> results,
        CritiqueResult critique
) {
}
