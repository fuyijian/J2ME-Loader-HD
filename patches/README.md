# 补丁：整帧超采样 + 相关修复

这份补丁把本项目相对**上游 master** 的全部功能改动打包成一个文件，
方便上游作者或其他分支取用，无需对齐本仓库的提交历史。

- **基线**：`nikita36078/J2ME-Loader` 的 master，提交 `76383eb`（2026-10-24）
- **范围**：17 个文件，约 500 行增删，只包含模拟器代码
  （`javax.microedition.*`、`com.siemens.*`、`dexlib`、配置界面、设置、Manifest）
- **不包含**：README、`.gitignore`、`app/build.gradle` 的本机适配（Java 17 / 应用名）、TinySoundFont 子模块内容

## 应用方式

在干净的上游工作区执行：

```bash
git checkout 76383eb          # 或更新的 master（较新提交改过 SettingsFragment，可能需手动合并这一处）
git apply patches/hd-supersampling.patch
```

已验证：在 `76383eb` 上 `git apply` **可直接应用**，无需 `--3way`。

## 内容概要

1. 屏幔缓冲 N 倍渲染 + 上屏缩回（`Image` / `Graphics` / `Canvas` / `CanvasWrapper`）
2. 全部绘制路径适配超采样（drawRegion / drawRGB / copyArea / getPixels / flush / 子图 / M3G 视口）
3. `GameCanvas` 自有缓冲、游戏自建离屏位图跟随倍率
4. 线条与边框精确化（轴线/边框改填充矩形；`strokeWidth` 按局部单位设为 1）
5. 可自定义工作目录（`MANAGE_EXTERNAL_STORAGE` + 文件选择器权限判断）
6. `dexlib` 插桩重算栈帧（修复现代 JDK 编译游戏的安装失败）

详细说明见仓库根目录 [README-HD.md](../README-HD.md)。
