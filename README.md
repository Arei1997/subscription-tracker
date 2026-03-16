# Subscription Tracker

A command-line app to track your subscriptions, monitor costs, and get upcoming renewal reminders. Built with Java 17+, SQLite, and Maven.

---

## Features

- Add, update, and delete subscriptions
- Cancel and reactivate subscriptions
- Filter by active or cancelled
- Upcoming renewal alerts (within a custom day window)
- Monthly and annual cost summary, broken down by category
- Supports monthly, annual, and weekly billing cycles
- Data persisted locally in a SQLite database

---

## Requirements

- Java 17 or higher
- Maven 3.6+ (only needed to build from source)

---

## Run

Download the latest JAR from [Releases](../../releases) and run:

```bash
java -jar subscription-tracker.jar
```

The database file (`subscriptions.db`) and logs (`logs/app.log`) are created automatically in the same directory.

---

## Build from Source

```bash
git clone https://github.com/Arei1997/subscription-tracker.git
cd subscription-tracker
mvn clean package
java -jar target/subscription-tracker.jar
```

---

## Run Tests

```bash
mvn test
```

29 tests — 12 repository integration tests (real SQLite in-memory) and 17 service unit tests (Mockito).

---

## Project Structure

```
src/
├── main/
│   ├── java/com/tracker/
│   │   ├── Main.java                        # Entry point, CLI menu
│   │   ├── model/                           # Subscription record + enums
│   │   ├── repository/                      # All SQL (DatabaseInitialiser, SubscriptionRepository)
│   │   └── service/                         # Business logic (SubscriptionService)
│   └── resources/
│       └── logback.xml                      # Logging config
└── test/
    └── java/com/tracker/
        ├── repository/SubscriptionRepositoryTest.java
        └── service/SubscriptionServiceTest.java
```

---

## Data Storage

| File | Purpose |
|------|---------|
| `subscriptions.db` | SQLite database — created on first run |
| `logs/app.log` | Rolling log file, 7 days kept |

To inspect the database, use [DB Browser for SQLite](https://sqlitebrowser.org) or the SQLite Viewer extension in VS Code.
