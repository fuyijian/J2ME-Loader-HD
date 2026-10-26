# J2ME Loader HD

[![Android](https://img.shields.io/badge/Android-4.0%2B-brightgreen)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/fuyijian/J2ME-Loader-HD?label=release)](../../releases/latest)

Android 上的 J2ME（Java 手机游戏）模拟器，基于 [nikita36078/J2ME-Loader](https://github.com/nikita36078/J2ME-Loader) 的增强分支。
支持绝大多数 2D/3D 游戏（含 Mascot Capsule 3D）、虚拟键盘、按应用独立配置。

**这个分支只专注一件事：让模拟出来的画面和文字真正清晰，并补齐日常使用上的短板。**

## 它解决了什么问题

模拟器传统做法是把 1× 的画面直接放大到手机屏幕，文字边缘必然发虚——尤其是在 240×320 的老游戏上，
一行 12px 的汉字被放大 4~5 倍，糊成一片。

本分支改成和 PC 版 FreeJ2ME「HD 模式」等效的做法：**整帧按 N 倍分辨率渲染，上屏时才缩回**。
于是文字是真正的 N 倍光栅化，而不是把 1× 的抗锯齿结果放大。

| 对比项 | **本分支（HD）** | 官方 J2ME Loader | JL-Mod |
| --- | :---: | :---: | :---: |
| 整帧超采样（文字/图形真高清） | ✅ | ❌ | ❌ |
| 自动倍率（按屏幕自适应，含 OOM 降级） | ✅ | ❌ | ❌ |
| 自定义工作目录（Android 11+ 可用） | ✅ | ❌ | ✅ |
| 游戏倍速（2×/3×/4×） | ✅ | ❌ | ❌ |
| 着色器（PPSSPP 格式） | ✅ | ✅ | ✅ |
| MIDI 音色库（DLS/SF2） | ✅ | ✅ | ✅ |
| Mascot Capsule 3D | ✅ | ✅ | ✅ |
| 背景皮肤 skins / 一键移植 APK | ❌ | ❌ | ✅ |
| 安装现代 JDK 编译的移植游戏 | ✅ | ❌ | 视版本 |

> 「安装」这一项：现代 JDK 编译的移植游戏（class 版本 ≥ 52）在上游会报 `Dexing error`
> （`SimException: stack: overflow`），本分支已在 `dexlib` 中修复。

## 主要改动

1. **整帧超采样**：屏幕缓冲按 N 倍分配，`Graphics` 内部 `canvas.scale(N, N)`，上屏时整帧缩回；
   自动倍率按「显示区 ÷ 逻辑尺寸」取整，受 4096 边长 / 8M 像素上限与 OOM 自动降级保护。
2. **绘制路径全面适配**：`drawImage` / `drawRegion` / `drawRGB` / `copyArea` / `getPixels` /
   `Image.createImage(Image,…)` / M3G 视口 / `GameCanvas` 缓冲 / 游戏自建的离屏位图，
   都按「逻辑坐标 ↔ 物理像素」分离处理，避免个别游戏错位或异常放大。
3. **线条与边框精确化**：轴对齐线与矩形边框改用填充矩形绘制，消除 2× 下的虚线与三段色带。
4. **可自定义工作目录**：通过「所有文件访问」权限真正支持自定义目录（官方在 Android 11+ 上禁用了此功能）。
5. **游戏倍速**：安装时把游戏的 `System.currentTimeMillis()` / `Thread.sleep()` / `Timer` 重定向到虚拟时钟，
   配合设置里的「游戏速度」实现 2×/3×/4× 加速（修改后需重新导入游戏）。
6. **修复部分游戏无法安装**：`dexlib` 的 ASM 插桩丢弃了 StackMapTable 却未重建，改为重算栈帧后即可正常转换。

详细的实现说明与适配清单见 [README-HD.md](README-HD.md)。

## 下载与安装

到 [Releases](../../releases/latest) 下载最新 APK。

- 包名 `ru.playsoftware.j2meloader.hd`，**可与官方 J2ME Loader 共存**，不会覆盖原版
- 首次启动需授予存储权限；若想自定义游戏目录，还需在系统设置里开启「所有文件访问」
- 架构：arm64-v8a / armeabi-v7a / x86 / x86_64

## 使用建议

- **开启超采样**：游戏配置 → 字体 → 超采样 → 选「自动」。自动档会尽量让渲染分辨率贴合屏幕，最清晰
- **觉得卡**：把「图形模式」改成 **OpenGL ES（硬件）**，最终缩放交给 GPU；
  或把超采样倍率从自动降到 2×（填充量约为 4× 的四分之一）
- **切了工作目录后列表变空**：正常现象。列表数据库保存在工作目录内，新目录需要重新导入游戏
- **想加速**：设置 → 游戏速度 → 2×/3×/4×。**必须先重新导入游戏**（加速是在安装转换时改写的）
- **个别游戏用了自带的点阵字库**：这类文字是图片，超采样只能让图形变清晰，文字本身不会变；
  官方版和 PC 版同样如此

## 构建

需要 JDK 17+、Android SDK（compileSdk 37 / `android-37.0`）、Gradle 9.6：

```bash
./gradlew assembleOpenDebug     # 调试包
./gradlew assembleOpenRelease   # 发布包（R8 + 单 dex，体积约减半）
```

发布包签名：在项目根目录放 `keystore.properties`（已在 `.gitignore` 中排除）：

```properties
keyAlias=your_alias
keyPassword=your_password
storeFile=/absolute/path/to/keystore.jks
storePassword=your_store_password
```

## 上游信息

系统要求：Android 4.0+
游戏兼容性（上游维护）：[触屏游戏](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Touchscreen)) ·
[非触屏游戏](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Non-Touchscreen)) ·
[已知问题游戏](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Java-Games-with-Bugs)

本项目的功能改动也整理成一份可直接应用的补丁：
[`patches/hd-supersampling.patch`](patches/hd-supersampling.patch)
（基于上游 `master` 2026-10-24，17 个文件，已验证可直接 `git apply`）。欢迎上游取用。

上游小贴士：开启图像过滤在部分机型上会明显降低性能，卡顿时可关闭；
画面闪烁可尝试打开「即时处理模式」。

## 致谢与许可

本项目基于 [nikita36078/J2ME-Loader](https://github.com/nikita36078/J2ME-Loader)，
并参考了 [woesss/JL-Mod](https://github.com/woesss/JL-Mod) 与
[mobeiqingkong/J2ME-Loader](https://github.com/mobeiqingkong/J2ME-Loader) 在超采样上的实现思路。

> Copyright 2017-2026 Nikita Shakarun.
> Licensed under the [Apache License, Version 2.0](LICENSE).
> 本仓库为上游的修改版本：保留全部原始版权声明，修改内容见本文件与 git 提交记录。
