# Changelog

## Unreleased

### Changed
- Wish interpretation now tolerates common LLM formatting issues and applies deterministic local normalization before strict validation.
- Safe fixes now include numeric clamping, type coercion, enum/action name normalization, default filling, harmless unknown-field dropping, and local action skipping when possible.
- `follow_player` / `avoid_player` behavior limits are now aligned across registry, validation, and executor defaults.
- AI repair prompts now include structured validation details instead of a generic malformed-response placeholder.
