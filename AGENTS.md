# Engineering instructions

- Start with `docs/architecture.md`, then the affected module's contracts and tests. Repository contents are the source of truth; do not assume earlier chat context.
- Optimize for a fresh agent's comprehension: descriptive names, focused files, explicit state/data flow and the smallest cohesive modular design. Prefer maintainability over cleverness or implementation speed.
- Keep dependencies directed and acyclic. Common code must not import client code. Domain tasks must not access players, registries, files, networking or UI. Follow the documented boundaries and run `scripts/check-boundaries.py` after changes.
- Do not expose private module internals, add global state as a shortcut, or introduce speculative abstractions. Refactor affected boundaries when a concrete change requires it; preserve external contracts and intended behavior.
- Before significant changes, understand current behavior, interfaces and tests; record a concise plan for complex work. Keep architecture and relevant module documentation synchronized in the same task.
- Add or update meaningful tests for consequential behavior, run relevant static/build checks, and report exactly what was executed. Never claim a game, crash-recovery or persistence acceptance check passed merely because compilation succeeded.
- Keep inventory recovery independent of statistics and notifications. Preserve raw damaged recovery data and fail closed. Never test recovery against an unmarked valuable save or generate/clean unknown terrain.
- Finish the requested task without expanding into unrelated refactors. Report unrelated improvements separately. Explain material tradeoffs with pros and cons when challenging a design decision.
- Multi-agent work must have explicit non-conflicting ownership and one coherent architecture source. Integrate and verify cross-module behavior.
- Keep commit messages short, casual, in English, and starting with a lowercase letter (for example `fix prepared item nbt`).

## Code reviews

Keep reviews read-only. Report actionable, reproducible correctness/security/data-loss regressions or concrete boundary violations, with precise file/line locations and failure scenarios. Distinguish verified defects from architectural risks; do not invent findings, flag style preferences, or request unrelated refactoring.
