# J2ME Loader HD

[![Android](https://img.shields.io/badge/Android-4.0%2B-brightgreen)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/fuyijian/J2ME-Loader-HD?label=release)](../../releases/latest)

Android 上的 J2ME（Java 手机游戏）模拟器，基于 [nikita36078/J2ME-Loader](https://github.com/nikita36078/J2ME-Loader) 的增强分支。
支持绝大多数 2D/3D 游戏（含 Mascot Capsule 3D）、虚拟键盘、按应用独立配置。

官方版已经很完整了。这个分支只做两件它没做的事：**让画面和文字真正清晰**，以及**把老游戏跑得快一点**。

## 一、文字为什么糊，这版怎么解决

模拟器的传统做法是把 1× 的画面直接放大到手机屏幕。240×320 的老游戏放在 1080p 手机上，一行 12px 的汉字
被拉大四五倍，边缘必然发毛——这是放大，不是清晰。

本分支改成与 PC 版 FreeJ2ME「HD 模式」等效的做法：**整帧按 N 倍分辨率渲染，上屏那一刻才缩回**。
文字因此是真正的 N 倍光栅化，而不是把 1× 的抗锯齿结果拉大。倍率可自动（按屏幕算，尽量 1:1 映射），
也可手动锁定 2×~10×。

## 二、与官方版、JL-Mod 的差别

| 对比项 | **本分支（HD）** | 官方 J2ME Loader | JL-Mod |
| --- | :---: | :---: | :---: |
| 整帧超采样（文字/图形真高清） | ✅ | ❌ | ❌ |
| 自动倍率（按屏幕自适应，含 OOM 降级） | ✅ | ❌ | ❌ |
| 游戏速度 2×/3×/4×（可实时切换） | ✅ | ❌ | ❌ |
| 自定义工作目录（Android 11+ 可用） | ✅ | ❌ | ✅ |
| 着色器（PPSSPP 格式） | ✅ | ✅ | ✅ |
| MIDI 音色库（DLS/SF2） | ✅ | ✅ | ✅ |
| Mascot Capsule 3D | ✅ | ✅ | ✅ |
| 背景皮肤 skins / 一键移植 APK | ❌ | ❌ | ✅ |
| 安装现代 JDK 编译的移植游戏 | ✅ | ❌ | 视版本 |

> 最后一项：现代 JDK 编译的移植游戏（class 版本 ≥ 52）在官方版上会报 `Dexing error`
> （`SimException: stack: overflow`），本分支已在 `dexlib` 中修复；反过来，Java 1.1 时代的老游戏
> 也不会再因为栈帧校验失败而一启动就闪退。

## 三、主要改动

1. **整帧超采样**：屏幕缓冲按 N 倍分配，`Graphics` 内部整体缩放，上屏时整帧缩回；
   自动倍率受 4096 边长 / 8M 像素上限保护，内存不足自动降级。
2. **绘制路径全面适配**：`drawImage`、`drawRegion`、`drawRGB`、`copyArea`、`getPixels`、
   `Image.createImage(Image,…)`、M3G 视口、`GameCanvas` 缓冲、游戏自建的离屏位图，
   全部按「逻辑坐标 ↔ 物理像素」分离处理——这是让"个别游戏错位、贴图放大"消失的关键。
3. **线条与边框精确化**：轴对齐线与矩形边框改用填充矩形绘制，消除高倍率下的虚线与三段色带。
4. **游戏速度**：把游戏的系统时钟与 `Thread.sleep` 接到虚拟时钟上，实现 2×/3×/4× 加速，
   游戏内菜单随时切换、立即生效；`java.util.Timer` 驱动的动画同样受益。
5. **可自定义工作目录**：通过「所有文件访问」权限真正支持自定义目录（官方版在 Android 11+ 上禁用了它）。
6. **修复部分游戏无法安装**：`dexlib` 的 ASM 插桩丢弃了 StackMapTable 却未重建，改为保留/重算栈帧。

实现细节与完整的适配清单见 [README-HD.md](README-HD.md)。

## 四、下载与安装

到 [Releases](../../releases/latest) 下载最新 APK。

- 包名 `ru.playsoftware.j2meloader.hd`，**可与官方 J2ME Loader 共存**，不会互相覆盖
- 首次启动需授予存储权限；想自定义游戏目录时，还需在系统设置里开启「所有文件访问」
- 支持架构：arm64-v8a / armeabi-v7a / x86 / x86_64

## 五、使用建议

**画面**

- 想最清晰：游戏配置 → 字体 → 超采样 → **自动**。自动档会尽量让渲染分辨率贴合屏幕
- 觉得卡：把「图形模式」改为 **OpenGL ES（硬件）**，最终缩放交给 GPU；或把超采样降到 2×

**速度**

- 游戏内菜单 → **游戏速度** → 选 2×/3×/4×，立即生效，选 1× 恢复原速
- 标题带「（未启用）」说明这款游戏导入得比较早：**长按图标重新安装一次**即可启用
- 加速靠的是"让游戏感知的时间走得更快"，所以只对**按时间推进**的游戏有效；
  纯帧驱动、或本来就跑满速的游戏不会有变化

**其他**

- 切换工作目录后游戏列表变空是正常的：列表数据库跟着目录走，新目录需重新导入
- 个别游戏自带点阵字库，那类文字其实是图片，超采样只能让图形变清晰，文字本身不会变——
  官方版与 PC 版同理

## 六、构建

需要 JDK 17+、Android SDK（compileSdk 37 / `android-37.0`）、Gradle 9.6：

```bash
./gradlew assembleOpenDebug     # 调试包
./gradlew assembleOpenRelease   # 发布包（R8 + 单 dex，体积约减半）
```

发布包签名：在项目根目录放 `keystore.properties`（已被 `.gitignore` 排除）：

```properties
keyAlias=your_alias
keyPassword=your_password
storeFile=/absolute/path/to/keystore.jks
storePassword=your_store_password
```

## 七、上游信息与致谢

系统要求：Android 4.0+
兼容性列表（上游维护）：[触屏游戏](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Touchscreen)) ·
[非触屏游戏](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Non-Touchscreen)) ·
[已知问题游戏](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Java-Games-with-Bugs)

上游小贴士：开启图像过滤在部分机型上会明显降低性能，卡顿时可关闭；画面闪烁可尝试打开「即时处理模式」。

超采样部分的改动整理成了一份可直接应用的补丁：
[`patches/hd-supersampling.patch`](patches/hd-supersampling.patch)（基线为上游 2026-10-24 的 `master`）。
之后的音效优化与游戏速度等改动见提交历史。

本项目基于 [nikita36078/J2ME-Loader](https://github.com/nikita36078/J2ME-Loader)，
并参考了 [woesss/JL-Mod](https://github.com/woesss/JL-Mod) 与
[mobeiqingkong/J2ME-Loader](https://github.com/mobeiqingkong/J2ME-Loader) 在超采样上的实现思路。

> Copyright 2017-2026 Nikita Shakarun.
> Licensed under the [Apache License, Version 2.0](LICENSE).
> 本仓库为上游的修改版本：保留全部原始版权声明，修改内容见本文件与 git 提交记录。
