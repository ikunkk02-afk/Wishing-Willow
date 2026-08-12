# Wishing Willow 1.0.0

Wishing Willow 1.0.0 是许愿柳的第一个正式公开版本。

向许愿柳说出你的愿望，AI 会尝试真正改变 Minecraft 世界来实现它。
它可能按照你的本意实现，也可能按照最字面的方式理解你的话。
小心你许下的愿望。

---

*Wishing Willow 1.0.0 — the first public release. Speak your wish to the Wishing Willow. An AI will try to change the Minecraft world to make it real — sometimes faithfully, sometimes literally, sometimes absurdly. Be careful what you wish for.*

---

## 主要功能 / Key Features

- **自然语言许愿** — 不需要命令脚本，直接打字说出愿望
- **荒诞愿望实现** — 模糊/极端的愿望会被字面化、夸张化实现
- **高级物品生成** — 附魔装备、顶级附魔、高等级附魔
- **持续愿望** — 有些愿望不会执行一次就结束，会持续影响世界
- **诚实的结果反馈** — 成功/部分成功/无法执行/失败，不假装完成
- **模组研究** — 自动扫描已安装模组，支持 CurseForge/Modrinth/Web
- **进度系统** — 独立"许愿柳"进度页面，含隐藏挑战
- **影视化呈现** — 拆封动画、滤镜、音乐、加载提示

---

## 安装要求 / Requirements

- **Minecraft** 1.20.1
- **Forge** 47.4.22+
- **Java** 17
- **GeckoLib** 4.8.4（必须自行安装 / required — install separately）

---

## AI 配置 / AI Configuration

支持 DeepSeek、Ollama、LM Studio 以及任何 OpenAI 兼容 API。
**模组不自带免费 AI 服务**，你需要自己提供兼容的 API 端点。

---

## 已知限制 / Known Limitations

- AI 理解效果取决于所选模型
- 特别复杂的愿望可能只能部分实现
- 无限世界级修改会被性能保护限制
- 模组兼容性取决于目标模组
- AI 响应需要一定等待时间

---

## 安全提醒 / Safety

⚠ 建议在使用极端愿望前**备份世界**。某些荒诞愿望可能生成大量实体、修改方块或建立持久世界规则。

API Key **本地保存**，不会通过网络同步或写入存档。**不要上传含 API Key 的配置文件或日志。**

---

## Bug 反馈 / Bug Reports

[GitHub Issues](https://github.com/ikunkk02-afk/Wishing-Willow/issues)

⚠ 上传日志前检查是否包含 API Key。
