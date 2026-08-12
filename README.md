<div align="center">

<img src="src/main/resources/logo.png" alt="Wishing Willow" width="200">

# 🌿 Wishing Willow · 许愿柳

**Speak your wish. The willow is listening.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?logo=mojang-studios&logoColor=white)](https://www.minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.4.22-F16436?logo=curseforge&logoColor=white)](https://files.minecraftforge.net)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.0-8A2BE2)](https://github.com/ikunkk02-afk/Wishing-Willow/releases/tag/v1.0.0)
[![Build](https://img.shields.io/badge/Build-Passing-success)](https://github.com/ikunkk02-afk/Wishing-Willow/actions)

</div>

---

> *"You only get one wish."*

**Wishing Willow** is an AI-powered Minecraft Forge mod where your words become reality.
Hold the willow branch, speak your wish, and an AI will try to change the Minecraft world to make it real —
sometimes faithfully, sometimes literally, sometimes absurdly.
**Be careful what you wish for.**

> 🎬 Inspired by the 2026 film ***Obsession* (痴迷)** — the "Wishing Willow" is a prop from the movie,
> brought to life as a fully functional Minecraft mod.

---

**许愿柳** 是一个基于 AI 的 Minecraft Forge 愿望模组。拿起柳枝，说出你的愿望，
AI 会理解它并尝试真正改变 Minecraft 世界 —— 有时诚心实现，有时字面解读，有时荒诞扭曲。
**小心你许下的愿望。**

---

## ✨ Features · 主要功能

<table>
<tr>
<td width="50%">

### 🗣️ Natural-Language Wishing

No commands. No scripting. Just type what you want:

> *"I want lots of diamonds."*
> *"Give me a top-tier enchanted diamond sword."*
> *"I hope I am never alone again."*

The AI interprets your intent, selects the right Minecraft actions,
and builds a real execution plan.

</td>
<td width="50%">

### 🌀 Absurd Realization

Vague or extreme wishes are interpreted **literally** and **exaggerated**.

*"I hope I am never alone"* might fill your world with creatures that stay
with you — permanently. The absurdity system ensures ambiguous wishes
get creative, unexpected outcomes instead of failing silently.

</td>
</tr>

<tr>
<td width="50%">

### ✨ Advanced Items

Wishes for items produce **real Minecraft `ItemStack` objects**:

- Standard items — blocks, tools, resources
- Enchanted equipment with custom levels
- Treasure enchantments above vanilla limits
- High-level enchantments beyond natural maxima
- Custom attribute NBT

Server-side validation rejects impossible combinations.

</td>
<td width="50%">

### ♾️ Persistent Wishes

Some wishes don't end. They create effects that survive **logout, death,
and server restart**:

- Entity attraction & following
- Entity suppression world rules
- Never Alone companionship
- Persistent social rules

</td>
</tr>

<tr>
<td width="50%">

### 📊 Honest Outcomes

The mod reports what **actually** happened — no fake success:

| Outcome | Meaning |
|---|---|
| `Granted` | Fully realized |
| `Partially Granted` | Some parts succeeded, some couldn't |
| `Unexecutable` | Beyond safe capability — rejected honestly |
| `Failed` | Unexpected technical error |

No "pretend with particles." If it can't be done, the mod says so.

</td>
<td width="50%">

### 🏆 Advancement System

An independent **"Wishing Willow"** tab tracks your journey:

- Normal milestones
- Goal achievements
- Hidden challenge advancements

The root unlocks automatically on login — the tab is discoverable
from the start.

</td>
</tr>

<tr>
<td width="50%">

### 🔬 Mod Research

Wishing Willow **scans your installed modpack** to build a knowledge base:

- Vanilla Minecraft content
- Forge registry entries (items, blocks, entities)
- Public mod metadata
- CurseForge / Modrinth (optional API key)
- Web research (safety-checked, budget-limited)

This helps the AI understand what's available in your world.

</td>
<td width="50%">

### 🎬 Cinematic Presentation

Wishes unfold through a cinematic sequence:

- Screen filters & vignette effects
- Dynamic music state machine
- Processing hints & progress messages
- Trade / unboxing reveal animations

The experience mirrors the wonder of making a wish.

</td>
</tr>
</table>

---

## 📦 Requirements · 运行要求

| Type | Name | Version | Notes |
|---|---|---|---|
| ⚙️ | Minecraft | **1.20.1** | |
| ⚙️ | Forge | **47.4.22**+ | |
| ⚙️ | Java | **17** | |
| 🔴 Required | [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | **4.8.4** | Install separately |
| 📦 Bundled | Jsoup | 1.23.1 | HTML / web research |
| 📦 Bundled | LangChain4j | 1.18.1 | Agent orchestration |
| 📦 Bundled | Jackson | 2.22 | JSON processing |
| 📦 Bundled | Apache OpenNLP | 2.5.9 | Text analysis |

> 🔴 = must be installed by the player &nbsp;&nbsp;|&nbsp;&nbsp; 📦 = shipped inside the mod JAR

---

## 🚀 Installation · 安装

1. Install **Minecraft Forge 1.20.1**.
2. Install **GeckoLib 4.8.4** into your `mods` folder.
3. Place **`wishing_willow-1.0.0-all.jar`** into `mods`.
4. Launch the game.
5. Open **Wishing Willow Settings** → configure your AI provider.
6. ⚠️ **Never share your API key with anyone.**

---

## 🤖 AI Configuration · AI 配置

Wishing Willow uses an **OpenAI-compatible API** that **you** provide.
No free AI service is bundled — you bring your own model.

### Provider Presets

| Preset | Endpoint | Notes |
|---|---|---|
| **DeepSeek** | `api.deepseek.com` | Cloud, fast |
| **Ollama** | `localhost:11434` | Local, free |
| **LM Studio** | `localhost:1234` | Local, free |
| **Custom** | Any URL | OpenAI-compatible |

### Setup Steps

1. Open **Wishing Willow Settings** in-game
2. Select your provider type
3. Enter your **API key** and **model name**
4. Click **Test Connection** to verify
5. Save & start wishing

---

## 🔒 Privacy · 隐私安全

| Claim | Detail |
|---|---|
| 🔑 **Local only** | API key stored in `.minecraft/config/wishing_willow/ai-client.json` |
| 🚫 **No network sync** | Never sent through Minecraft networking |
| 🚫 **Not in saves** | Never written to world saves or server data |
| 🚫 **Not in logs** | Redacted from all log output |
| 📚 **Research safe** | Only **public** mod metadata is sent — no seeds, chat, or player data |

> See [`docs/`](docs/) for detailed technical documentation.

---

## ⚠️ Important Warning · 重要提醒

> This mod allows an AI to **modify the world**, **spawn entities**, **give items**,
> **change player state**, and **establish persistent rules**.

**🛡️ Back up your world before extreme wishes.**

Some absurd realizations may spawn many entities, modify blocks, or create lasting
world rules. The mod includes execution budgets and safety limits, but AI is not
perfectly predictable.

---

## ⌨️ Commands · 命令

All commands use the `/wishingwillow` prefix.

### Player Commands

| Command | Description |
|---|---|
| `/wishingwillow agent latest` | Inspect latest AI agent iteration & tool state |
| `/wishingwillow program latest` | Inspect latest Wish Program (interpretation → execution) |
| `/wishingwillow action latest` | Inspect latest action step |
| `/wishingwillow wish latest` | Inspect full pipeline state |
| `/wishingwillow execution latest` | Inspect latest execution record |

### Admin Commands (OP 2+)

| Command | Description |
|---|---|
| `/wishingwillow pipeline inspect <uuid>` | Full pipeline inspection by session |
| `/wishingwillow execution list` | List all active executions |
| `/wishingwillow execution info <uuid>` | Execution detail by ID |
| `/wishingwillow execution cancel <uuid>` | Cancel an execution |
| `/wishingwillow execution dryrun <planId>` | Validate a plan without executing |

> No command prints API keys, authorization headers, or full AI configuration.

---

## 📋 Known Limitations · 已知限制

- AI quality depends on your chosen model and provider
- Highly complex wishes may only be partially realizable
- Infinite-scale modifications are bounded by performance protections
- Cross-mod compatibility depends on target mod registry & behavior
- AI response latency varies (seconds to minutes)
- Absurd realizations are creative but not always reversible

---

## 🐛 Reporting Bugs · 提交 Bug

[![GitHub Issues](https://img.shields.io/badge/GitHub-Issues-181717?logo=github)](https://github.com/ikunkk02-afk/Wishing-Willow/issues)

Please include:

- Minecraft / Forge / Wishing Willow versions
- AI provider and model
- Exact wish text
- `latest.log` or `debug.log`
- List of installed mods
- Steps to reproduce

> ⚠️ **Before uploading logs — check for private information. Never include your API key.**

---

## 🔗 Links · 链接

| Platform | 链接 |
|---|---|
| 📺 **哔哩哔哩** | [space.bilibili.com/1832031043](https://space.bilibili.com/1832031043) |
| 🎵 **抖音** | [douyin.com/user/MS4wLjABAAA...](https://www.douyin.com/user/MS4wLjABAAAAXPEr9Q0OnMztYvgDTXt6H3g9_626CRAmMbX1L64pBkxbvbHR2ACMWmL55mIL0-Gi) |

> 发布视频、更新动态都会发在这里，欢迎关注。

---

## 📄 License · 许可证

[MIT](LICENSE) © 2026 寿云 ([ikunkk02-afk](https://github.com/ikunkk02-afk))

---

<div align="center">

🌿 *"Every wish has its price."*

</div>
