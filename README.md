# llm-study-java

Learning project: calling an LLM in pure Java.

## Requirements
- Java 25
- Maven 3.8+

## Build and run
Each roadmap step is a standalone `main` class. Build, then run one by its class:
```bash
mvn package
mvn exec:java -Dexec.mainClass=br.com.llm.study.roadmap._1_HelloCompletion.HelloCompletion
```
`mvn exec:java` does not read `.env`; export the keys first:
```bash
set -a; . ./.env; set +a
```
