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
