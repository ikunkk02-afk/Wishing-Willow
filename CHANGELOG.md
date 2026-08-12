# Changelog

## 1.0.0 — 2026-08-12

First public stable release.

### Added

- **Core System:** Wishing Willow item, packaged (sealed) variant, and trade/unboxing sequence.
- **Natural-Language Wish Input:** Player-facing wish screen with confirm dialog and text validation.
- **AI Provider System:** Configurable OpenAI-compatible provider with presets for DeepSeek, Ollama,
  LM Studio, and custom endpoints. Connection testing, model discovery, and interpretation preview.
- **WishProgram Action System:** Structured AI response parsing with tolerant normalization
  (numeric clamping, type coercion, enum cleanup, safe-field repair) followed by strict server
  validation.
- **Wish Execution Pipeline:** Server-authoritative planner, compiler, validator, and executor
  with step-level tracking and lifecycle management.
- **Outcome Classification:** `SUCCESS`, `PARTIAL_SUCCESS`, `UNEXECUTABLE`, `FAILED` — derived
  from terminal core-step evidence, not presentation-only side effects.
- **Absurd Wish Realization:** Literal and exaggerated interpretation of vague/abstract/extreme
  wishes with configurable absurdity style and intensity.
- **Persistent Wishes:** Entity attraction aura, Never Alone companionship, entity suppression
  world rules, and persistent social rules surviving logout, death, and restart.
- **Advanced Item Generation:** Enchanted equipment, treasure enchantments, high-level enchantments
  above vanilla maxima, and custom `ItemStack` attributes through native item-grant actions.
- **Advancement System:** Independent "Wishing Willow" tab with 10 advancements (root, milestones,
  hidden challenges) granted server-side at real lifecycle boundaries.
- **Mod Research:** Installed-mod scanner, registry snapshot, CurseForge/Modrinth API sources,
  public web research with identity resolution, and AI-assisted knowledge interpretation.
- **Cinematic Presentation:** Unboxing sequence, screen filters, music state machine, processing
  hints, and reveal animations.
- **Wish Payment:** Exact `ItemStack` receipt, session-keyed idempotent refunds with offline
  persistence, and side-effect commit guards.
- **Diagnostics Commands:** `/wishingwillow agent latest`, `program latest`, `action latest`,
  `wish latest`, `execution latest/list/info/cancel/dryrun`, and `pipeline inspect`.
- **Execution Settings:** Server-configurable toggle for blocks, explosions, destructive
  explosions, cross-dimension teleport, and debug safe mode.
- **Omens:** Atmospheric messages hinting at wish side effects and delivery style.

### Safety / Reliability

- Execution budgets (step limits, block caps, per-tick scheduling).
- Safe world modification (loaded-chunk checks, batch cursors).
- No fake presentation-only "success" — core evidence drives outcomes.
- Persistent rules with bounded parameters and low-frequency maintenance.
- API credential privacy (redacted `toString()`, no key in logs/networking/saves).
- AI repair/fallback with structured error feedback.
- Session lifecycle protection (cancellation token, idempotency guards).

### Known Limitations

- AI output quality depends on the chosen model.
- Highly complex wishes may only be partially realized.
- Infinite-scale changes are bounded by performance protections.
- Cross-mod compatibility depends on target mod registry structure.
- AI response latency varies with model and wish complexity.
