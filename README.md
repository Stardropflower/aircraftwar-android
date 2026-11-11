# 飞机大战 · Android 版（AircraftWar-Android）

> 《软件构造实践》第二学期实验项目 —— 把 Windows 版飞机大战迁移到 Android，并在其上增加网络对战等功能。
>
> 课程：哈尔滨工业大学（深圳）· 软件构造实践 · 2026 秋
> 授课内容：Android 开发技术（课件 `实验1.pdf`，35 页；原件不入库，要点见 [`docs/00-课程要求摘要.md`](docs/00-课程要求摘要.md)）

## 项目定位

本仓库是**实验项目的唯一代码仓**，承接 Windows 版（Java Swing）的全部玩法与设计，
目标是交付一个可在 Android 真机/模拟器上运行的飞机大战，并在其上完成课程要求的网络功能。

- Windows 版源仓库：`https://github.com/Stardropflower/aircraftwar`（private，Java Swing + Supabase 云端榜）
- 迁移策略：**游戏内核（规则/状态机/对象模型）整体复用，只替换平台相关的四类边界**
  —— 渲染、主循环、输入、资源与存储/网络。详见 [迁移分析](docs/02-迁移分析.md)。

## 本学期实验路线图

| 实验 | 学时 | 内容 | 提交物 | 状态 |
|------|------|------|--------|------|
| 一 | 2 | Android 开发技术 | **任务书** | 🟡 草稿已就绪，待补小组信息 → [任务书](docs/01-任务书.md) |
| 二 | 4 | 代码迁移 | 程序 | ⬜ 未开始 |
| 三 | 4 | 界面与音效 | 程序 | ⬜ 未开始 |
| 四 | 4 | 排行榜与存储 | 程序 | ⬜ 未开始 |
| 五 | 4 | 网络对战 | 程序 | ⬜ 未开始 |
| 六 | 2 | 扩展功能 | 程序 | ⬜ 未开始 |
| 七 | 4 | 结题验收 | 结题报告 | ⬜ 未开始 |

> 课程说明：本课程是实践类课程，以学生实践为主，**开发功能不受以上内容约束**，可在此基础上开发个人感兴趣内容。
> 因此本项目在最低要求之外，规划了「幽灵竞速」作为加分扩展。

## 技术选型

| 维度 | 选择 | 理由 |
|------|------|------|
| 语言 | **Java**（不用 Kotlin） | 源项目是 Java，可复用部分（实体模型、策略、工厂、观察者、DAO 逻辑）能几乎原样搬过来；改用 Kotlin 会平白增加重写量与两类语言的互操作成本 |
| 渲染 | **SurfaceView + Canvas**，独立渲染线程 | 2D 逐帧重绘的标准做法；沿用 Windows 版已验证的「逻辑 25Hz + 渲染 60fps 插值」架构，手感一致 |
| 主循环 | 独立线程 + `Choreographer` 对齐垂直同步 | 不占用主线程，避免 ANR；这是 Android 上替代 `javax.swing.Timer` 的正解 |
| 输入 | `MotionEvent`（手指拖动跟随） | 触屏替代鼠标；暂停/切弹道改为界面按钮 |
| 音效 | `SoundPool`（短音效）+ `MediaPlayer`（BGM） | 替代 `javax.sound.sampled.Clip` |
| 本地存储 | `SharedPreferences` / `Room` | 替代 Java 对象序列化的 `.dat` 文件 |
| 网络 | **OkHttp + WebSocket**，REST 走 Retrofit | ⚠️ Android **没有** `java.net.http`（源项目现用的 HttpClient），必须换掉 |
| 后端 | 复用现有 Supabase（PostgREST），对战房间另起轻量服务 | 排行榜已成型可直接迁移；实时对战需要长连接，Supabase 不适合做帧同步中转 |

## 目录规划

```
aircraftwar-android/
├── app/src/main/java/edu/hitsz/aircraftwar/
│   ├── core/         # 【可复用】从 Windows 版迁移的游戏内核（实体、策略、工厂、观察者）
│   ├── render/       # 【新写】SurfaceView + 渲染线程 + 插值
│   ├── input/        # 【新写】触屏处理
│   ├── ui/           # 【新写】Activity / 菜单 / 排行榜界面
│   ├── audio/        # 【新写】SoundPool / MediaPlayer 封装
│   ├── data/         # 【新写】本地存储 + 云端 DAO（OkHttp 版）
│   └── net/          # 【新写】WebSocket 对战协议、房间、同步
├── app/src/main/assets/    # 图片与音频（沿用 src/images、src/videos）
├── app/src/main/res/       # 布局、字符串、主题
├── docs/                   # 实验文档
└── README.md
```

## 本地开发环境

> ⚠️ **当前开发机尚未安装 Android 工具链**（未检测到 Android Studio / Android SDK / Gradle / adb）。
> 代码可以编写与版本管理，但**编译、安装、运行、调试必须先在装了 Android Studio 的机器上完成**。

需要准备：

1. **Android Studio**（课程推荐版本：Android Studio Panda）— 自带 JDK 与 Gradle
2. **Android SDK**：`compileSdk 35`、`minSdk 24`（Android 7.0）、`targetSdk 35`
3. **JDK 17**（AGP 8.x 要求；Android Studio 自带 JBR 即可）
4. 一台真机（开启 USB 调试）或 AVD 模拟器

## 文档索引

- [课程要求摘要](docs/00-课程要求摘要.md) — 从 `实验1.pdf` 提炼的实验安排、任务书要求、技术要点
- [任务书](docs/01-任务书.md) — 实验一提交物（草稿，待补小组信息）
- [迁移分析](docs/02-迁移分析.md) — 逐模块的「可复用 / 需重新适配」判定与原因

## 与 Windows 版的关系

Windows 版**继续维护**，作为迁移的对照基准与回退方案：

- 它是本次迁移的**功能规格来源**（玩法、数值、Boss 规则、皮肤解锁等均已成型）
- 迁移过程中若 Android 侧出现规则歧义，以 Windows 版行为为准
- 两者共享同一套 Supabase 表结构，**全球排行榜可跨端互通**（Windows 玩家与 Android 玩家同榜）
