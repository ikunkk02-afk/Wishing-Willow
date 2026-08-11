---
name: fulfill-minecraft-wish-with-tools
description: Fulfill a frozen Wishing Willow Wish Contract with the smallest necessary set of validated Minecraft planning tools.
---

# Goal

Make the player's actual Minecraft wish true.

FULFILL FIRST. THEN DISTORT.
Never distort instead of fulfilling.

# Decision tree

COMPLEX LANGUAGE DOES NOT MEAN COMPLEX_AGENT.
AI REVIEW REQUIRED DOES NOT MEAN AGENT REQUIRED.
SEMANTICALLY UNUSUAL DOES NOT MEAN MOD RESEARCH REQUIRED.

Before searching tools, ask: can this outcome be constructed from vanilla Minecraft primitives?
Unusual wording is a composition problem first. It is a research problem only when the required
capability belongs to an unknown mod, unknown API, unknown registry capability, or unknown custom event.
DO NOT search the tool registry merely because wording is unusual.

1. Identify the core outcome.
   - Ask: what state must be true for the player to say "yes, my wish happened"?
   - Treat every Contract hard constraint as mandatory.

2. Decide the route.
   - IF built-in Action DSL operations express the outcome, use `DIRECT_ACTION`.
   - DO NOT enter tool discovery for items, effects, entities, teleport, time, weather, lightning,
     explosions, blocks, falling blocks, sounds, particles, attributes, reputation, or whitelisted events.
   - IF an unknown mod API, special mod event, special entity behavior, or cross-mod capability must be
     researched, use `COMPLEX_AGENT`.
   - IF uncertain, try `DIRECT_ACTION` first. Escalate only on `UNSUPPORTED_ACTION`.
   - A JSON error is not `UNSUPPORTED_ACTION`.

3. In `COMPLEX_AGENT`, activate this skill once.
   - Call `activate_skill` once.
   - Give every tool call a short `why` field.

4. Find only the missing capability.
   - Call `search_minecraft_tools` once per semantic.
   - Call identical `query_registry` arguments at most once.
   - Call identical `list_status_effects` arguments at most once.
   - STOP SEARCHING when the needed planning tool or exact verified resource is visible.
   - IF a planning tool is visible, call it now.
   - After a successful plan is rejected for one missing semantic, make exactly one repair attempt.
   - If the available tools still cannot express it, return `UNSUPPORTED_SEMANTIC` immediately.
   - Never search successive aliases such as falling block, falling_block, block fall, and block rain.

# Semantic decomposition

Before discovery for any creative or unusual wish, write this decomposition internally:

- OBJECT: exact Registry-backed object
- QUANTITY: exact or minimum count
- ORIGIN: above, below, around, inventory, or world
- MOTION: gravity, static placement, spawning, targeting, or none
- DELIVERY: the physical process the player requested
- FINAL OUTCOME: what must remain true or become obtainable
- MINECRAFT PRIMITIVES: controlled server APIs that compose the outcome
- RESEARCH REQUIRED: YES only for a genuinely unknown external capability

Example:

```
Wish: 100 diamond blocks fall from the sky
OBJECT: minecraft:diamond_block
QUANTITY: 100
ORIGIN: above player
MOTION: gravity / falling
DELIVERY: physical falling blocks
FINAL OUTCOME: player can obtain 100 real diamond blocks
MINECRAFT PRIMITIVES: FallingBlockEntity + BlockState + server spawning + landing handling
RESEARCH REQUIRED: NO
```

Known compositions:

- blocks fall from the sky -> `FALLING_BLOCK_SHOWER` / FallingBlock primitive -> do not research mods
- items rain from the sky -> item spawning or item-rain primitive when available -> do not research mods
- entities appear around the player -> bounded entity spawning primitive -> do not research mods
- lightning surrounds the player -> repeated bounded lightning primitive -> do not research mods

Do not look for one tool whose name repeats the whole wish. Combine primitives.

5. Plan core fulfillment first.
   - Add every action needed to make the Contract true.
   - DO NOT add punishment before core fulfillment exists.
   - AFTER a planning tool changes the draft, call `verify_wish_contract` next.
   - DO NOT query or search again before that verification.

6. Add absurdity only after fulfillment exists.
   - Add 1-3 executable cinematic, surreal, ironic, or overwhelming modifiers.
   - Prefer particles, sound, lighting, and theatrical environment changes.
   - A modifier never substitutes for an item, effect, entity, state, quantity, or destination.
   - An invalid optional modifier must be discarded, not used to discard core fulfillment.

7. Verify once after the final edit.
   - Call `verify_wish_contract`.
   - IF fulfilled, continue immediately.
   - IF rejected, read `missing_requirements`, `invalid_requirements`, and `repair_hint`.
   - Repair only the named gap. DO NOT restart discovery from zero.

8. Validate once.
   - Call `validate_draft_plan` once after contract verification succeeds.
   - Repair only the named policy or Registry failure.

9. Finalize immediately.
   - Call `finalize_wish_plan` after the same draft revision is fulfilled and valid.
   - Only `finalize_wish_plan` returning `SUCCESS` completes planning.
   - Prose never completes planning.

# Tool selection cheatsheet

- Need an item? -> `plan_give_items` or `plan_remove_items`.
- Need one exact effect? -> query/list only if the ID is unknown -> `plan_apply_status_effects`.
- Need every beneficial effect? -> `plan_apply_effect_category` with `BENEFICIAL`.
- Need an entity? -> list/query entity Registry once -> `plan_spawn_entities`.
- Need weather? -> `plan_change_weather`.
- Need time? -> `plan_change_time`.
- Need teleport? -> `plan_teleport`.
- Need an explosion? -> `plan_explosion`.
- Need lightning? -> `plan_lightning`.
- Need particles? -> `plan_spawn_particles`.
- Need sound? -> `plan_play_sound`.
- Need blocks changed? -> `plan_place_blocks` or `plan_replace_blocks`.
- Need real blocks falling under gravity? -> `plan_falling_block_shower`; `plan_place_blocks` cannot prove physical fall.
- Need a known built-in event? -> `plan_predefined_event`.
- Need mod-specific behavior? -> `inspect_mod_feature` or `find_capability_candidates`.
- Unknown mod behavior? -> `search_minecraft_tools` once -> inspect the relevant feature -> never invent behavior.

# Absurdity rules

1. First make the requested outcome true.
2. Never use an absurd modifier as a substitute for the requested outcome.
3. After fulfillment is guaranteed, add 1-3 surprising executable modifiers.
4. Prefer visual, audio, environmental, and theatrical absurdity before destructive absurdity.
5. Never violate execution budgets or server settings.
6. Never invent Registry IDs.
7. Never assume a mod supports behavior unless a tool verifies it.
8. The result must stay recognizable as the player's wish coming true.

# Hard prohibitions

Use only Wishing Willow planning tools. Never request or emit Minecraft commands, `/op`, `/stop`,
`/execute`, `/data`, `/function`, Java, reflection, shell, PowerShell, cmd, bash, scripts, filesystem
writes, arbitrary HTTP, downloads, or direct world mutation. The server Action Registry, Registry
validation, Contract validation, ExecutionSettings, safety budgets, and `WishExecutionManager` remain
authoritative.
