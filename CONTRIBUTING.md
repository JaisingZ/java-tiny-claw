# Contributing

Thanks for taking an interest in Tiny Agent Harness.

Tiny Agent Harness is intentionally small. The project exists to study clear Agent runtime boundaries: the model decides, while the harness controls the loop, tools, context, safety checks, and observability.

## Before changing code

- Read [`docs/agent-harness-principles.md`](docs/agent-harness-principles.md).
- Keep the change scoped to one layer: Runtime, Provider, Context, Tool Registry, Communication, Middleware, app wiring, Observability, or optional governance.
- Prefer small, testable changes over framework-style expansion.

## Development

Use Java 21 and Maven 3.9+.

```sh
java -version
mvn -version
mvn test
```

PowerShell users should pass Maven system properties as complete arguments:

```sh
mvn "-Dtest=AgentApplicationTest" test
```

## Pull requests

- Explain the runtime boundary affected by the change.
- Include focused tests for new public behavior.
- Keep high-risk operations interceptable, auditable, and replayable.
- Do not commit local secrets, `agent.properties`, build output, or `.tinyclaw` runtime state.

## Commit style

Use concise conventional commits:

- `feat: add bounded runtime capability`
- `fix: handle provider empty response`
- `docs: clarify context boundary`
- `test: cover tool approval timeout`
