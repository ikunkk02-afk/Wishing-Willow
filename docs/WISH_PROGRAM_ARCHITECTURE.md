# Wish Program Architecture Analysis

## Scope and reference study

This document records the design analysis completed before changing the Wishing-Willow runtime.
The reference repositories were cloned outside this repository and inspected at the following commits:

| Project | Commit | License | Design observations used here |
| --- | --- | --- | --- |
| mindcraft-bots/mindcraft | `5f3acc87b479864124173de444f31fa5538f94a6` | MIT | Known commands wrap bounded skills; `ActionManager` owns interruption, the current action, timeout, resume cancellation, and fast-loop detection. `newAction` is an explicit escalation for missing behavior rather than the default path. |
| yuniko-software/minecraft-mcp-server | `240c8cec337ce152cc9e058ebdef511055808406` | Apache-2.0 | A tool is one named operation with a description, typed schema, validation boundary, executor, and structured error/result. The model does not need Mineflayer implementation details. |
| MineDojo/Voyager | `55e45a880755d0c8c66ca7fb5fe7962ac8974f89` | MIT | Stable control primitives are composed into reusable skills. Skill retrieval returns a bounded top-k set; the whole library is not searched repeatedly during execution. |
| JesseRWeigel/mineflayer-chatgpt (`minecraft-agent-swarm`) | `ddc0bae5dcc028d22511e5cc21c8d048b577e06f` | MIT | Primitive, navigation, direct-action, and skill watchdogs are independent. Abort/stop is best effort, but the outer `Promise.race` always releases the decision loop. Repeated identical action/result pairs are temporarily blocked. |
| zhaungsont/mineflayer-gpt | `707702b977b1483d2580552fc6c02b378b7b32b8` | MIT | The stable path is intentionally small: one LLM response selects a predefined task and structured target, then a deterministic handler invokes the Minecraft API. |

All implementation in Wishing-Willow is a clean-room reimplementation. No source fragments from the
reference projects are copied. Their licenses were inspected to establish constraints and attribution
expectations, but this refactor uses only architectural and behavioral ideas.

## Current architecture findings

The current normal path is effectively:

`WishInterpreter -> WishContract -> DirectWishActionPlanner (second AI) -> compiler -> deterministic contract validation -> execution`

When Direct Action cannot compile, it becomes:

`CapabilityMatcher -> WishAgentLoop -> search/activate/edit/verify/finalize tools -> WishPlanningOrchestrator -> compatibility AiWishPlanner -> repair -> execution fallback`

The largest problems are:

1. A normal wish uses two AI requests before execution. The first request describes semantics and a contract;
   the second request finally chooses executable actions.
2. `WishPlanningOrchestrator` deliberately converts Agent failure into another planner, so a bounded failure
   does not terminate. Server-side execution rejection can then invoke `FallbackWishPlanner` again.
3. `WishActionRegistry` is only an enum-to-executor map. Action descriptions, accepted parameters, AI prompt
   text, validation rules, timeout policy, capability mapping, and debug labels live in different places.
4. `WishContractReviewer` is an AI semantic approval step in the Agent path even when the primitive itself
   already has deterministic semantics.
5. The Agent is a planner for ordinary wishes and exposes discovery/search before the model has a complete
   common action catalog.
6. `WishExecutionManager` schedules every immediate step independently. It does not model required core
   actions versus optional presentation actions, a sequential program, per-action deadlines, or superseding
   an older active execution owned by the same player.
7. Reconnect currently restarts resumable planning. That can issue a new AI request for stale work instead of
   treating old planning states as terminal legacy state.
8. `FALLING_BLOCK_SHOWER` already creates real `FallingBlockEntity` objects, but it is reached through a
   semantic recipe plus a second AI planner and old plan/contract machinery instead of being a first-class
   primitive visible during the first understanding request.

## Component decisions

| Component | Decision | New responsibility |
| --- | --- | --- |
| `WishInterpreter` | REPLACE behavior, KEEP compatibility name | Perform the single AI Understanding request and return both the goal interpretation and `WishProgram`. The request includes the Action Catalog. |
| `ClientWishPlanningCoordinator` | SIMPLIFY | Compile and submit an already-selected `WishProgram`; start Complex Agent only for `UNKNOWN_CAPABILITY`. No normal second AI request. |
| `WishPlanningOrchestrator` | DEPRECATE | Legacy-only compatibility surface. It must not enter the default hot path and must not chain to a compatibility planner. |
| `DirectWishActionPlanner` / Direct Action JSON | DEPRECATE | Replaced by the program emitted by the first Understanding request. Kept temporarily for old tests/debug migration only. |
| `WishActionRouter` | REPLACE | Route from program resolution: known actions, then known skill, then unknown capability. No wording-specific routing table. |
| `WishPlanRepairCoordinator` / `AiWishPlanner` / `FallbackWishPlanner` | DEPRECATE | No automatic fallback after Action/Agent failure. Legacy debug APIs only. |
| `WishContractValidator` | SIMPLIFY role | Deterministic goal/safety assertion and legacy plan compatibility only. It is not an AI gate before primitive execution. |
| `WishContractReviewer` | DEPRECATE | Removed from the normal and known-skill paths. |
| `WishActionRegistry` | REPLACE implementation | Single source of truth for id, usage description, parameter schema, capabilities, validator/executor binding, timeout, result type, and AI/debug catalog rendering. |
| `WishExecutionManager` | KEEP low-level Minecraft effects, SIMPLIFY orchestration | Remains the persisted server effect/scheduler layer and executes program groups with primitive/program watchdogs. `WishActionManager` owns start, cancel, supersede, and loop admission. |
| `WishAgentLoop` / `WishAgentSession` | SIMPLIFY | Maximum five iterations. Only missing-capability research/adapter work is allowed. Failure terminates as unsupported; no compatibility planner follows it. |
| Existing Forge action executors | KEEP | Reused behind action definitions after server registry/resource validation. |
| `WishSavedData`, packets, execution saved data | KEEP + VERSION | Add optional program data and migration-safe defaults. Legacy planning/request states become failed/cancelled and are never automatically re-issued on reconnect. |

## Target runtime

1. The first AI call receives the player's wish, safety/fulfillment policy, and a compact catalog generated
   from `WishActionDefinition` objects.
2. It returns a `WishProgram` with a goal, required `core_actions`, optional `presentation_actions`, and an
   optional `unknown_capability` description. It cannot contain commands, Java, scripts, or arbitrary code.
3. The server validates action ids and parameters against the same definitions used to generate the catalog.
   Registry ids are resolved locally against live Forge registries, with conservative local fuzzy resolution.
4. Known actions are compiled into sequential program groups and started through `WishActionManager`.
5. If the program requests a registered reusable skill, the skill expands once into primitive actions. Skill
   retrieval is bounded keyword/tag/example matching and returns only a small candidate set.
6. Only when neither action definitions nor skills cover the declared capability does the Complex Agent run.
   It has at most five iterations and no planner/reviewer fallback after failure.
7. Core action results determine wish success. Presentation failures are recorded but do not fail the wish.
8. A new program supersedes the player's previous non-terminal program. Primitive, skill/program, and Complex
   Agent watchdogs terminate their own layer with an explicit result.

## Compatibility boundary

Existing `WishPlan`, `WishExecutionRecord`, and packet ids remain readable so old worlds do not crash. New
programs use an explicit schema version and are persisted alongside legacy fields. Old `MATCHING`, `PLANNING`,
`VALIDATING`, AI request, or Agent search states are migrated to a terminal legacy failure and are not resumed.
Only a persisted execution that the low-level executor already marks safely resumable (currently journaled block
batches and tracked falling-block showers) may continue after restart.

## Implemented execution guarantees

- `WishProgram` schema version 1 persists beside the legacy interpretation/plan envelope. It has separate
  required core and optional presentation lists and rejects command/script/code-shaped data.
- `WishActionRegistry` publishes 32 bounded Action definitions, including the requested player, block,
  entity, world, presentation, and flow primitives plus three legacy-safe compatibility actions.
- The compiler expands `repeat` with a count ceiling of 16, groups `parallel` children together, advances
  `sequence` groups in order, and turns `delay` into a bounded delayed group.
- The executor returns deterministic action evidence (`status`, `requested`, `completed`, `failed`, and
  `message`) and never invokes an AI reviewer for a known Action or Skill.
- Identical normalized actions are admitted at most twice; non-progress retries are also capped at two.
  Falling-block batch progress is not misclassified as a failed retry.
- Primitive timeouts come from Action definitions, Skills/programs use 60/90 second watchdogs, and the
  Complex Agent retains its separate 60 second deadline with at most five iterations.
- A newly accepted program marks every older nonterminal execution for the same player `SUPERSEDED` before
  it starts. Reconnect never restarts interpretation or planning AI work.
- `/wishingwillow program latest` and `/wishingwillow action latest` expose program/action state in addition
  to the existing wish and Agent diagnostics.
