# Subscription Tracker — Dev Log

A step-by-step build log explaining every decision made while building this project.
Intended as a learning reference alongside the codebase.

---

## Step 1 — `pom.xml` (Maven Project File)

### What is it?
`pom.xml` stands for **Project Object Model**. It is the configuration file for **Maven**, a build tool for Java projects. Every Maven project has exactly one `pom.xml` at its root.

### What does it do?
It tells Maven everything it needs to know about your project:
- **What your project is** — group ID, artifact ID, version
- **What it depends on** — external libraries (SQLite driver, logging, testing frameworks)
- **How to build it** — Java version, plugins, output format

### Why do we need one?
Without a build tool like Maven, you would have to:
- Manually download every `.jar` library your project needs
- Manually add them to your classpath when compiling
- Manually compile every `.java` file in the right order
- Manually bundle everything into a runnable JAR

Maven automates all of this. You declare what you need, and Maven handles the rest.

### Key sections in our `pom.xml`

#### `<properties>`
Sets the Java version (17) and file encoding. Maven uses these when compiling.

#### `<dependencies>`
The libraries our project needs. Maven downloads them automatically from the internet (Maven Central repository) and caches them locally (`~/.m2/`).

| Dependency | Purpose |
|---|---|
| `sqlite-jdbc` | JDBC driver that lets Java talk to a SQLite database |
| `slf4j-api` + `logback-classic` | Logging framework — write log messages at INFO/DEBUG/ERROR levels |
| `junit-jupiter` | Test framework — write and run unit tests |
| `mockito-core` + `mockito-junit-jupiter` | Mocking framework — fake out dependencies in unit tests |

Dependencies marked `<scope>test</scope>` are only available during testing — they are not included in the final JAR.

#### `<build><plugins>`

**maven-surefire-plugin** — runs unit tests when you call `mvn test`. Required explicitly for JUnit 5 support.

**maven-shade-plugin** — bundles the app and all its dependencies into a single **fat JAR** (also called an uber JAR). This is what makes `java -jar subscription-tracker.jar` work on any machine without needing to install anything extra. It also sets the `Main-Class` manifest entry so Java knows where to start execution.

### Common Maven commands
```bash
mvn compile        # Compile source code
mvn test           # Compile and run all tests
mvn package        # Compile, test, and produce the fat JAR in target/
mvn clean          # Delete the target/ folder (compiled output)
mvn clean package  # Full clean rebuild
```

---
