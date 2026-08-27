# riddle — 汤姆·里德尔的日记

用触控笔在纸上写字。停笔片刻，日记本会**把你的墨水吸走**——字迹隐入纸面——纸思考片刻，一段回答便以流畅的手写体一笔一划地浮现出来，随后慢慢淡去。

没有刺眼的屏幕光，没有键盘，没有聊天界面。只有纸上浮现的墨迹。

_这就是[那个演示视频](https://x.com/MaximeRivest)里的日记本。_

**[English](README.md) | 简体中文**

---

## 两种打开日记的方式

| | 📱 **Android 平板** | 📃 **reMarkable Paper Pro** |
|---|---|---|
| 位置 | **本 fork**，[`android/`](android/) 目录 | [上游原版](https://github.com/MaximeRivest/riddle) |
| 屏幕 | 任意 Android 平板，120Hz+ LCD | 电子墨水屏 + 厂商波形引擎 |
| 手写笔 | `MotionEvent` 压感（实测：小米焦点笔） | evdev 原始事件，4096 级压感 |
| 安装 | Android Studio 构建 APK，**无需 root** | 开发者模式 + xovi/AppLoad |
| 大模型 | 任意 OpenAI 兼容视觉接口——**推荐小米 MiMo-V2.5**（最便宜的原生全模态） | OpenAI 兼容接口或 [pi](https://github.com/badlogic/pi-mono) |

---

## 📱 Android 移植版 —— 本 fork 的内容

用 Kotlin 原生重写的日记本，在**小米平板 6 Pro** 上开发实测。riddle 的灵魂全部保留：

- **压感书写** + 手掌拒识——识别到触控笔后自动忽略手指，放心把手搭在屏幕上
- 停笔 2.8 秒后的**吸墨渐隐**，随后整页交给 Tom
- 回答以手写字体**逐字浮现**，每个字微微歪斜，像手写的呼吸感
- **日记记得一切**——每页对话存在本机 JSON 里，最近几轮自动注入上下文
- **往事页**（☰）：翻阅历史对话、长按撕掉一页、或者烧掉整本
- **不内置任何字体**——在设置里导入你喜欢的 .ttf / .otf（默认系统衬线）

无需 root、无需开发者模式、无需墨水屏。自己动手构建：

```sh
git clone https://github.com/JingyuZhang-SCU/riddle
# 用 Android Studio 打开 android/ → Build > Generate APKs
```

完整的构建与配置说明：**[android/README.md](android/README.md)**（中文）

### 快速配置（推荐：小米 MiMo-V2.5）

打开应用 → 右上角 ⚙，三项填法：

| 设置项 | 值 |
|--------|-----|
| 接口地址 | `https://api.xiaomimimo.com/v1` |
| API Key | [platform.xiaomimimo.com](https://platform.xiaomimimo.com/#/console/api-keys) 用小米账号创建 |
| 模型名 | `mimo-v2.5`（注意：`mimo-v2.5-pro` 不支持图片输入） |

可通过邀请码获取Mimo10元免费试用：876VB7
链接：https://platform.xiaomimimo.com?ref=876VB7 （注册后自动填入 · 体验金 40 天有效）

其他可选：智谱 `glm-4v-flash`（免费额度）、阿里云 `qwen-vl-plus`、OpenRouter 等。

---

# 原版 —— reMarkable Paper Pro 上的 riddle

_以下为上游电子墨水屏版的原文档，保持原样。发布版与 remagic 目录请见
[上游仓库](https://github.com/MaximeRivest/riddle)。_

### 🪄 新手？从这里开始

你需要一台处于开发者模式、装有启动器的 **reMarkable Paper Pro**。听起来麻烦，
其实不难——**[remagic](https://github.com/maximerivest/remagic)** 会引导你打开开发者模式，
一条命令完成所有设置。回到这里，把 riddle 放进去，开始给 Tom 写信。

已经会用 xovi + AppLoad？从 [remagic](https://github.com/maximerivest/remagic)
目录安装，[下载预编译包](#install-the-prebuilt-bundle)，或者
[从源码构建](#building)。

### 用 remagic 安装（最简单）

```sh
remagic install riddle     # 校验和验证下载 → AppLoad
remagic config riddle      # 浏览器设置表单（+ 手机扫码）
```

然后在 **AppLoad** 里：点 **Reload**，再点 **The Diary**。写字，然后停笔。
（也可以直接在平板上的 **Store** 应用里安装。）

### 安装预编译包

1. 从[最新 release](https://github.com/MaximeRivest/riddle/releases/latest)
   下载 `riddle-<version>.zip` 并解压：`unzip riddle-*.zip -d riddle`
2. 把文件夹传到平板：
   `scp -O -r riddle root@10.11.99.1:/home/root/xovi/exthome/appload/`
3. 配置 API key：在该文件夹里 `cp oracle.env.example oracle.env`，填入你的
   `RIDDLE_OPENAI_KEY`（任意 OpenAI 兼容 key）。也可以跳过，用
   [pi](#option-b--pi-the-power-path)。
4. 在 **AppLoad** 里：点 **Reload**，再点 **The Diary**。写字，然后停笔。

> ⚠️ **这会修改你的设备。** 预编译包以**接管模式**运行：点开 The Diary
> 会停止整个 reMarkable 系统界面并接管屏幕。**五指同时触屏**即可退出，
> xochitl 会自动重启。它以 root 运行并直接驱动墨水屏引擎。仅在
> **reMarkable Paper Pro**（ferrari, aarch64, OS 3.26–3.27）上测试过，
> 其他型号或系统版本不一定可用，风险自负。与 reMarkable AS 无关。
> 安装任何东西之前先确认 SSH 还能用——万一卡死：
> `ssh root@10.11.99.1 'systemctl start xochitl'`。

## 工作原理

```
 触控笔（evdev 原始事件，完整 4096 级压感，硬件事件频率）
   │ 笔迹
   ▼
 riddle ── 停笔 2.8s → 提交整页 → PNG ──► oracle（常驻 LLM 进程，
   │                                       逐句流式返回回答）
   ▼ 笔画（Dancing Script → 细化为单像素笔路径）
 显示后端
   ├── qtfb        — 窗口模式，运行在 xochitl 内（从源码构建的版本）
   └── quill       — 完全接管：停止 xochitl，直接驱动厂商墨水屏
                     波形引擎，墨迹即时呈现（延迟最低；预编译包
                     用的就是这个）
```

- **本仓库** —— 应用本体（Rust）。触控笔输入、墨水层、手写合成
  （栅格化 → Zhang-Suen 细化 → 笔画追踪 → 动画重放）、oracle 进程管理、
  以及两个显示后端。
- **[Quill](https://github.com/MaximeRivest/quill)** —— 接管式显示宿主（C/C++）。对厂商
  `libqsgepaper.so` 波形引擎的净室实现、MIT 许可的适配层，暴露为极小的
  C ABI（`quill_init` / `quill_buffer` / `quill_swap`），riddle 以
  `--features takeover` 链接。还带几个小 demo（`scribble` 笔到玻璃延迟
  测试，以及地图、图片、GIF 渲染器）。

## 手势

| 这样做 | 会发生 |
|---------|-----|
| 写字，然后停笔 | 日记吸走你的墨水，Tom 回信 |
| 写 *"show me what I wrote about…"* | 被记住的那一页**从纸下浮起**：日期、你自己的笔迹逐笔重写、Tom 的旧回答——都是褪色墨迹。笔尖任意触碰，今天的页面回来 |
| 写 *"what do you remember?"* | Tom 用手写体列一份记忆清单 |
| 翻转笔杆 | 擦除 |
| 画一个大大的 **？** | 呼出内置指南 |
| 五指同时触屏 | 离开日记*（接管模式）* |
| 电源键 | 页面变为 *"The diary sleeps."*，平板休眠；再按一次，从原地醒来*（接管模式）* |

窗口模式（qtfb）下触控屏和电源键归 xochitl 管：从 AppLoad 关闭日记即可。

## 日记会记住

每一页写完都会保存——你真实的笔迹、一份转写、Tom 的回答——日记因此能做三件事：

- **延续对话。** 最近几页随每次请求一起带上，Tom 记得你昨天写了什么
  （两种后端行为一致）。
- **召唤过去。** 用笔问——*"show me the page about the garden"*、
  *"find what I wrote on Tuesday"*——日记会当着你的面把那一页重写出来，
  用你自己的笔迹，带着日期，褪色的墨。没有按钮，没有列表：笔是唯一的接口。
- **凭记忆回答。** *"What do you remember?"* 会得到一份手写的记忆索引。

记忆只存在平板本机，是 `/home/root/riddle-data/memories` 下的纯文本文件
（删掉文件夹日记就失忆；最多保留最近约 400 页）。`oracle.env` 里设
`RIDDLE_MEMORY=off` 可全部关闭——不存储，也不随请求发送额外内容。设置
`RIDDLE_TZ_OFFSET`（距 UTC 的小时数）让记忆日期显示正确。

## oracle（日记里的"灵魂"）

回答来自一个视觉 LLM：它读取提交页面上的手写内容（以 PNG 形式发送）。
启动时从**两种后端**里选一种——有什么用什么：

### 方案 A —— 任意 OpenAI 兼容接口（最简单，零配置）

设一个 API key，riddle 直接对话任何 OpenAI 兼容的 `/chat/completions`
端点。OpenAI、OpenRouter、Groq、本地推理服务——只要说这种"方言"都行。
平板上不需要额外软件。

```sh
export RIDDLE_OPENAI_KEY="sk-..."                       # 必填
export RIDDLE_OPENAI_BASE="https://api.openai.com/v1"   # 可选（默认值）
export RIDDLE_OPENAI_MODEL="gpt-4o-mini"                # 可选；必须支持图片
export RIDDLE_OPENAI_REASONING="low"                    # 仅思考类模型需要
export RIDDLE_OPENAI_MAX_TOKENS="2000"                  # 防跑飞
```

任何有视觉能力的模型都行。在平板上这些配置写在二进制旁边的 `oracle.env`
里（见 `oracle.env.example`，或直接运行 `remagic config riddle`——内置
OpenAI、OpenRouter、Gemini 一键预设）。OpenRouter 示例：

```sh
export RIDDLE_OPENAI_KEY="$OPENROUTER_API_KEY"
export RIDDLE_OPENAI_BASE="https://openrouter.ai/api/v1"
export RIDDLE_OPENAI_MODEL="openai/gpt-4o-mini"
```

思考类模型（Gemini 3.x、o 系列）有两个坑：设
`RIDDLE_OPENAI_REASONING=low` 可以更快见到第一笔（某些供应商在非思考
模型上会拒绝该字段——那就别设）；`RIDDLE_OPENAI_MAX_TOKENS` 要给足——
隐藏的推理 token 也计入额度，上限太低会饿死可见回答。

启动日记前先验证配置：

```sh
riddle --oracle-test path/to/handwriting.png   # 打印流式回答
```

实测设备上首笔延迟约 0.9–1.1 秒。HTTPS 内置于 riddle（纯 Rust，无额外依赖）。

### 方案 B —— pi（高阶玩法）

如果你已经在用 [`pi`](https://github.com/badlogic/pi-mono)，riddle 会启动一个
保温的 `pi --mode rpc` 常驻进程（Node + 你的订阅认证只加载一次），每轮只付
模型延迟。未设置 `RIDDLE_OPENAI_KEY` 时自动启用。默认值（可在 `oracle.env`
覆盖）：pi 位于 `/home/root/node/bin`（`RIDDLE_PI_BIN_DIR`）、provider
`openai-codex`（`RIDDLE_PI_PROVIDER`）、模型 `gpt-5.4-mini`
（`RIDDLE_PI_MODEL`）。

两种后端都逐句流式返回，笔在模型说完之前就开始写字。人格 prompt 在
`src/oracle.rs`。

关于 Tom 的记忆：HTTP 后端下每页都是全新对话——Tom 不记得你的上一页。
pi 的保温会话则记得本次打开日记以来的全部内容（pi 还会把会话持久化在
它自己的数据目录里）。

如果 oracle 无法回答——缺 key、key 被拒、没有 Wi-Fi——Tom 会把原因写在
纸上而不是装模作样，完整错误进日志（`journalctl -u riddle-takeover`）。

## 构建

从 x86_64 交叉编译。两种形态：

### 窗口模式（AppLoad/qtfb）—— 从源码构建

上面的预编译包是接管形态；窗口形态需要自己构建。要求设备上装有
[xovi + AppLoad](https://github.com/asivery/rm-appload)。

```sh
git clone https://github.com/MaximeRivest/riddle
cd riddle
cargo build --release --target aarch64-unknown-linux-gnu
```

把二进制装到 `/home/root/xovi/exthome/appload/riddle/`，配一个
`external.manifest.json`，设 `"qtfb": true` 并把 `"application"` 指向
二进制本身（本仓库里的 manifest 是接管版的——只有 `qtfb` 为 true 时
AppLoad 才会通过 `QTFB_KEY` 递给 riddle 一个窗口）。

### 接管模式（即时墨迹）—— 演示视频里的那个

需要 reMarkable SDK 工具链（`~/rm-sdk-3.26`），因为链接的厂商 Qt 库依赖
它的 glibc；还需要从**你自己的设备**上拉取 `libqsgepaper.so`（专有软件，
此处不分发）：

```sh
# quill 和 riddle 两个仓库并排放置。
cd quill && ./build.sh              # 通过 ssh 从设备拉取 libqsgepaper.so，
                                    # 构建 libquill.so 和各 demo
cd ../riddle && ./build-takeover.sh
./scripts/make-bundle.sh            # 在 dist/riddle/ 里备好 AppLoad 包
```

备好的 `dist/riddle/` 自带一切（二进制、`libquill.so`、启动脚本、
manifest）——拷到 `/home/root/xovi/exthome/appload/riddle/`，或用
`remagic publish dist/riddle` 发布到目录。通过 AppLoad 启动
（`appload-launch.sh`）会 detach 到一个瞬时 systemd 单元：停止 xochitl、
运行日记、退出时**总是恢复 xochitl**——五指触屏或 SIGTERM
（`systemctl stop riddle-takeover`）离开；电源键让日记休眠/原地唤醒。
单元的 stop 钩子保证即使 riddle 死得不体面也会重启 xochitl。万一卡死：
`ssh root@10.11.99.1 'systemctl start xochitl'`。

## 离开设备的数据

- 每页写完后被栅格化为一张小尺寸灰度 PNG，发给你**自己配置**的 oracle——
  除此之外没有任何数据离开平板，无任何遥测。
- PNG（`/tmp/riddle-page.png`）在 oracle 读取后立即删除；设
  `RIDDLE_KEEP_PAGE=1` 可保留最后一页用于调试。
- riddle 从不把回答写盘。但 pi 后端会在它自己的数据目录里保存会话历史——
  HTTP 后端什么都不留。
- Tom 保持入戏是设计使然：人格 prompt（见 `src/oracle.rs`）告诉模型
  它只是这本日记，别的什么都不是。

## 字体

回答的手迹是 [Dancing Script](https://github.com/googlefonts/DancingScript)
（SIL OFL 1.1——见 `fonts/OFL.txt`）。Android 移植版**不捆绑任何字体**——
由用户自行导入。

## 许可证

本仓库所有内容均为 MIT（见 `LICENSE`）。它所 interpose 的厂商库
（`libqsgepaper.so`、Qt）**不包含在内**，必须来自你自己的设备/SDK。
本 fork 的 Android 移植版同为 MIT；不分发任何字体或 API key。

---

*汤姆·里德尔的日记属于每一个打开它的人。别告诉他太多你的秘密。*
