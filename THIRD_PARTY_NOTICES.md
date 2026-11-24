# 第三方材料

## ECDICT

- 来源：https://github.com/skywind3000/ECDICT
- 上游许可：MIT，Copyright (c) 2025 Linwei
- 随包完整许可：[ECDICT_LICENSE.txt](app/src/main/assets/licenses/ECDICT_LICENSE.txt)
- 本仓库的通用 SQLite 词典由既有 ECDICT 数据整理，保留词语、中文、英文、音标及词形字段；压缩分片与解压结果的校验值记录在 `dictionary/manifest.json`。
- 词典不保证自动判断上下文中的具体含义，使用者可编辑中文注释。

## Gradle Wrapper

Gradle Wrapper 8.9 来自 Gradle 项目，采用 Apache License 2.0。许可副本在 `gradle/wrapper/LICENSE`。

## Android 与 ML Kit

通过 Gradle 引用的 Android、AndroidX、Google ML Kit 及其传递依赖保留各自条款。本项目的 MIT 许可不重新许可这些组件或模型。

- https://developers.google.com/ml-kit/terms
- https://source.android.com/docs/setup/about/licenses

## 试读

`sample_reading.pdf`、`sample_pages.json` 和 `docs/sample_text.txt` 使用为本项目编写的原创示例，随项目按 MIT 发布。没有包含剑桥试题或从私人阅读备份中抽取的段落。
