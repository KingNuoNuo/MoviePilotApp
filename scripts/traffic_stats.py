#!/usr/bin/env python3
"""抓取本仓库的 GitHub Traffic 数据，生成 SVG 趋势图并回写 README 统计区块。

只用标准库，无需 pip install。

用法（本地）：
    GH_TOKEN=xxx python scripts/traffic_stats.py --repo KingNuoNuo/MoviePilotApp

用法（GitHub Actions）：见 .github/workflows/traffic.yml
    环境变量 GITHUB_REPOSITORY / GH_TOKEN 自动注入。

注意：GitHub 的内置 GITHUB_TOKEN 没有 traffic API 权限，会返回 403。
      请把 classic PAT（scope: repo）存成 secret TRAFFIC_TOKEN；
      没配 PAT 时脚本会安全跳过并以 exit 0 结束，不会让 CI 变红。
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import ssl
import sys
import urllib.error
import urllib.request

API = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
START = "<!-- traffic:start -->"
END = "<!-- traffic:end -->"

# 与 README 主色调一致的靛蓝
C_BAR = "#4F46E5"
C_UNIQUE = "#A5B4FC"
C_AXIS = "#D0D7DE"
C_TEXT = "#57606A"
C_TITLE = "#1F2328"
C_PANEL = "#F6F8FA"


def get_json(path: str, token: str) -> dict | list:
    req = urllib.request.Request(
        f"{API}{path}",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "traffic-stats",
        },
    )
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=30, context=ctx) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch(repo: str, token: str) -> tuple[list, list]:
    views = get_json(f"/repos/{repo}/traffic/views", token)
    clones = get_json(f"/repos/{repo}/traffic/clones", token)
    return views, clones


def nice_max(value: int) -> int:
    """把坐标轴上界取整成好看的刻度。"""
    if value <= 0:
        return 4
    for step in (1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000):
        for mult in (1, 2, 5, 10):
            bound = step * mult
            if bound >= value:
                return bound
    return value


def build_svg(days: list, total: dict, width: int = 780, height: int = 220) -> str:
    pad_l, pad_r, pad_t, pad_b = 46, 14, 44, 30
    plot_w = width - pad_l - pad_r
    plot_h = height - pad_t - pad_b
    # 上界必须按「单日峰值」算，不能用 14 天累计值，否则柱子全贴在底部
    peak = max((d["views"] for d in days), default=0)
    y_max = nice_max(max(peak, 1))

    n = len(days)
    slot = plot_w / max(n, 1)
    bar_w = min(slot * 0.52, 26)

    def y_of(value: int) -> float:
        return pad_t + plot_h - (value / y_max) * plot_h

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}" role="img" '
        f'aria-label="Repository traffic over the last {n} days">',
        f'<rect width="{width}" height="{height}" rx="10" fill="{C_PANEL}"/>',
        f'<text x="{pad_l}" y="24" font-family="-apple-system,Segoe UI,Helvetica,Arial,sans-serif" '
        f'font-size="14" font-weight="600" fill="{C_TITLE}">'
        f'近 {n} 天访问量 · {total["window"]}</text>',
    ]

    # 网格线 + Y 轴刻度
    for i in range(3):
        value = int(y_max * i / 2)
        y = y_of(value)
        parts.append(
            f'<line x1="{pad_l}" y1="{y:.1f}" x2="{width - pad_r}" y2="{y:.1f}" '
            f'stroke="{C_AXIS}" stroke-width="1" stroke-dasharray="3 3"/>'
        )
        parts.append(
            f'<text x="{pad_l - 8}" y="{y + 4:.1f}" text-anchor="end" '
            f'font-family="-apple-system,Segoe UI,Helvetica,Arial,sans-serif" '
            f'font-size="10" fill="{C_TEXT}">{value}</text>'
        )

    # 柱状图：深色 = 浏览量，内部浅色细柱 = 独立访客（叠放而非并列，否则细柱不可见）
    core_w = max(bar_w * 0.40, 3)
    for i, day in enumerate(days):
        cx = pad_l + slot * i + slot / 2
        x = cx - bar_w / 2
        v_h = plot_h * (day["views"] / y_max)
        if v_h > 0:
            parts.append(
                f'<rect x="{x:.1f}" y="{pad_t + plot_h - v_h:.1f}" width="{bar_w:.1f}" '
                f'height="{v_h:.1f}" rx="2" fill="{C_BAR}"/>'
            )
        u_h = plot_h * (day["uniques"] / y_max)
        if u_h > 0:
            parts.append(
                f'<rect x="{cx - core_w / 2:.1f}" y="{pad_t + plot_h - u_h:.1f}" '
                f'width="{core_w:.1f}" height="{u_h:.1f}" rx="1" fill="{C_UNIQUE}"/>'
            )

    # 基线
    parts.append(
        f'<line x1="{pad_l}" y1="{pad_t + plot_h}" x2="{width - pad_r}" y2="{pad_t + plot_h}" '
        f'stroke="{C_AXIS}" stroke-width="1"/>'
    )

    # X 轴日期（首 / 中 / 末）
    marks = sorted({0, n // 2, n - 1})
    for i in marks:
        if i < 0 or i >= n:
            continue
        cx = pad_l + slot * i + slot / 2
        parts.append(
            f'<text x="{cx:.1f}" y="{height - 10}" text-anchor="middle" '
            f'font-family="-apple-system,Segoe UI,Helvetica,Arial,sans-serif" '
            f'font-size="10" fill="{C_TEXT}">{days[i]["label"]}</text>'
        )

    # 图例：放在标题行右侧，X 轴日期独占底部，避免重叠
    legend_y = 20
    lx = width - pad_r - 130
    parts.append(
        f'<rect x="{lx}" y="{legend_y - 8}" width="9" height="9" rx="2" fill="{C_BAR}"/>'
        f'<text x="{lx + 13}" y="{legend_y}" font-family="-apple-system,Segoe UI,Helvetica,Arial,sans-serif" '
        f'font-size="10" fill="{C_TEXT}">浏览量</text>'
    )
    parts.append(
        f'<rect x="{lx + 62}" y="{legend_y - 8}" width="9" height="9" rx="2" fill="{C_UNIQUE}"/>'
        f'<text x="{lx + 75}" y="{legend_y}" font-family="-apple-system,Segoe UI,Helvetica,Arial,sans-serif" '
        f'font-size="10" fill="{C_TEXT}">独立访客</text>'
    )

    parts.append("</svg>")
    return "\n".join(parts) + "\n"


def build_block(total: dict) -> str:
    updated = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d")
    return "\n".join(
        [
            START,
            "",
            "## 📊 访问统计",
            "",
            f"![近 14 天访问趋势](docs/traffic.svg)",
            "",
            "| 指标 | 近 14 天 |",
            "| --- | ---: |",
            f"| 页面浏览量 | {total['views']} |",
            f"| 独立访客 | {total['uniques']} |",
            f"| 克隆次数 | {total['clones']} |",
            f"| 独立克隆者 | {total['clone_uniques']} |",
            "",
            f"<sub>数据窗口 {total['window']} · 由 GitHub Actions 每日更新于 {updated} · "
            f"来源：GitHub Traffic API（仅统计最近 14 天）</sub>",
            "",
            END,
        ]
    )


def patch_readme(text: str, block: str) -> str | None:
    if START not in text or END not in text:
        return None
    head, rest = text.split(START, 1)
    _, tail = rest.split(END, 1)
    return f"{head}{block}{tail}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--token", default="")
    args = parser.parse_args()

    repo = args.repo
    token = (
        args.token
        or os.environ.get("TRAFFIC_TOKEN")
        or os.environ.get("GH_TOKEN")
        or os.environ.get("GITHUB_TOKEN")
        or ""
    ).strip()

    if not repo:
        print("!! 缺少仓库名（--repo 或 GITHUB_REPOSITORY），跳过。")
        return 0
    if not token:
        print("!! 未配置 TRAFFIC_TOKEN / GH_TOKEN / GITHUB_TOKEN，跳过访问统计。")
        print("   在仓库 Settings -> Secrets -> Actions 新增 TRAFFIC_TOKEN（classic PAT，勾选 repo）。")
        return 0

    try:
        views, clones = fetch(repo, token)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:200]
        print(f"!! traffic API 返回 {exc.code}：{detail}")
        if exc.code in (403, 401):
            print("   内置 GITHUB_TOKEN 无 traffic 权限，请改用 classic PAT（scope: repo）作为 TRAFFIC_TOKEN。")
        return 0
    except Exception as exc:  # noqa: BLE001
        print(f"!! 请求失败：{exc}")
        return 0

    v_days = views.get("views", [])
    c_days = clones.get("clones", [])
    by_day = {}
    for row in c_days:
        by_day[row["timestamp"][:10]] = row["count"]

    days = []
    for row in v_days:
        day = row["timestamp"][:10]
        d = dt.date.fromisoformat(day)
        days.append(
            {
                "date": day,
                "label": f"{d.month}/{d.day}",
                "views": row["count"],
                "uniques": row["uniques"],
            }
        )

    total = {
        "views": views.get("count", sum(d["views"] for d in days)),
        "uniques": views.get("uniques", sum(d["uniques"] for d in days)),
        "clones": clones.get("count", sum(by_day.values())),
        "clone_uniques": clones.get("uniques", 0),
        "window": f"{days[0]['date']} ~ {days[-1]['date']}" if days else "-",
    }

    print(f"repo      : {repo}")
    print(f"window    : {total['window']} ({len(days)} 天)")
    print(f"views     : {total['views']} (uniques {total['uniques']})")
    print(f"clones    : {total['clones']} (uniques {total['clone_uniques']})")

    os.makedirs("docs", exist_ok=True)
    with open("docs/traffic.svg", "w", encoding="utf-8") as fh:
        fh.write(build_svg(days, total))

    block = build_block(total)
    try:
        with open("README.md", encoding="utf-8") as fh:
            readme = fh.read()
    except FileNotFoundError:
        print("!! 没有 README.md，只生成了 docs/traffic.svg。")
        return 0

    patched = patch_readme(readme, block)
    if patched is None:
        print("!! README.md 里找不到 traffic:start/end 标记，只生成了 docs/traffic.svg。")
        return 0

    if patched == readme:
        print("README 统计区块无变化。")
    else:
        with open("README.md", "w", encoding="utf-8") as fh:
            fh.write(patched)
        print("README 统计区块已更新。")
    return 0


if __name__ == "__main__":
    sys.exit(main())