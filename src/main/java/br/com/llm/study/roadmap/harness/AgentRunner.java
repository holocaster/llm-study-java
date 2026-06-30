package br.com.llm.study.roadmap.harness;

/** Callback that runs a fresh subagent on a task and returns its final text answer. */
@FunctionalInterface
public interface AgentRunner {
    String run(String task) throws Exception;
}
