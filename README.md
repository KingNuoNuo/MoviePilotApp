# MoviePilotApp

基于 MoviePilot v3 API 的安卓客户端（Kotlin + Jetpack Compose），实现媒体搜索、资源检索、订阅与下载管理。

## 功能

- **搜索**：TMDb 媒体搜索（海报墙），多季剧集支持选季/选集（自动拉取季集信息），可直接按关键词搜种子资源
- **资源检索**：SSE 流式搜索（实时显示各站点进度与命中数），推荐算法分层排序（整季包 > 多集包 > 单集，组内按 做种数/√体积），一键下载/订阅
- **下载管理**：实时进度/速度，暂停、继续、删除（确认弹窗），入库结果标记（成功/失败/等待）
- **订阅管理**：订阅列表、海报封面、删除确认
- **认证**：API 令牌或账号密码登录，JWT 失效自动重登，启动自动探测连接

## 下载

到 [Releases](../../releases) 页面下载 `MoviePilotApp-vX.Y.Z.apk` 安装。

## 构建

```bash
./gradlew assembleRelease
```

Release 签名需要 `keystore.properties`（或环境变量 `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_PASSWORD`），文件不入库。推 `v*` tag 会触发 GitHub Actions 自动构建并发布 Release。

## 环境要求

- Android 8.0+（minSdk 26）
- 已部署 MoviePilot v3 的服务器
