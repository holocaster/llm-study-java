package br.com.llm.study.roadmap.dtos;

import java.util.List;

public record EmbeddingResponse(List<Item> data) {
  public record Item(int index, List<Double> embedding) {}
}
