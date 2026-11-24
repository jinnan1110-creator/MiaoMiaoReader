# 构建与签名

## 开发版

JDK 21、Android SDK 35、Build Tools 35.0.0、Gradle Wrapper 8.9。Android Studio 或 SDK 管理器负责安装工具并展示 SDK 许可。

```sh
chmod +x gradlew
./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest
```

`prepareDictionary` 会校验 `dictionary/manifest.json` 中的分片及数据库 SHA-256，再解压到构建目录。源码目录不会生成或保存使用者的阅读数据。

开发包使用 Android 默认开发签名，包名带 `.dev`，不覆盖正式版。

## 发行版

正式版包名仍为 `com.jinnan.miaomiaoreader`，公开首版版本为 `0.2.2`，versionCode 为 `4`。发行私钥不在公开仓库中。

维护者可在本机配置四个环境变量后执行 `./gradlew assembleRelease`：

| 变量 | 内容 |
| --- | --- |
| `MM_READER_KEYSTORE` | 本机私钥文件的绝对路径 |
| `MM_READER_STORE_PASSWORD` | 存储密码 |
| `MM_READER_KEY_ALIAS` | 签名别名 |
| `MM_READER_KEY_PASSWORD` | 私钥密码 |

未提供完整签名配置时，Release 输出为未签名包。自行编译的包只有使用相同私钥，才能覆盖同包名的官方版本；不要将私钥或密码提交到仓库。

## 验证

```sh
node verification/verify-reader.cjs
python3 tools/check_public_source.py
./gradlew testDebugUnitTest
```

手势脚本覆盖轻点、999/1000 毫秒边界、选短语、双向翻页、滚动、双指操作与取消事件。Android 设备检查另运行 `./gradlew connectedDebugAndroidTest`；脚本检查不代替真机触控检查。
