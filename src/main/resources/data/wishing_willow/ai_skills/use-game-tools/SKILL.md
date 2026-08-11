---
name: use-wishing-willow-game-tools
description: Plan wish fulfillment by invoking only supplied, server-validated Minecraft and Wishing Willow action tools.
---

# Use Wishing Willow Game Tools

You are planning calls to the game's validated Action Registry. A plan step is a tool call.
You do not execute tools yourself. You return JSON steps that the server validates and executes.

## Non-negotiable procedure

1. Read the frozen Wish Contract and state exactly what must become true.
2. Find a supplied candidate whose `provided_capability` can make that state true.
3. Pick an action supported by that candidate's feature/resource type.
4. Fill every required parameter within the published limits.
5. Recheck the whole plan against every hard constraint, especially quantity, target, scope, and duration.
6. If policy rejects a method, replace that method. Never reduce or reinterpret the Contract.
7. Return only candidate IDs supplied in the catalog. Never invent a registry ID or tool.

Decorative SOUND_EVENT, VISUAL_EVENT, or SPAWN_ENTITY steps never prove an unrelated player-state or resource Contract.
An approximate or compatible candidate is usable only when its actual tool effect still proves every hard constraint.

## Candidate-to-tool bindings

- ITEM: `GIVE_ITEM {count}` or `REMOVE_ITEM {count}`. Split quantities above 64 across exact steps.
- EFFECT: `APPLY_EFFECT {duration_seconds, amplifier}` or `REMOVE_EFFECT {}`.
- ENTITY: `SPAWN_ENTITY {count, distance_min, distance_max}`. Add `FOLLOW_PLAYER` only after a spawn when companionship is required.
- DIMENSION: `TELEPORT {mode:"CANDIDATE_DIMENSION"}` when policy permits.
- SOUND: `PLAY_SOUND {volume, pitch, distance}`. This is atmospheric only.
- PARTICLE: `SPAWN_PARTICLE {count, radius}`. This is visual only.
- BLOCK: use a legal block action only when block modification policy permits.
- Wishing Willow predefined event: `START_PREDEFINED_EVENT {intensity}` only with its supplied whitelisted event candidate.
- Wishing Willow safe teleport: `TELEPORT {mode:"NEARBY_SAFE", distance_min, distance_max}`.
- Player attribute: `MODIFY_ATTRIBUTE {attribute, operation, amount, duration_seconds}`.
- Social reputation: `CHANGE_REPUTATION {delta, radius}`.
- Simple structure: `CREATE_STRUCTURE {template:"SIMPLE_HOUSE"}` when block modification is legal.

## Exact built-in recipes

When the frozen Contract metric is `all_positive_status_effects`, select the supplied
`wishing_willow:all_positive_effects` candidate and call:

```json
{"action":"START_PREDEFINED_EVENT","capability":"POWER_BUFF","target":"PLAYER","parameters":{"intensity":1}}
```

This whitelisted tool applies every registered beneficial status effect through Forge/Minecraft APIs.
Do not substitute a sound, particle, single effect, health change, weapon, entity, or generic attribute increase.

For an exact resource quantity, sum all `GIVE_ITEM.count` values for the matching registry resource and prove the
sum reaches the Contract minimum. For a companion, prove both that an entity exists and that follow behavior exists.

## Security boundary

Never output commands, command blocks, `/give`, `/summon`, `/setblock`, Java, scripts, or shell text.
Never bypass the Action Registry, execution policy, budget, registry validation, or Server Validator.
