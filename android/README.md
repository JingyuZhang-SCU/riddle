# riddle-android — 汤姆·里德尔的日记，在你的安卓平板上

简体中文 | [English](../README.md)

把 [MaximeRivest/riddle](https://github.com/MaximeRivest/riddle)——reMarkable Paper Pro 上的汤姆·里德尔日记——移植到普通 Android 平板的原生应用。

用触控笔在纸上写字，停笔片刻，墨迹被纸"吸走"——页面想了一会儿，Tom 的回答以手写字体逐字浮现。没有发光的屏幕 UI，没有键盘，没有聊天气泡，只有纸和墨。

> 原版为 reMarkable Paper Pro（墨水屏）打造。本移植版面向 **小米平板 6 Pro** 等 Android 平板，以高刷 LCD 上流畅的动画复刻同样的体验。

## ✨ 功能

- **压感书写**：直接读取触控笔的压感与笔迹（已适配小米焦点笔；识别到笔后自动拒收手指，防手掌误触）
- **吸墨**：停笔 2.8 秒，整页墨迹渐渐被纸吸走，随后把页面交给 Tom
- **手写回信**：Tom 的回答以你指定的字体逐字浮现，每字微带抖动，像手写的呼吸感
- **日记记得一切**：每页对话（你的手写转写 + Tom 的回答）都留在本机；最近几轮会注入上下文，Tom 记得你昨天写过什么
- **往事**：点 ☰ 翻阅全部历史页，点按展开完整回答，长按撕下一页，也可以烧掉整本
- **字体由你决定**：应用不分发任何字体，在设置里导入你喜欢的 .ttf / .otf（如霞鹜文楷等开源字体），点选即换
- **翻页即翻篇**：落笔新的一笔，旧回答随之消失，纸永远是新的

## 🔧 构建

1. 克隆本仓库，用 Android Studio（Hedgehog 或更新版本）打开；
2. 用 Android Studio 打开，等待 Sync 完成（Gradle wrapper 已随仓库提供，首次会自动下载 Gradle 8.7 与依赖）；
3. `Build > Generate App Bundles or APKs > Generate APKs`，产物在 `app/build/outputs/apk/debug/`；
4. 安装到平板（USB 调试直接运行，或把 APK 传到平板安装）。

需要 Android 8.0（API 26）以上、带压感触控笔的设备体验最佳（无笔设备也可用手指书写）。

## ⚙️ 配置

打开应用，点右上角 ⚙，填三项：**接口地址、API Key、模型名**。任意 OpenAI 兼容且支持图片输入的接口均可。

### 推荐：小米 MiMo-V2.5

本项目推荐使用 [小米 MiMo API 开放平台](https://mimo.mi.com/) 的 **`mimo-v2.5`**——目前最便宜的原生全模态模型之一（按量计费约输入 ¥3/百万 tokens、输出 ¥6/百万 tokens），原生理解图像，读手写体很稳：

| 设置项 | 值 |
|--------|-----|
| 接口地址 | `https://api.xiaomimimo.com/v1` |
| API Key | 在 [控制台](https://platform.xiaomimimo.com/#/console/api-keys) 用小米账号创建（`sk-` 开头） |
| 模型名 | `mimo-v2.5` |

> 注意选 `mimo-v2.5`（支持图像理解）而不是 `mimo-v2.5-pro`（后者不支持图片输入）。

### 其他可用接口

- 智谱 `glm-4v-flash`（有免费额度）：`https://open.bigmodel.cn/api/paas/v4`
- 阿里云 `qwen-vl-plus`：`https://dashscope.aliyuncs.com/compatible-mode/v1`
- OpenAI / OpenRouter / 本地推理服务等任何 OpenAI 兼容端点

## 🪄 玩法

- 写一句话，Tom 会用写信的语言回应你——他只认得你写在纸上的字
- 连续对话几页后问他"你还记得我昨天写了什么吗"
- 从 ⚙ 里换一枚手写字体，Tom 的笔迹会跟着变
- 长按往事里的一页把它撕掉，Tom 就再也不记得这段对话

## 🔐 隐私

- 每写完一页，页面被压缩成灰度 PNG 发送给你自己配置的模型接口，除此之外没有任何数据离开设备
- 对话记忆只存在应用私有目录，删除应用即彻底清除；不收集任何遥测
- 字体文件由你自行导入，请自行确认其授权许可

## 🧱 架构速览

| 原版 (Rust) | 本移植 (Kotlin) |
|------|------|
| evdev 原始触控笔事件（4096 级压感） | `MotionEvent` 压感 + 历史采样点 |
| qtfb / quill 显示后端（xovi、墨水屏波形引擎） | 自定义 View + 双缓冲 Bitmap + 144Hz LCD |
| 字体→Zhang-Suen 细化→逐帧重放 | 字体排版 + 逐字符浮现 + 微旋转抖动 |
| oracle 进程（纯 Rust HTTPS，流式 SSE） | OkHttp 流式 SSE + 增量排版（动画跨数据块连续） |
| `riddle-data/memories` | 应用私有目录 `memories/` 下的 JSON 文件 |

## 📄 License

MIT（沿用 [riddle](https://github.com/MaximeRivest/riddle) 与 [quill](https://github.com/MaximeRivest/quill)）。本项目不分发任何字体——设置里的字体均由用户自行导入，请自行确认所导入字体的授权许可。与 reMarkable AS 及小米无关。

*汤姆·里德尔的日记属于每一个打开它的人。别告诉他太多你的秘密。*
