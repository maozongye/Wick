# Wick

适用于微信的增强模块，支持 Xposed（LSPosed）和 Zygisk 两种加载方式。

基于 [WeKit](https://github.com/Ujhhgtg/WeKit) 的个人二开版本，主要改动方向是降低被微信检测的概率：

- 移除高风险功能（伪造类、批量自动化类、环境伪装类）
- 系统字段替换改为方法 hook，减少可被完整性检查发现的特征
- 安装身份、数据目录、日志前缀、线程名等派生自构建 nonce
- 行为层加入拟人间隔与配额，并对风险信号做熔断

## 构建

```bash
./x build           # debug
./x build --release # release（启用 R8 与字符串加密）
./x run --zygisk    # 构建并安装为 Zygisk 模块
```

`./x` 是 `cargo xtask` 的别名，负责编译 Rust native 库、刷新 `jniLibs/`、调用 Gradle 打包并签名。
**不要直接跑 Gradle**：Gradle 不会编译 Rust，只会打包 `jniLibs/` 里已有的 `.so`。

### 打包可安装 APK 的前提

`jniLibs/` 里的 native 库必须来自 Linux 构建。Windows 上无法编译
`app/src/main/rust/wekit-native`（mp3lame-sys / libvpx 的构建脚本依赖 POSIX 环境）。
CI 的 `native` job 会把产物作为 `wick-native-libs` artifact 上传，可下载后解压到
`app/src/main/jniLibs/` 再用 Gradle 打包。

### 固定包名

默认 `applicationId` 带 nonce 派生后缀，每次构建都不同。用 LSPosed 时包名变化会被识别为新模块、
作用域需要重新勾选，因此验证阶段建议：

```bash
./gradlew :app:assembleStandardDebug -PwekitStablePackage=true
```

### 文件日志

```bash
WEKIT_FILE_LOG=true ./gradlew :app:assembleStandardDebug -PwekitStablePackage=true
```

日志写入 `noBackupFilesDir/<派生目录>/logs/`，仅在需要排查问题时开启——明文日志本身是一种特征。

## 版本支持

微信 8.0.65 – 8.0.78，各版本 APK 下载地址见 [tooling/wechat-apk-sources.md](tooling/wechat-apk-sources.md)。

## 许可

GPL-3.0，见 [LICENSE](LICENSE)。本仓库包含上游 WeKit 的代码，同样受该许可约束。
