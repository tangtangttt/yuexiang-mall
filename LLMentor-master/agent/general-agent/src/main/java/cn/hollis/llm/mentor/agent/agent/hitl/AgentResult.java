package cn.hollis.llm.mentor.agent.agent.hitl;

public sealed interface AgentResult permits AgentFinished, AgentInterrupted {
}
