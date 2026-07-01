package br.com.llm.study.roadmap.harness;

@FunctionalInterface
public interface AgentRunner {
    String run(String task) throws Exception;
}
