# Contributing

Bug reports, questions that IntentFlow translates wrongly, and pull requests are welcome. For a new feature or
database, open an issue first so we can agree on the design. [BACKLOG.md](BACKLOG.md) lists what is planned.

## Building

You need Java 21 or later and Docker, which the integration tests use through Testcontainers.

```
./gradlew build                          # every module on Spring Boot 4
./gradlew build -PspringBoot=3.2.12      # the same on Spring Boot 3.2
./gradlew :intentflow-core:test          # core alone, without dialect modules or Spring
./gradlew :intentflow-shop-api:llmTest   # the question suite against a live model, see intentflow-examples
./gradlew :intentflow-shop-api:performanceTest   # query plans of generated SQL on a large database
```

With `INTENTFLOW_TEST_DB=compose`, the integration tests use the databases from the example's Docker Compose file
instead of starting containers.

## Pull requests

- Add a test that fails without your change. A change to generated SQL shows up in the `expected-sql` files of the
  dialect modules; update them and check that the new SQL is what you meant.
- A new database follows [Adding a database](README.md#adding-a-database) and passes `DialectConformanceTest`.
- A change to the LLM prompt needs the `llmTest` results before and after it, from at least one model.
- A change to how SQL is generated needs a `performanceTest` run; add a case when the change covers a query shape
  the suite does not have.
- Keep the README short: a new setting is a row in the configuration table, and longer explanations go to `docs/`.

## Code style

- Follow the code around your change: its naming, its formatting, and how much it comments. Format Java files with
  the VS Code Java formatter (Eclipse JDT, default profile).
- Every public interface and annotation, which together are the API, has Javadoc. Classes and methods explain
  themselves through their names.
- Mark nullability with JSpecify (`@Nullable`, `@NullMarked`), never with other annotations. Public interfaces return
  `Optional` rather than `null` for a value that may be absent.
- Import every class on its own line; no wildcard imports such as `import java.util.*`.
- No `var`, no fully qualified class names unless two classes really share a name, and no `//` comments that
  repeat the code.
- Core depends on no framework. Spring code belongs in the starters, and UI code only in the UI modules.
- New dependencies are pinned to a release at least two weeks old, in `gradle/libs.versions.toml`.
