# Wishing Willow (许愿柳)

[![Forge](https://img.shields.io/badge/Forge-47.4.22-orange)](https://files.minecraftforge.net/)
[![Minecraft](https://img.shields.io/badge/MC-1.20.1-green)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-red)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/version-1.0.1-brightgreen)](https://github.com/ikunkk02-afk/Wishing-Willow/releases)

A mysterious Forge mod centered on the Wishing Willow — an AI-powered wish fulfillment system. Speak your wish, crack the willow branch in two, and watch as your words reshape the world.

## ✨ Features

- 🪄 **Make wishes** — hold the Wishing Willow, type your wish, and crack it in two
- 🤖 **AI-powered interpretation** — wishes are understood by an LLM via OpenAI-compatible API
- 🧠 **Agent-based planning** — complex wishes use LangChain4j tool-calling agents to plan actions
- 🎬 **Cinematic sequences** — immersive camera effects and music during wish fulfillment
- 📚 **Mod knowledge base** — automatic research of installed mods for cross-mod wish support
- 🎵 **Immersive soundtrack** — original music and dynamic audio during trade reveals and wishes
- 🌐 **Multi-provider support** — DeepSeek, Ollama, LM Studio, or any OpenAI-compatible endpoint

## 📦 Dependencies

### Minecraft Mod Dependencies (required)

| Mod | Version | Purpose |
|-----|---------|---------|
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | 4.8.4 | Entity and item animations |

### Java Embedded Dependencies (bundled — no separate install needed)

The following libraries are bundled inside Wishing Willow via [Forge JarJar](https://docs.minecraftforge.net/en/1.20.x/advanced/jarjar/). **Players do not need to download or install any of these.**

| Library | Version | Purpose |
|---------|---------|---------|
| `dev.langchain4j:langchain4j` | 1.18.1 | AI orchestration framework |
| `dev.langchain4j:langchain4j-core` | 1.18.1 | Core model, message, and tool-calling APIs |
| `dev.langchain4j:langchain4j-skills` | 1.18.1-beta28 | Skill-based agent tool routing |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.22 | JSON annotations |
| `com.fasterxml.jackson.core:jackson-core` | 2.22.1 | JSON processing |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.1 | JSON data binding |
| `org.jsoup:jsoup` | 1.23.1 | HTML parsing for web research |
| `org.apache.opennlp:opennlp-tools` | 2.5.9 | NLP for skill matching |
| `org.commonmark:commonmark` | 0.28.0 | Markdown parsing |
| `org.commonmark:commonmark-ext-yaml-front-matter` | 0.28.0 | YAML front matter in skill files |
| `org.jspecify:jspecify` | 1.0.0 | Nullability annotations |

> ⚡ **LangChain4j, Jackson, Jsoup, OpenNLP, and CommonMark are all bundled with Wishing Willow. Players install only the Wishing Willow JAR and GeckoLib.**

### Optional Mods

| Mod | Notes |
|-----|-------|
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) | Recipe viewing (not required for functionality) |

## ⚙️ AI Provider Configuration

Wishing Willow requires an OpenAI-compatible API endpoint. Configure it in-game:

1. Open Mods → Wishing Willow → Config → AI Provider, or use `/wishingwillow ai`
2. Or open `/wishingwillow settings`

**Supported providers:**

| Provider | Base URL | Notes |
|----------|----------|-------|
| DeepSeek | `https://api.deepseek.com/v1` | Built-in preset |
| Ollama | `http://localhost:11434/v1` | Local, built-in preset |
| LM Studio | `http://localhost:1234/v1` | Local, built-in preset |
| Custom | Any URL | Any OpenAI-compatible endpoint (vLLM, LiteLLM, etc.) |

### AI Error Messages

When AI requests fail, Wishing Willow shows specific error messages instead of a generic "wish cannot be granted":

| Error | Player Message |
|-------|---------------|
| Timeout | "The wish's power did not respond... Please check your AI network connection and try again." |
| DNS Failure | "Cannot reach the AI service. Please check your network or server address." |
| Unauthorized | "The AI API key is invalid. Please update it in settings." |
| Forbidden | "The AI service refused the request." |
| Model Unavailable | "The selected AI model is unavailable. Try another model in settings." |
| Rate Limited | "AI requests are too frequent. Please wait a moment and wish again." |
| Invalid Response | "The AI returned an unreadable wish result. Please try again." |
| Server Error | "The AI service encountered an error. Please try again later." |
| Connection Refused | "Could not connect to the AI service. Please check your server address and network." |

## 🚀 Quick Start

1. Install Minecraft 1.20.1 with Forge 47.4.22
2. Download `wishing_willow-1.0.1.jar` from [Releases](https://github.com/ikunkk02-afk/Wishing-Willow/releases)
3. Install [GeckoLib 4.8.4](https://www.curseforge.com/minecraft/mc-mods/geckolib)
4. Place both JARs in your `mods/` folder
5. Launch the game
6. Acquire a One Wish Willow™ (trade with villagers or creative menu)
7. Hold the willow, type `/wishingwillow wish`, speak your wish, and crack it in two

> 💡 **LangChain4j is bundled.** You do NOT need to download any Java libraries separately.

## 🛠️ Development

### Prerequisites
- Java 17 JDK
- Git

### Building

```bash
git clone https://github.com/ikunkk02-afk/Wishing-Willow.git
cd Wishing-Willow
./gradlew build
```

The player-facing JAR is at `build/libs/wishing_willow-1.0.1.jar` (~9.1 MB, all deps bundled).

> ℹ️ The build also produces `wishing_willow-1.0.1-slim.jar` (~4 MB, no bundled deps) for development reference only.

### Dev Commands
```bash
./gradlew runClient      # Launch Minecraft client
./gradlew runServer      # Launch dedicated server
./gradlew test           # Run JUnit tests (~355 tests)
./gradlew build          # Full build including JarJar
```

### Architecture

```
WishingWillow (@Mod entry)
 ├── Registry (Blocks, Items, Sounds)
 ├── Network (ModNetworking)
 ├── Events (CommonModEvents, VillagerTradeEvents)
 ├── Client
 │   ├── GUI (AiSettingsScreen, WishingWillowSettingsScreen)
 │   ├── Animation (Unboxing, Wish cinematic sequences)
 │   ├── Music (WishingWillowMusicController)
 │   └── AI
 │       ├── ClientAiWishCoordinator (wish lifecycle)
 │       ├── ClientWishPlanningCoordinator (agent planning)
 │       └── ClientWishPlanningEvents (Forge subscriber — LangChain4j-free)
 └── AI
     ├── AiService (HttpClient pool, thread management)
     ├── OpenAiCompatibleProvider (HTTP, retry, streaming)
     ├── WishInterpreter (prompt → interpretation)
     ├── Prompt (WishingWillowPrompt, assembler, runtime context)
     └── WishManager (server-side wish lifecycle)
```

> ⚠️ **Important:** `ClientWishPlanningEvents` is the ONLY class annotated with `@EventBusSubscriber` in the AI module. It deliberately imports zero LangChain4j types, ensuring Forge's automatic subscriber registration never triggers a `NoClassDefFoundError` during mod loading. All AI logic is lazily loaded when a wish is actually submitted.

## 📊 Project Stats

- **355+ unit tests** — comprehensive coverage for AI, research, execution, and client logic
- **~9.1 MB** player JAR — 11 dependency JARs bundled via Forge JarJar
- **Java 17** — no module system issues with `--add-opens` for Forge's SecureJarHandler
- **47.4.22** — tested against Forge's recommended 1.20.1 build

## 🔗 Links

- [GitHub Repository](https://github.com/ikunkk02-afk/Wishing-Willow)
- [Bilibili](https://space.bilibili.com/) — Development vlogs
- [Douyin](https://www.douyin.com/) — Short-form content

## 📄 License

MIT — see [LICENSE](LICENSE) for details.

**Author:** ikunkk02-afk (寿云)
