# Changelog

## v0.1.0 - 2026-06-18

Initial experimental baseline for Tiny Agent Harness.

### Highlights

- Java 21 Maven project for studying controlled Agent runtime execution.
- Runtime loop for finish, single-tool, parallel-tool, and optional thinking decisions.
- OpenAI-compatible provider integration with LM Studio as the default app wiring.
- Minimal tool registry with file, edit, bash, and subagent tools.
- Context composition from `AGENTS.md` and `.tinyclaw/skills/**/SKILL.md` summaries.
- Optional Plan Mode state under `.tinyclaw/state/.../PLAN.md` and `TODO.md`.
- Telegram Webhook integration with session memory, intent filtering, and tool approval.
- Benchmark runner, metrics, readable logs, and JSON trace output.
- GitHub project presentation files: CI, issue templates, PR template, contributing guide, security policy, and social preview asset.
