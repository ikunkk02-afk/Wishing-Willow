# Changelog

## Unreleased

### Changed
- Wish interpretation now tolerates common LLM formatting issues and applies deterministic local normalization before strict validation.
- Safe fixes now include numeric clamping, type coercion, enum/action name normalization, default filling, harmless unknown-field dropping, and local action skipping when possible.
- `follow_player` / `avoid_player` behavior limits are now aligned across registry, validation, and executor defaults.
- AI repair prompts now include structured validation details instead of a generic malformed-response placeholder.
- Added `ENTITY_REMOVAL`, strict capability aliases, bounded `remove_entity`, and persistent `entity_suppression` rules.
- Added structured AI `ACCEPT` / `REJECT` results plus a server-authoritative final decision policy.
- Added exact-`ItemStack`, session-keyed, idempotent refunds with persistent offline delivery and side-effect commit guards.
