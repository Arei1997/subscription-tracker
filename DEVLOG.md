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

## Step 2 — Model Layer (`model/`)

### What is it?
The model layer defines the **data structures** the rest of the application works with. It has no logic, no database code, and no user interaction — it purely represents what a subscription *is*.

### What we created

#### Enums — `Category`, `BillingCycle`, `Status`
An **enum** (enumeration) is a special Java type for a fixed set of named constants. Instead of storing raw strings like `"MONTHLY"` throughout the code (which can be mistyped or inconsistent), we define them once as an enum and use the type everywhere.

```java
public enum BillingCycle {
    MONTHLY, ANNUAL, WEEKLY
}
```

Benefits:
- The compiler catches typos at build time — `BillingCycle.MONTLY` won't compile
- IDEs autocomplete the valid values
- Easy to `switch` on in business logic

In the database, these are still stored as plain strings (`TEXT` column). When we read them back from SQLite we convert: `BillingCycle.valueOf(resultSet.getString("billing_cycle"))`.

#### Record — `Subscription.java`
A **record** is a Java 16+ feature for creating immutable data carriers with minimal boilerplate. Declaring:

```java
public record Subscription(int id, String name, ...) {}
```

automatically gives you:
- A constructor with all fields
- Getters for every field (called `id()`, `name()`, etc. — no `get` prefix)
- `equals()`, `hashCode()`, and `toString()` implementations

Records are **immutable** — once created, the values cannot be changed. This is intentional: if something about a subscription needs updating, a new object is created. This makes the data easier to reason about and prevents accidental mutation.

#### Nullable fields
Two fields can be `null`:
- `cancelledDate` — only set when a subscription is cancelled
- `notes` — optional free text

We use plain `null` (not `Optional`) consistently across the entire codebase. This was a deliberate convention choice — mixing both would be inconsistent and confusing.

### Why separate the model layer?
Keeping data structures in their own package means:
- The `repository` knows what shape to return data in
- The `service` knows what shape to validate and process
- The `CLI` knows what shape to display

All three layers speak the same language — `Subscription` objects — without any layer needing to know how the others work.

---

## Step 3 — Repository Layer (`repository/`)

### What is it?
The repository layer is the **only place in the codebase that touches SQL**. It owns the database connection, all queries, and the mapping from raw SQL result rows back into `Subscription` objects.

### What we created

#### `DatabaseInitialiser.java`
Runs once on startup. Creates the `subscriptions` table if it doesn't already exist (`CREATE TABLE IF NOT EXISTS`). This means the app works on a brand-new machine with no manual setup — just run it and the database is ready.

It takes a JDBC URL string in its constructor (e.g. `"jdbc:sqlite:subscriptions.db"`), which makes it easy to pass an in-memory URL in tests.

#### `SubscriptionRepository.java`
Contains every SQL statement in the app. Broken into three sections:

**Write operations**
- `add(Subscription)` — inserts a new row
- `update(Subscription)` — updates all mutable fields by ID
- `delete(int id)` — removes a row

**Read operations**
- `findById(int)` — returns `Optional<Subscription>` (empty if not found)
- `findAll()` — all subscriptions, sorted by name
- `findByStatus(Status)` — filter by ACTIVE or CANCELLED
- `findRenewingBefore(LocalDate)` — upcoming renewals within a date window

**Aggregates**
- `getTotalMonthlyCost()` — normalises all billing cycles to monthly in SQL
- `getCostByCategory()` — same normalisation, grouped by category, ordered by cost

### Key design decisions

#### `PreparedStatement` everywhere
Every query uses `PreparedStatement` with `?` placeholders — never string concatenation. This prevents SQL injection and also lets the DB engine reuse query plans.

#### Connection-per-call pattern
Each method opens a fresh `Connection` and closes it in a try-with-resources block. This is simple and correct for a single-user desktop app. A multi-user server app would use a connection pool instead.

#### try-with-resources
Every `Connection`, `PreparedStatement`, and `ResultSet` is opened inside a `try (...)` block. Java automatically calls `.close()` on them when the block exits — even if an exception is thrown. This prevents resource leaks.

#### Monthly cost normalisation in SQL
The billing cycle normalisation (monthly → ×1, annual → ÷12, weekly → ×52/12) is written as a `CASE` expression directly in SQL. Doing it in SQL means the database does the aggregation in one pass rather than loading all rows into Java and summing them up there.

#### `map(ResultSet)` private helper
All the logic for converting a result row into a `Subscription` object lives in one private method. Every read operation calls it, so there's no duplication of field-name strings or type conversions.

#### Enum ↔ String conversion
Enums are stored as their `.name()` string in the database (e.g. `"MONTHLY"`, `"ACTIVE"`). On read, `BillingCycle.valueOf(rs.getString(...))` converts back. This is simple and readable in the DB, and the enum type gives compile-time safety in Java.

#### Nullable `cancelled_date`
Before parsing `cancelled_date`, we check if the column is `null` in the result set. A null string passed to `LocalDate.parse()` would throw — the null check prevents that.

---

## Step 4 — Project Structure

### What we created
Added the remaining Maven standard directories:
- `src/main/resources/` — for config files (e.g. `logback.xml`)
- `src/test/java/com/tracker/` — for all test classes
- `src/test/resources/` — for test config overrides

Each directory contains a `.gitkeep` file so git tracks them even when empty. These are removed once real files are placed inside.

Also deleted the stale `com/subscriptiontracker/` tree left over from an earlier draft — the active package is `com/tracker/`.

---

## Step 5 — Service Layer (`service/`)

### What is it?
The service layer sits between the CLI and the repository. It owns all **business logic and validation**. It never writes SQL — it calls the repository for all data access.

### What we created

#### `SubscriptionService.java`
The single service class. Takes a `SubscriptionRepository` in its constructor (injected dependency — makes it easy to mock in tests).

**Add**
- `add(name, category, cost, billingCycle, startDate, notes)` — validates input, calculates renewal date, builds a `Subscription` with `id=0` (DB assigns the real ID), calls `repository.add()`.

**Update**
- `update(id, ...)` — looks up the existing record first (`getOrThrow`), validates, recalculates renewal date, preserves `status`, `cancelledDate`, and `createdAt` from the original.

**Delete**
- `delete(int id)` — verifies the subscription exists before delegating to the repository.

**Cancel / reactivate**
- `cancel(int id)` — sets status to `CANCELLED`, records today as `cancelledDate`. Throws if already cancelled.
- `reactivate(int id)` — sets status back to `ACTIVE`, clears `cancelledDate`, recalculates a fresh renewal date from today. Throws if already active.

**Queries**
- `getAll()`, `getActive()`, `getCancelled()`, `findById(int)`
- `getRenewingWithinDays(int days)` — wraps `findRenewingBefore(today + days)`

**Aggregates**
- `getTotalMonthlyCost()` — delegates to repository
- `getTotalAnnualCost()` — multiplies monthly × 12 (no extra SQL needed)
- `getCostByCategory()` — delegates to repository

### Key design decisions

#### No SQL here — ever
The service never imports `java.sql`. All data access goes through the repository. This is enforced by the package boundary.

#### `getOrThrow` private helper
Rather than scattering `Optional.orElseThrow` calls, one private method fetches by ID and throws `IllegalArgumentException` if missing. All write operations call it first, so the CLI always gets a clear error if the user types a bad ID.

#### Validation is centralised here
`validateName` and `validateCost` are private methods called by both `add` and `update`. The CLI does not validate — it passes raw input to the service and catches `IllegalArgumentException` to show the user an error message.

#### `calculateRenewalDate` is package-visible
Marked `static` and not `private` so the service tests can call it directly to verify renewal date logic without going through `add()`.

#### Reactivation recalculates renewal from today
When a cancelled subscription is reactivated, the old renewal date is stale. A fresh renewal date is calculated from `LocalDate.now()` using the original billing cycle.

---
