# Wishing Willow（许愿柳）

Wishing Willow is a Minecraft Forge mod built for Minecraft 1.20.1.

## Development

- Minecraft 1.20.1
- Forge 47.4.22
- Java 17
- Official mappings 1.20.1

Build the mod on Windows with:

```powershell
.\gradlew.bat build
```

## Local AI credentials

Wishing Willow stores player-provided OpenAI-compatible settings in:

```text
.minecraft/config/wishing_willow/ai-client.json
```

The API key is a local client credential. The first version stores it as plain text so the mod can reconnect between
sessions. Do not share or upload this file, include it in bug reports, or commit it to source control. The credential is
never sent through Minecraft networking and is never written to a world save or `WishSavedData`.

## Local mod research

The client builds a read-only knowledge base for the installed modpack under:

```text
.minecraft/config/wishing_willow/knowledge/
```

It sends only public mod metadata, the mod file name and SHA-512, public project text, and namespaced registry IDs to
research services and the configured AI provider. It never sends local paths, player or server identifiers, world seeds,
credentials, chat, or save files. The optional CurseForge API key is stored only in
`.minecraft/config/wishing_willow/research-client.json`; it is never logged, cached with knowledge, or sent to AI.

## Wish validation and AI pipeline

The wish pipeline now uses tolerant AI parsing followed by deterministic normalization and then strict server
validation. In short:

`AI response -> loose JSON recovery -> WishProgramNormalizer -> strict WishProgramValidator -> planning -> execution`

The normalizer repairs safe issues locally (for example numeric clamping, type coercion, enum/action name cleanup,
missing defaults, and harmless unknown fields). Unsafe or ambiguous cases still reject, and the final server executor
continues to enforce world, registry, permission, and budget limits.

The understanding envelope now has an explicit `decision`: `ACCEPT` carries an interpretation and executable program;
`REJECT` carries a bounded rejection code/message and no actions. Capability values come from the Java enum, with a
small whitelist of aliases (including entity-removal synonyms) normalized before strict validation. The server policy
remains authoritative and can reject an AI-accepted program that violates resource or safety limits.

`ENTITY_REMOVAL` is independent from spawning. `remove_entity` performs bounded nearby type removal, while
`entity_suppression` stores a persistent world rule for mob groups. Suppression scans only entities in already-loaded
levels and intercepts future entity joins; it never generates, force-loads, or scans unloaded chunks/dimensions. Players,
items, projectiles, vehicles, and other non-`Mob` entities are excluded by the server implementation.

The consumed willow is recorded as an exact count-one `ItemStack` receipt keyed by wish session. Before any core world
side effect, AI/network/validation/planning/policy rejection requests an idempotent refund. Offline refunds persist until
login; online delivery tries inventory first and drops the unchanged remainder at the player. Once a successful core
action reports an affected world object, the payment is committed and no automatic refund is allowed.

## Wish planning diagnostics

Agent tool planning is an optional enhancement over the compatibility JSON planner. Unknown or unsupported tool-call
capability, Agent timeouts, malformed tool responses, repeated tool errors, and contract-review technical failures fall
back to the JSON planner automatically. A new wish cancels the previous client planning token, and late responses from
the cancelled wish are ignored.

Use `/wishingwillow agent latest` to inspect the live Agent iteration/tool/fallback state, or
`/wishingwillow wish latest` to inspect the latest wish from interpretation through execution. Neither command prints
API keys, authorization headers, or the full local AI configuration.
