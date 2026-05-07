package cn.hollis.llm.mentor.agent.entity.record;

/**
 * 批评结果记录
 */
public record CritiqueResult(
        boolean passed,
        String feedback
) {
}
