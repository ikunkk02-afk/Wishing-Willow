# Wishing Willow / 许愿柳

**Wishing Willow** is an AI-powered Minecraft Forge mod. Speak your wish aloud to the Wishing Willow,
and an AI will try to change the Minecraft world to make it real — sometimes faithfully, sometimes
literally, sometimes absurdly. Be careful what you wish for.

---

**许愿柳** 是一个基于 AI 的 Minecraft Forge 愿望模组。向许愿柳说出你的愿望，AI 会理解它，并尝试通过
Minecraft 世界中的实际行为将它实现。有些愿望会被正常实现，有些会被字面化、夸张化甚至荒诞地实现。
小心你许下的愿望。

---

## Features / 主要功能

### Natural-Language Wishing / 自然语言许愿

You don't write commands. Just speak. Type any wish and the AI will interpret it:

- "I want lots of diamonds."
- "Give me a top-tier enchanted diamond sword."
- "I hope I am never alone again."

The AI understands context, picks appropriate Minecraft actions, and constructs a real execution plan
using the mod's available capabilities.

### Absurd Wish Realization / 荒诞愿望实现

Vague, abstract, or extreme wishes may be interpreted literally and exaggerated. "I hope I am never alone"
might fill your surroundings with creatures that stay with you — persistently. The mod's absurdity system
(`absurd_wish_realization`) ensures ambiguous wishes receive creative, unexpected implementations rather
than failing silently.

### Advanced Item Generation / 高级物品生成

Wishes for items produce real Minecraft `ItemStack` objects with full support for:

- Standard items (blocks, tools, resources)
- Enchanted equipment (custom enchantments at configurable levels)
- Top-tier ("treasure") enchantments
- High-level enchantments above vanilla maxima
- Advanced `ItemStack` attributes (custom NBT)

All generated items pass through server-side validation; impossible combinations are rejected.

### Persistent Wishes / 持续愿望

Some wishes don't end after a single action. They create ongoing effects that persist through logout,
death, and server restart:

- Entity attraction / following behavior
- World rules (entity suppression, social rules)
- Never Alone companionship

### Wish Outcome Classification / 愿望结果分类

The mod honestly reports what happened:

- **Granted** — the wish was fully realized.
- **Partially Granted** — some parts succeeded, others could not be completed.
- **Unexecutable** — the wish is beyond what the mod or world can safely do. The mod rejects it
  rather than pretending with particles or buffs.
- **Failed** — an unexpected technical error occurred.

No fake "success" messages. If a wish cannot be done, the mod says so.

### Advancements / 进度系统

An independent "Wishing Willow" advancement page tracks your journey. Includes normal, goal, and
hidden challenge advancements. The root advancement unlocks automatically when you first log in
with the mod installed, making the tab discoverable from the start.

### Mod Research / 模组研究

Wishing Willow scans your installed modpack and builds a local knowledge base:

- Vanilla Minecraft content
- Forge registry entries (items, blocks, entities)
- Public mod metadata
- CurseForge / Modrinth project information (optional, API-key gated)
- Web research for discovery (safety-checked, budget-limited)

This knowledge helps the AI understand what's available in your world when planning wish execution.

### Cinematic Presentation / 影视化呈现

Wishes unfold through a cinematic sequence: screen filters, music states, processing hints, and
reveal animations. The trade/unboxing experience mirrors the wonder of making a wish.

---

## Requirements / 运行要求

| Requirement | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.22 (or compatible 47.x) |
| Java | 17 |
| GeckoLib | 4.8.4 (Required — install separately) |

**Required dependencies** (must be installed by the player):
- [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) 4.8.4

**Bundled** (shipped inside the mod JAR):
- Jsoup (HTML parsing, web research)
- LangChain4j (agent orchestration)
- Jackson (JSON processing)
- Apache OpenNLP (text analysis)

---

## Installation / 安装

1. Install Minecraft Forge 1.20.1.
2. Install GeckoLib 4.8.4 into your `mods` folder.
3. Place the Wishing Willow JAR into `mods`.
4. Launch the game.
5. Open the Wishing Willow Settings screen and configure your AI provider.
6. **Never share your API key with anyone.**

---

## AI Configuration / AI 配置

Wishing Willow uses an **OpenAI-compatible API** that you provide. No free AI service is bundled.

Supported provider presets:
- **DeepSeek** — `api.deepseek.com`
- **Ollama** — local models
- **LM Studio** — local models
- **Custom** — any OpenAI-compatible endpoint

To configure: open the Wishing Willow Settings screen, select your provider, enter your API key
and model name, then test the connection.

---

## Privacy / 隐私安全

- Your API key is stored **locally** in `.minecraft/config/wishing_willow/ai-client.json`.
- The key is **never** sent through Minecraft networking.
- The key is **not** written to world saves or server data.
- Do **not** upload your config files or include your API key in bug reports.
- Research features only send **public** mod metadata. No world seeds, chat, player identifiers,
  or save data are transmitted.

For detailed technical documentation, see [`docs/`](docs/).

---

## ⚠ Important Warning / 重要提醒

This mod allows an AI to:

- Modify the world
- Spawn entities
- Give items
- Change player state
- Establish persistent rules

**Back up your world before using extreme wishes.** Some absurd realizations may spawn many entities,
modify blocks, or create lasting world rules. The mod includes execution budgets and safety limits,
but AI is not perfectly predictable.

---

## Commands / 命令

All commands use the `/wishingwillow` prefix:

| Command | Permission | Description |
|---|---|---|
| `/wishingwillow agent latest` | Player | Inspect the latest AI agent iteration and tool state |
| `/wishingwillow program latest` | Player | Inspect the latest Wish Program (interpretation through execution) |
| `/wishingwillow action latest` | Player | Inspect the latest action step |
| `/wishingwillow wish latest` | Player | Inspect latest wish pipeline state (interpretation → plan → execution) |
| `/wishingwillow execution latest` | Player | Inspect latest execution record |
| `/wishingwillow pipeline inspect <session>` | OP 2 | Full pipeline inspection by session UUID |
| `/wishingwillow execution list` | OP 2 | List all active executions |
| `/wishingwillow execution info <id>` | OP 2 | Execution detail by ID |
| `/wishingwillow execution cancel <id>` | OP 2 | Cancel an execution |
| `/wishingwillow execution dryrun <planId>` | OP 2 | Validate a plan without executing |
| `/wishingwillow execution trigger <id> <step>` | Dev only | Debug step trigger (non-production) |

No command prints API keys, authorization headers, or full AI configuration.

---

## Known Limitations / 已知限制

- AI understanding quality depends on your chosen model.
- Highly complex wishes may only be partially realizable.
- Infinite-scale world modifications are limited by performance protections.
- Cross-mod compatibility depends on the target mod's registry and behavior.
- AI responses take time — a wish may take seconds to minutes depending on complexity.
- Absurd realizations are creative but not always reversible.

---

## Reporting Bugs / 提交 Bug

Please [open a GitHub Issue](https://github.com/ikunkk02-afk/Wishing-Willow/issues).

Include at minimum:

- Minecraft version
- Forge version
- Wishing Willow version
- AI provider and model
- The exact wish text
- `latest.log` / `debug.log`
- List of installed mods
- Steps to reproduce

⚠ **Before uploading logs: check for private information. Never include your API key.**

---

## License / 许可证

MIT. See [`LICENSE`](LICENSE).
