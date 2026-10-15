<div align="center">

# MoviePilotApp

**MoviePilot v3 的第三方安卓客户端** —— 媒体搜索、站点种子检索、季集选择、订阅与下载管理，一个 App 全搞定。

[![Stars](https://img.shields.io/github/stars/KingNuoNuo/MoviePilotApp?style=flat&color=4F46E5)](https://github.com/KingNuoNuo/MoviePilotApp/stargazers)
[![Forks](https://img.shields.io/github/forks/KingNuoNuo/MoviePilotApp?style=flat&color=4F46E5)](https://github.com/KingNuoNuo/MoviePilotApp/network/members)
[![Watchers](https://img.shields.io/github/watchers/KingNuoNuo/MoviePilotApp?style=flat&color=4F46E5)](https://github.com/KingNuoNuo/MoviePilotApp/watchers)
[![Issues](https://img.shields.io/github/issues/KingNuoNuo/MoviePilotApp?style=flat&color=4F46E5)](https://github.com/KingNuoNuo/MoviePilotApp/issues)
[![License](https://img.shields.io/github/license/KingNuoNuo/MoviePilotApp?style=flat&color=4F46E5)](LICENSE)
[![Release](https://img.shields.io/github/v/release/KingNuoNuo/MoviePilotApp?style=flat&color=4F46E5&label=Release)](https://github.com/KingNuoNuo/MoviePilotApp/releases/latest)
[![Last Commit](https://img.shields.io/github/last-commit/KingNuoNuo/MoviePilotApp?style=flat&color=4F46E5)](https://github.com/KingNuoNuo/MoviePilotApp/commits/main)
[![Top Language](https://img.shields.io/github/languages/top/KingNuoNuo/MoviePilotApp?style=flat&color=4F46E5)](https://github.com/KingNuoNuo/MoviePilotApp)
[![Build](https://github.com/KingNuoNuo/MoviePilotApp/actions/workflows/release.yml/badge.svg)](https://github.com/KingNuoNuo/MoviePilotApp/actions/workflows/release.yml)

[⬇️ 下载 APK](https://github.com/KingNuoNuo/MoviePilotApp/releases/latest) · [🐛 反馈问题](https://github.com/KingNuoNuo/MoviePilotApp/issues) · [💡 功能建议](https://github.com/KingNuoNuo/MoviePilotApp/issues)

`Android` `Kotlin` `Jetpack Compose` `Material3` `MoviePilot` `Media Server` `TMDb` `Emby` `Jellyfin` `STRM` `Torrent` `Downloader` `NAS` `Self-hosted`

</div>

---

## 这是什么

给自建 MoviePilot v3 用的手机端。服务器放在 NAS / 软路由 / 家庭服务器上，手机装这个 App，就能搜片、找资源、选季选集、下单下载、盯进度，不用再开电脑开网页。

## 功能

- **媒体搜索** —— TMDb 影视库搜索，海报墙展示；多季剧集支持选季 / 选集（自动拉取季集信息），也可直接按关键词搜种子资源
- **资源检索** —— SSE 流式搜索，实时显示各站点爬取进度与命中数；推荐算法分层排序（整季包 > 多集包 > 单集，组内按 `做种数 / √体积`），一键下载或订阅
- **下载管理** —— 实时进度与速度，暂停 / 继续 / 删除（带确认弹窗），入库结果标记（成功 / 失败 / 等待）
- **订阅管理** —— 订阅列表、海报封面、删除确认
- **认证** —— API 令牌或账号密码登录，JWT 失效自动重登，启动自动探测连接状态
- **应用内更新** —— 直接检查 GitHub Release，新版可下载并唤起系统安装器

## 下载

到 [Releases](https://github.com/KingNuoNuo/MoviePilotApp/releases/latest) 页面下载 `MoviePilotApp-vX.Y.Z.apk` 安装。应用内「设置 → 应用更新」也能直接检测并安装。

## 构建

```bash
./gradlew assembleRelease
```

Release 签名需要 `keystore.properties`（或环境变量 `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_PASSWORD`），文件不入库。推 `v*` tag 会触发 GitHub Actions 自动构建并发布 Release（已开启 R8 代码压缩与资源压缩）。

## 环境要求

- Android 8.0+（minSdk 26）
- 已部署 MoviePilot v3 的服务器

## English

**MoviePilotApp** is a third-party Android client for [MoviePilot](https://github.com/jxxghp/MoviePilot) v3, written in Kotlin with Jetpack Compose and Material 3.

- Search TMDb movies and TV shows, pick seasons / episodes, or search torrent resources by keyword
- Streaming (SSE) site search with live per-site progress and a tiered recommendation ranking (full season > multi-episode > single episode)
- Download manager with live progress and speed, pause / resume / delete, and library-import status
- Subscription management, API-token or password login with automatic token refresh, in-app updates from GitHub Releases

Signed release APKs are published automatically by GitHub Actions on every `v*` tag.

<!-- traffic:start -->

## 📊 访问统计

![近 14 天访问趋势](docs/traffic.svg)

| 指标 | 近 14 天 |
| --- | ---: |
| 页面浏览量 | 2 |
| 独立访客 | 2 |
| 克隆次数 | 69 |
| 独立克隆者 | 35 |

<sub>数据窗口 2026-10-01 ~ 2026-10-14 · 由 GitHub Actions 每日更新于 2026-10-15 · 来源：GitHub Traffic API（仅统计最近 14 天）</sub>

<!-- traffic:end -->