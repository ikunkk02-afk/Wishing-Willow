# Wishing Willow (许愿柳)

A mysterious Forge mod centered on the Wishing Willow — an AI-powered wish fulfillment system for Minecraft 1.20.1.

[![Forge](https://img.shields.io/badge/Forge-47.4.22-orange)](https://files.minecraftforge.net/)
[![Minecraft](https://img.shields.io/badge/MC-1.20.1-green)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-red)](https://adoptium.net/)

## Features

- Plant a **Wishing Willow** and make your wishes come true
- AI-powered wish interpretation via OpenAI-compatible providers
- Agent-based planning with tool calling (LangChain4j)
- Cinematic wish animation sequences
- Built-in mod research and knowledge base
- JEI integration for wish-related recipes

---

## Dependencies

### Minecraft Mod Dependencies (required at runtime)

| Mod | Version | Notes |
|-----|---------|-------|
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | 4.8.4 | For entity/item animations |

### Java Embedded Dependencies (bundled — players do NOT need to install separately)

The following libraries are embedded inside Wishing Willow via [Forge JarJar](https://docs.minecraftforge.net/en/1.20.x/advanced/jarjar/). **Players do not need to download or install any of these separately.**

| Library | Version | Purpose |
|---------|---------|---------|
| `dev.langchain4j:langchain4j` | 1.18.1 | AI orchestration framework |
| `dev.langchain4j:langchain4j-core` | 1.18.1 | Core LangChain4j model/agent APIs |
| `dev.langchain4j:langchain4j-skills` | 1.18.1-beta28 | Skill-based agent tool routing |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.22 | JSON annotations (LangChain4j requirement) |
| `com.fasterxml.jackson.core:jackson-core` | 2.22.1 | JSON processing (LangChain4j requirement) |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.1 | JSON data binding (LangChain4j requirement) |
| `org.jsoup:jsoup` | 1.23.1 | HTML parsing for web research features |
| `org.apache.opennlp:opennlp-tools` | 2.5.9 | Natural language processing for skill matching |
| `org.commonmark:commonmark` | 0.28.0 | Markdown parsing for skill files |
| `org.commonmark:commonmark-ext-yaml-front-matter` | 0.28.0 | YAML front matter in skill files |
| `org.jspecify:jspecify` | 1.0.0 | Nullability annotations |

> **LangChain4j is bundled with Wishing Willow. Players do not need to install it separately.**

### Optional Dependencies

| Mod | Version | Notes |
|-----|---------|-------|
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) | 15.x+ | Recipe viewing (optional, not required for functionality) |

### AI Provider Configuration

Wishing Willow requires an OpenAI-compatible API endpoint to function. Configure it via the in-game settings screen (`/wishingwillow settings` or Mods → Wishing Willow → Config).

Supported providers:
- OpenAI (`https://api.openai.com/v1`)
- DeepSeek (`https://api.deepseek.com/v1`)
- Any OpenAI-compatible endpoint (Ollama, vLLM, LiteLLM, etc.)

---

## Development

### Prerequisites

- Java 17 (JDK)
- Gradle (wrapper included)

### Building

```bash
./gradlew build
```

The player-facing JAR is produced at `build/libs/wishing_willow-<version>.jar`.

> **Note:** The build also produces `wishing_willow-<version>-slim.jar` which is a thin JAR without bundled dependencies. Only the non-suffixed JAR should be distributed to players.

### Running in Dev

```bash
./gradlew runClient   # Launch Minecraft client
./gradlew runServer   # Launch dedicated server
./gradlew runGameTestServer  # Run GameTests
```

### Running Tests

```bash
./gradlew test        # Run JUnit tests
```

---

## License

MIT — see [LICENSE](LICENSE) for details.

Authors: ikunkk02-afk
