# 妙妙阅读 · MiaoMiao Reader

在英文原文旁留下自己的中文理解，再带着原句复习生词。

一款面向中文使用者的 Android 英文阅读与标注工具。支持导入 PDF 和 UTF-8 TXT，采用墨绿与暖白配色，阅读时默认收起工具栏。

English: An Android English reader with editable Chinese word annotations, PDF OCR, vocabulary export with source sentences, and word pronunciation.

## 能做什么

- **读自己的材料**：导入 PDF 或 TXT。扫描 PDF 使用随包提供的英文 OCR 模型，识别结果按页缓存；可切换原页核对。
- **减少误触**：快速轻点正文中央显示或收起工具栏；长按单词约 1 秒后松手打开标注，长按后拖动选择短语。向左滑动下一页，向右滑动上一页。
- **把理解写在旁边**：中文注释可修改、单独隐藏，或全部隐藏用于自测。同一个词在不同语境可以保存不同注释。
- **标出阅读重点**：六种高亮颜色；红色单线、双线和波浪线，在单词弹窗中选择。
- **带着语境复习**：导出 CSV，包含词语、中文注释、所在原句、文档、页码、标注样式与颜色，可用 Excel 打开。
- **听单词发音**：调用 Android 系统英语 TTS；设备需安装英语语音。不提供句子朗读。
- **保留阅读成果**：本地保存文档、识别缓存、进度与标注，支持 ZIP 备份和恢复。

## 安装与试用

需要 **Android 8.0 或以上**。正式试用 APK 在 [Releases](https://github.com/jinnan1110-creator/MiaoMiaoReader/releases) 中提供；下载后按手机提示允许安装。

首次打开有一份原创英文试读，可直接尝试长按标注，也可以从书架右上角导入自己的文件。

项目处于早期试用阶段。OCR 可能误识别复杂版式、模糊文字、公式和图表；请使用“查看原页”核对。词典提供候选释义，语境义可手动调整。标注不会写入原始 PDF。

目前专注 Android 手机版，未实现账号、网页版本或多设备同步。

## 想知道你是否愿意用

如果你试用了，欢迎在 [Issues](https://github.com/jinnan1110-creator/MiaoMiaoReader/issues/new/choose) 中选择“使用反馈”，简单告诉我们：

1. 你主要阅读什么材料？
2. 有没有持续使用的意愿？最常用哪个功能？
3. 哪个操作最容易误触，或让你想放弃？

也欢迎用 Star 表示关注。项目未接入自建用户统计；APK 下载次数代表下载事件，不能当作独立用户数。

## 开发与构建

技术栈：Java、Android WebView、SQLite、ML Kit 英文 OCR。使用 Android SDK 35、Build Tools 35.0.0、Gradle 8.9；推荐 JDK 21。

在 Android Studio 中打开项目，安装 SDK 后构建；也可以运行：

```sh
chmod +x gradlew
./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest
node verification/verify-reader.cjs
python3 tools/check_public_source.py
```

Debug 包名为 `com.jinnan.miaomiaoreader.dev`，可与正式版共存，使用独立数据。输出在 `app/build/outputs/apk/debug/`。

词典分片在构建时自动解压并校验 SHA-256，无需自行寻找词库。签名说明见 [BUILDING.md](docs/BUILDING.md)，数据与第三方许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 公开版的数据范围

公开版包含 ECDICT 通用词典及其 MIT 许可、原创英文试读。个人雅思词库、剑桥试读材料、个人阅读数据和发行签名密钥不在仓库中。此前个人版已安装的词库文件会继续保留，不随公开版覆盖。

## 隐私与许可

应用不把文档和标注上传到开发者服务；手机系统备份及系统 TTS 服务由设备设置和所选服务管理。详见 [隐私说明](docs/PRIVACY.md)。

本项目原创代码和原创示例采用 [MIT License](LICENSE)。第三方内容保留各自许可。
