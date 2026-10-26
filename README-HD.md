# J2ME Loader HD

基于 [nikita36078/J2ME-Loader](https://github.com/nikita36078/J2ME-Loader)（上游 `master`，2026-10 快照）的增强分支，
主要目标是**让模拟出来的画面和文字真正清晰**，并补齐一些日常使用上的短板。

> 超采样的实现思路参考了 [woesss/JL-Mod](https://github.com/woesss/JL-Mod) 与
> [mobeiqingkong/J2ME-Loader](https://github.com/mobeiqingkong/J2ME-Loader)（感谢）。

## 特性

### 1. 整帧超采样（核心）

传统做法是把 1× 画面放大到屏幕，文字必然发虚。这里改成和 FreeJ2ME PC 版 HD 模式等效的做法：

- 屏幕缓冲按 **N 倍**分配，`Graphics` 内部 `canvas.scale(N, N)`，所有逻辑坐标落到 N 倍像素上
- 上屏时整帧缩回，于是**文字是真正的 N 倍光栅化**，而不是把 1× 的抗锯齿结果放大
- **自动倍率**：按「显示区 ÷ 逻辑尺寸」取整，尽量做到 1:1 映射；受 4096 边长 / 8M 像素上限约束，内存不足自动降级
- 配置路径：**游戏配置 → 字体 → 超采样**（自动 / 关闭 / 2×~10×）

适配了所有会接触像素坐标的路径，避免个别游戏出现错位或放大：

| 路径 | 处理方式 |
| --- | --- |
| `Graphics.drawImage` / `drawRegion` / `flush` | 超采样源图取物理区域，目标仍用逻辑坐标 |
| `Graphics.drawRGB` | 改用 Bitmap 形式（`int[]` 重载会忽略画布变换） |
| `Graphics.copyArea` | 源区域 ×倍率，经复用暂存位图中转（地图滚动的整屏平移） |
| `Graphics.getPixels` / `Image.getRGB` | 读取时缩放回逻辑像素 |
| `drawLine` / `drawRect` | 轴对齐线与边框改用填充矩形，避免描边跨 2~3 行形成色带 |
| `GameCanvas` 自有缓冲 | 跟随屏幕倍率惰性重建（否则游戏内容仍画在 1× 上） |
| 游戏自建的可变 `Image` | 跟随倍率（≤3×，带面积上限与 OOM 回退），预渲染缓存里的文字也清晰 |
| `Image.createImage(Image,…)` | 按物理区域截取子图，结果保持倍率 |
| M3G `Graphics3D.bindTarget` | 视口按倍率缩放，避免 3D 只画在左上角 |

### 2. 可自定义工作目录

上游在 Android 11+ 上禁用此功能（点「工作目录」无反应，且启动时忽略已保存路径）。
现在通过「所有文件访问」权限支持真正的自定义目录，游戏内的外部存储也映射到真实 `/sdcard`。

### 3. 修复部分游戏安装失败

现代 JDK 编译的 J2ME 移植游戏（class 版本 ≥ 52）安装时报 `Dexing error`：`dexlib` 的 ASM 插桩
丢弃了 StackMapTable 却没有重建，导致 `dx` 无法推断栈帧（`SimException: stack: overflow`）。
改为 `COMPUTE_FRAMES` + 宽容的 `getCommonSuperClass` 后即可正常转换。

### 4. 其他

- 游戏列表网格一行可容纳 6 个汉字（行内边距 10dp → 垂直 5dp / 水平 2dp）

## 构建

环境：JDK 17+、Android SDK（compileSdk 37 / platform `android-37.0`）、Gradle 9.6

```bash
./gradlew assembleOpenDebug     # 调试包（多 dex，方便排查）
./gradlew assembleOpenRelease   # 发布包（R8 + 单 dex，体积约为 debug 的一半）
```

如需签名发布包，在项目根目录放 `keystore.properties`（该文件已被 `.gitignore` 排除）：

```properties
keyAlias=your_alias
keyPassword=your_password
storeFile=/absolute/path/to/keystore.jks
storePassword=your_store_password
```

## 已知限制

- `Mascot Capsule` / 摩托罗拉 3D 渲染路径尚未验证（它们不直接读取位图尺寸，暂无适配点）
- 超采样会显著增加填充量：建议开启「图形模式 → OpenGL ES」，把最终缩放交给 GPU
- 通过「工作目录」切换目录后，游戏列表数据库位于该目录下，新目录需要重新导入游戏

## 许可

沿用上游的 Apache License 2.0。本仓库为上游的修改版本，按 Apache-2.0 要求：

- 保留所有原始版权声明
- 修改内容见本文件与 git 提交记录
- 上游项目：[nikita36078/J2ME-Loader](https://github.com/nikita36078/J2ME-Loader)
