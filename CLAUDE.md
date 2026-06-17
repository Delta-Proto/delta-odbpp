# CLAUDE.md

## Project

Delta ODB++ — Java ODB++ design-archive parser with SVG export and an
ODB++-to-Gerber/Excellon converter. Multi-module Maven build:

- `odbpp-lib` — the published library (`com.deltaproto:delta-odbpp`)
- `odbpp-app` — Spring Boot web viewer (not published)

## Build & Test

```bash
mvn clean test                       # Run all tests
mvn test -pl odbpp-lib -Dtest=ClassName   # Run a specific test class
cd odbpp-app && mvn spring-boot:run  # Run the web viewer on :8080
```

## Deploy

See [DEPLOY.md](DEPLOY.md) for release instructions. The GPG passphrase is in `.mvn-gpg-passphrase`.

```bash
mvn clean deploy -Prelease -Dgpg.passphrase=$(cat .mvn-gpg-passphrase)
```

## Key Conventions

- The parser normalises the whole model to **millimetres at parse time**;
  renderers and exporters assume mm.
- ODB++ tools-file sizes are **mils/microns** (not inches/mm) — see `ToolsParser`.
- Tests run only on committed, non-customer data: synthetic minimal fixtures in
  `odbpp-lib/src/test/resources/odb` and openly-available sample archives in
  `examples/`, both reached through the `Fixtures` test helper. Customer or
  otherwise private archives must **never** be committed — keep them out of the repo.
