#!/usr/bin/env python3
"""Adds semiconductor-belt apartment sale statistics to feed/latest.json.

Source: 국토교통부 아파트 매매 실거래가 (data.go.kr). Needs the MOLIT_KEY environment
variable; the key is never written to the repository.

For each region it uses the latest *settled* month (deals can be reported up to 30 days
after the contract, so month M is complete once month M+1 has ended), drops canceled
deals, and computes the deal count and the median price per 3.3㎡ of exclusive area.
"""
import datetime as dt
import json
import os
import re
import statistics
import sys
import time
import urllib.request

ENDPOINT = "https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev"
REGIONS = [
    ("화성 동탄", "41597"),
    ("용인 기흥", "41463"),
    ("수원 영통", "41117"),
    ("평택", "41220"),
    ("이천", "41500"),
    ("용인 처인", "41461"),
]
TABLE_TITLE = "반도체 벨트 아파트 실거래"
CHART_TITLE = "반도체 벨트 전용 3.3㎡당 중위 실거래가"


def fetch(key, code, ym):
    deals, page = [], 1
    while True:
        url = f"{ENDPOINT}?serviceKey={key}&LAWD_CD={code}&DEAL_YMD={ym}&pageNo={page}&numOfRows=1000"
        for attempt in range(4):
            try:
                xml = urllib.request.urlopen(url, timeout=30).read().decode("utf-8")
                break
            except Exception as e:
                if attempt == 3:
                    # 인증키가 들어 있는 URL은 절대 출력하지 않아요
                    raise RuntimeError(f"request failed for {code} {ym}: {type(e).__name__}") from None
                time.sleep(3)
        code_m = re.search(r"<resultCode>([^<]*)<", xml)
        if code_m and code_m.group(1) not in ("000", "00"):
            raise RuntimeError(f"API error {code_m.group(1)} for {code} {ym}")
        for item in re.findall(r"<item>(.*?)</item>", xml, re.S):
            f = dict(re.findall(r"<(\w+)>([^<]*)</\1>", item))
            if f.get("cdealType", "").strip() == "O":
                continue
            try:
                price = int(f["dealAmount"].replace(",", "").strip())
                area = float(f["excluUseAr"])
            except (KeyError, ValueError):
                continue
            deals.append(price / (area / 3.3058))
        total = int((re.search(r"<totalCount>(\d+)<", xml) or [0, 0])[1])
        if page * 1000 >= total or page >= 10:
            return deals
        page += 1
        time.sleep(0.3)


def month_add(d, n):
    m = d.month - 1 + n
    return dt.date(d.year + m // 12, m % 12 + 1, 1)


def main():
    key = os.environ.get("MOLIT_KEY", "").strip()
    if not key:
        sys.exit("MOLIT_KEY is not set")
    today = dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).date()  # KST 기준
    settled = month_add(today.replace(day=1), -2)
    year_ago = month_add(settled, -12)
    ym = lambda d: f"{d.year}{d.month:02d}"

    rows, points = [], []
    for name, code in REGIONS:
        now = fetch(key, code, ym(settled))
        before = fetch(key, code, ym(year_ago))
        med = statistics.median(now) if now else None
        med_before = statistics.median(before) if len(before) >= 5 else None
        yoy = f"{(med / med_before - 1) * 100:+.1f}%" if med and med_before else "—"
        rows.append([name, f"{len(now):,}건", f"{med:,.0f}만" if med else "—", yoy])
        if med:
            points.append({"x": name.split()[-1], "y": round(med)})
        time.sleep(0.3)

    path = os.path.join(os.path.dirname(__file__), "..", "feed", "latest.json")
    feed = json.load(open(path, encoding="utf-8"))
    section = next(s for s in feed["sections"] if s["id"] == "realestate")
    label = f"{settled.month}월"
    table = {
        "title": f"{TABLE_TITLE} ({settled.year}년 {label} 확정)",
        "columns": ["", f"{label} 거래", "3.3㎡당 중위가", "1년 전 대비"],
        "rows": rows,
        "note": "국토교통부 실거래가(공공데이터포털) · 해제 거래 제외 · 전용면적 기준. "
                "거래 신고는 계약 후 30일 안에 하므로 가장 최근 확정된 달을 써요. 중위가는 그달 거래된 단지 구성에 따라 출렁일 수 있어요.",
    }
    section["tables"] = [t for t in section.get("tables", []) if not t["title"].startswith(TABLE_TITLE)] + [table]
    chart = {
        "title": f"{CHART_TITLE} ({label})",
        "unit": "", "type": "bar",
        "note": "국토교통부 실거래가 · 단위 만원 · 해제 거래 제외",
        "points": points,
    }
    section["charts"] = [c for c in section.get("charts", []) if not c["title"].startswith(CHART_TITLE)] + [chart]
    json.dump(feed, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(json.dumps(table, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
