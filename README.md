# RelaySwitch（小米 / Android 原生多中转站 AI 客户端）

参考电脑端 [CC Switch](https://github.com/farion1231/cc-switch) 的多中转站管理思路，为 Android（小米 HyperOS / MIUI）打造的原生 Kotlin + Jetpack Compose 客户端。

## 核心功能

- **多中转站一键切换**：点击顶部胶囊即可切换不同中转站与默认模型，支持新增、编辑、删除。
- **三协议原生直连（无 CORS 跨域限制）**：
  - OpenAI 兼容接口（`/v1/chat/completions`，含 `reasoning_content` 兼容）
  - Claude 原生接口（`/v1/messages`）
  - Gemini 原生接口（`:streamGenerateContent?alt=sse`）
- **SSE 流式输出 & 随时中止**：基于 OkHttp 流式读取，支持生成过程中点击停止。
- **图片上传对话**：调用 Android 原生 Photo Picker（无需申请相册存储权限），自动等比缩放至 1280px 并压缩为 JPEG Base64，按对应协议的多模态格式发送。
- **Cloudflare Workers 配置云同步（可选）**：附带 `cloudflare-worker/` 脚本，可将中转站列表同步到 Cloudflare KV，避免在手机上手动输入长 API Key。

## 一、在电脑上打包 APK

1. 安装并打开 [Android Studio](https://developer.android.com/studio)。
2. 选择 **File -> Open**，选中解压后的 `RelaySwitch` 文件夹。
3. 等待 Gradle 自动同步依赖（首次同步需要联网，约 1~3 分钟）。
4. 连接小米手机（开启 USB 调试）直接点击 **Run**，或点击菜单 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** 生成安装包。
5. 生成的 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`。

## 二、小米手机安装自打包 APK 注意事项

1. 将 `app-debug.apk` 发送到小米手机（通过数据线、LocalSend 或微信/QQ 传输）。
2. 点击安装时，若小米澎湃 OS（HyperOS）弹出“安全守护 / 未经小米应用商店审核”提示，选择**继续安装**或点击右上角设置临时关闭**安全守护**即可完成安装。
3. 打开 App 后，点击顶部胶囊进入中转站面板，填入你在电脑端使用的中转站 `Base URL`、`API Key` 与模型名即可开始对话。

## 三、部署可选的 Cloudflare Workers 云同步

如果你希望在电脑上维护一份中转站 JSON，并在小米手机上一键拉取：

```bash
cd cloudflare-worker
npm install -g wrangler
wrangler login
wrangler kv namespace create RELAY_KV
# 将返回的 id 填入 wrangler.toml 中的 id 字段
wrangler secret put SYNC_TOKEN
# 输入你自定义的同步密码（例如一串随机字符）
wrangler deploy
```

部署完成后，在手机 App 的中转站面板底部填入你的 Worker 地址（如 `https://relayswitch-sync.xxx.workers.dev`）和 `SYNC_TOKEN`，即可一键推送或拉取配置。
