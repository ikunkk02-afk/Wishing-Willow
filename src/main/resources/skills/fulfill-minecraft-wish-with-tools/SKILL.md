---
name: fulfill-minecraft-wish-with-tools
description: Resolve a genuinely unknown Minecraft capability after the Wish Action Registry and Skill Library cannot express it.
---

# Scope

This skill is only for `UNKNOWN_CAPABILITY`. Normal wishes are compiled to a Wish Program and never
enter the Complex Agent.

# Rules

1. Prefer an existing Action.
2. If multiple Actions are needed, compose them.
3. If a reusable Skill exists, use it.
4. Only research when a required capability is genuinely unknown.
5. Never search merely because the wording is creative or unusual.
6. Never repeat the same failed Action without changing parameters or strategy.
7. Required core Actions determine wish success.
8. Presentation Actions are optional.

# Unknown-capability workflow

The Complex Agent has at most five iterations:

1. Identify the one missing mod/API capability.
2. Research only the installed mod or feature that owns it.
3. Construct a bounded temporary capability adapter or reusable Skill from verified APIs.
4. Execute and test it once.
5. Return the actual result, or `UNSUPPORTED`.

Do not fall back to another planner, reviewer, repair model, or compatibility planner. Do not search
successive aliases for the same capability. The same normalized tool call may be attempted at most
twice.

# Action catalog

The authoritative Action catalog is injected from `WishActionRegistry`. IDs, descriptions, schemas,
capabilities, examples, timeouts, and result types must not be duplicated in this file.

# Safety

Never emit Minecraft commands, arbitrary Java, scripts, reflection, shell commands, filesystem writes,
or unverified Registry IDs. Server-side validation, execution settings, Action watchdogs, and Wish
Program budgets remain authoritative.
