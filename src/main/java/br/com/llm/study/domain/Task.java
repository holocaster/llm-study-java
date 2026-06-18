package br.com.llm.study.domain;

public record Task(int id, String title, String notes, boolean done) {

    public Task complete() {
        return new Task(id, title, notes, true);
    }
}
