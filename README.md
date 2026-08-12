# 许愿柳 · Wishing Willow

[![Forge](https://img.shields.io/badge/Forge-47.4.22-orange)](https://files.minecraftforge.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)](https://www.minecraft.net/)
[![Java](https://img.shields.io/badge/Java-17-red)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.1-brightgreen)](https://github.com/ikunkk02-afk/Wishing-Willow/releases)

一个基于 AI 的 Minecraft Forge 模组。拿起柳枝，说出你的愿望，然后「啪」地一声将它折断——愿望会以意想不到的方式被实现。

An AI-powered Minecraft Forge mod. Hold the willow branch, speak your wish, then crack it in two — and watch as your wish reshapes the world, sometimes faithfully, sometimes literally, sometimes absurdly. **Be careful what you wish for.**

---

> 🎬 **灵感来源 / Inspired by**：2026 年电影《痴迷》（*Obsession*）中的道具「许愿柳 / Wishing Willow」。本模组将这一电影道具还原为一个完整可玩的 Minecraft 模组。
>
> This mod is inspired by the "Wishing Willow" prop from the 2026 film ***Obsession* (痴迷)**.

---

## 目录 / Table of Contents

- [功能特性](#功能特性-features)
- [依赖关系](#依赖关系-dependencies)
- [AI 服务配置](#ai-服务配置-ai-provider-configuration)
- [安装](#安装-installation)
- [开发](#开发-development)
- [联系方式](#联系方式-links)
- [许可证](#许可证-license)

## 功能特性 / Features

- **许愿系统**：手持许愿柳，输入愿望并折断柳枝即可许愿。
- **AI 理解愿望**：通过 OpenAI 兼容接口由大语言模型理解玩家愿望。
- **智能体规划**：复杂愿望由 LangChain4j 工具调用智能体规划执行动作。
- **电影级演出**：许愿过程中包含沉浸式镜头效果与配乐。
- **模组知识库**：自动调研已安装模组，支持跨模组的愿望实现。
- **多服务商支持**：支持 DeepSeek、Ollama、LM Studio 或任意 OpenAI 兼容接口。

## 依赖关系 / Dependencies

### Minecraft 模组前置（必须安装）

| 模组 | 版本 | 用途 |
|------|------|------|
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | 4.8.4 | 实体与物品动画 |

### Java 内置依赖（已打包，无需单独安装）

以下依赖已通过 [Forge JarJar](https://docs.minecraftforge.net/en/1.20.x/advanced/jarjar/) 内置在模组 JAR 内，**玩家无需额外下载或安装**。

| 依赖 | 版本 | 用途 |
|------|------|------|
| `dev.langchain4j:langchain4j` | 1.18.1 | AI 编排框架 |
| `dev.langchain4j:langchain4j-core` | 1.18.1 | 模型、消息与工具调用核心 API |
| `dev.langchain4j:langchain4j-skills` | 1.18.1-beta28 | 基于技能的智能体工具路由 |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.22 | JSON 注解 |
| `com.fasterxml.jackson.core:jackson-core` | 2.22.1 | JSON 处理 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.1 | JSON 数据绑定 |
| `org.jsoup:jsoup` | 1.23.1 | 网页调研的 HTML 解析 |
| `org.apache.opennlp:opennlp-tools` | 2.5.9 | 技能匹配的自然语言处理 |
| `org.commonmark:commonmark` | 0.28.0 | Markdown 解析 |
| `org.commonmark:commonmark-ext-yaml-front-matter` | 0.28.0 | 技能文件的 YAML 前置元数据 |
| `org.jspecify:jspecify` | 1.0.0 | 可空性注解 |

> **LangChain4j、Jackson、Jsoup、OpenNLP 与 CommonMark 均已内置。玩家只需安装 Wishing Willow 本体与 GeckoLib。**

### 可选模组

| 模组 | 说明 |
|------|------|
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) | 配方查看（非必需） |

## AI 服务配置 / AI Provider Configuration

Wishing Willow 需要一个 OpenAI 兼容接口。在游戏内配置：

1. 打开 模组 → Wishing Willow → 配置 → AI 服务，或使用 `/wishingwillow ai`；
2. 或使用 `/wishingwillow settings`。

支持的服务商：

| 服务商 | 接口地址 | 说明 |
|--------|----------|------|
| DeepSeek | `https://api.deepseek.com/v1` | 内置预设 |
| Ollama | `http://localhost:11434/v1` | 本地部署，内置预设 |
| LM Studio | `http://localhost:1234/v1` | 本地部署，内置预设 |
| 自定义 | 任意地址 | 任意 OpenAI 兼容接口（vLLM、LiteLLM 等） |

### AI 错误提示

AI 请求失败时，Wishing Willow 会显示具体错误信息，而非笼统的「愿望无法实现」：

| 错误 | 提示信息 |
|------|----------|
| 超时 | 愿望的力量没有回应……请检查 AI 网络连接后重试。 |
| DNS 失败 | 无法连接到 AI 服务，请检查网络或接口地址。 |
| 未授权 | AI API 密钥无效，请在设置中更新。 |
| 禁止访问 | AI 服务拒绝了请求。 |
| 模型不可用 | 当前选择的 AI 模型不可用，请在设置中更换模型。 |
| 请求过频 | AI 请求过于频繁，请稍后再次许愿。 |
| 响应无法解析 | AI 返回了无法解析的愿望结果，请重新尝试。 |
| 服务器错误 | AI 服务出现了故障，请稍后再试。 |
| 连接被拒 | 无法连接到 AI 服务，请检查服务地址与网络。 |

## 安装 / Installation

1. 安装 Minecraft 1.20.1 与 Forge 47.4.22；
2. 从 [Releases](https://github.com/ikunkk02-afk/Wishing-Willow/releases) 下载 `wishing_willow-1.0.1.jar`；
3. 安装 [GeckoLib 4.8.4](https://www.curseforge.com/minecraft/mc-mods/geckolib)；
4. 将两个 JAR 文件放入 `mods/` 文件夹；
5. 启动游戏；
6. 获得一枝「许愿柳」（与村民交易或创造模式获取）；
7. 手持柳枝，输入愿望，然后折断它。

> LangChain4j 已内置，**无需**单独下载任何 Java 库。

## 开发 / Development

### 环境要求

- Java 17 JDK
- Git

### 构建

```bash
git clone https://github.com/ikunkk02-afk/Wishing-Willow.git
cd Wishing-Willow
./gradlew build
```

面向玩家的正式 JAR 位于 `build/libs/wishing_willow-1.0.1.jar`（约 9.1 MB，已内置全部依赖）。

> 构建同时会生成 `wishing_willow-1.0.1-slim.jar`（约 4 MB，不含内置依赖），仅用于开发参考。

### 开发命令

```bash
./gradlew runClient      # 启动 Minecraft 客户端
./gradlew runServer      # 启动专用服务器
./gradlew test           # 运行单元测试（约 355 项）
./gradlew build          # 完整构建（含 JarJar）
```

### 架构

```
WishingWillow (@Mod 入口)
 ├── Registry（方块、物品、音效）
 ├── Network（ModNetworking）
 ├── Events（CommonModEvents、VillagerTradeEvents）
 ├── Client
 │   ├── GUI（AiSettingsScreen、WishingWillowSettingsScreen）
 │   ├── Animation（开箱与许愿电影级演出）
 │   ├── Music（WishingWillowMusicController）
 │   └── AI
 │       ├── ClientAiWishCoordinator（愿望生命周期）
 │       ├── ClientWishPlanningCoordinator（智能体规划）
 │       └── ClientWishPlanningEvents（Forge 订阅者，不引用 LangChain4j）
 └── AI
     ├── AiService（HttpClient 池、线程管理）
     ├── OpenAiCompatibleProvider（HTTP、重试、流式）
     ├── WishInterpreter（提示词 → 解析）
     ├── Prompt（WishingWillowPrompt、组装器、运行时上下文）
     └── WishManager（服务端愿望生命周期）
```

> `ClientWishPlanningEvents` 是 AI 模块中唯一标注 `@EventBusSubscriber` 的类，且刻意不引用任何 LangChain4j 类型，从而确保 Forge 自动订阅者注册时不会触发 `NoClassDefFoundError`。所有 AI 逻辑均在真正提交愿望时才延迟加载。

## 联系方式 / Links

- **GitHub**：[ikunkk02-afk/Wishing-Willow](https://github.com/ikunkk02-afk/Wishing-Willow)
- **哔哩哔哩**：[space.bilibili.com/1832031043](https://space.bilibili.com/1832031043)
- **抖音**：[douyin.com/user/MS4wLjABAAAAXPEr9Q0OnMztYvgDTXt6H3g9_626CRAmMbX1L64pBkxbvbHR2ACMWmL55mIL0-Gi](https://www.douyin.com/user/MS4wLjABAAAAXPEr9Q0OnMztYvgDTXt6H3g9_626CRAmMbX1L64pBkxbvbHR2ACMWmL55mIL0-Gi)

## 许可证 / License

[MIT](LICENSE) © 2026 ikunkk02-afk（寿云）
