---
name: fulfill-minecraft-wish-with-tools
description: Fulfill a frozen Wishing Willow Wish Contract with the smallest necessary set of validated Minecraft planning tools.
---

# Goal

Make the player's actual Minecraft wish true.

FULFILL FIRST. THEN DISTORT.
Never distort instead of fulfilling.

# Decision tree

1. Identify the core outcome.
   - Ask: what state must be true for the player to say "yes, my wish happened"?
   - Treat every Contract hard constraint as mandatory.

2. Decide the route.
   - IF built-in Action DSL operations express the outcome, use `DIRECT_ACTION`.
   - DO NOT enter tool discovery for items, effects, entities, teleport, time, weather, lightning,
     explosions, blocks, sounds, particles, attributes, reputation, or whitelisted events.
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
