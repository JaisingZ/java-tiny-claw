# Security Policy

Tiny Agent Harness is an experimental project for studying controlled Agent runtime execution. It is not a production security product.

## Supported versions

Security fixes are handled on the default development branch until the project starts publishing stable releases.

## Reporting a vulnerability

Please do not open a public issue for sensitive security reports.

Report privately through GitHub Security Advisories when available, or contact the repository owner directly.

Include:

- A short description of the issue.
- The affected layer: Runtime, Provider, Context, Tool Registry, Communication, Middleware, app wiring, or Observability.
- Reproduction steps or a minimal proof of concept.
- Any relevant trace, log, or configuration excerpt with secrets removed.

## Security model

Tiny Agent Harness keeps risk boundaries explicit:

- Tools validate arguments and workspace paths.
- Tool permission rules can allow, ask, or deny tool calls.
- Telegram approvals can gate high-risk actions.
- Runtime execution remains observable through logs, metrics, and JSON traces.

Never commit local tokens, `agent.properties`, private keys, or generated runtime state.
