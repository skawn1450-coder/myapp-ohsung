# 오늘의 시장 (myapp-ohsung)

부동산 · 주식 · 반도체(메모리) · 오늘의 이슈를 한 화면에서 요약해 보는 개인용 안드로이드 앱.

## 구조

| 위치 | 역할 |
|---|---|
| `app/` | 안드로이드 앱 (Kotlin + Jetpack Compose) |
| `feed/latest.json` | 매일 갱신되는 브리핑 데이터. 앱이 GitHub에서 직접 읽어요 |
| `.github/workflows/build.yml` | `main`에 앱 코드가 바뀌면 APK를 빌드해 Releases에 올려요 |
| `keystore/dev.jks` | 개인 설치용 개발 서명 키 (공개돼도 되는 용도, Play 스토어용 아님) |

## 데이터

- **요약·호재/악재·전망·이슈**: `feed/latest.json`. 앱을 열거나 당겨서 새로고침하면 최신 파일을 받아요.
- **실시간 시세 차트**: 앱이 Yahoo Finance 차트 API에서 직접 받아요 (지연될 수 있음).
- 전망은 증권사·연구기관 의견을 출처와 함께 요약한 것이며 투자 권유가 아니에요.

## 출처 기준 (신뢰도 순)

1. **1차 출처 (최우선)**: 한국은행, 한국부동산원, 국토교통부, 재정경제부, 국가데이터처, 금융위, 한국거래소(KRX), DART 공시, 기업 공식 발표(삼성전자·SK하이닉스 뉴스룸), 트렌드포스·카운터포인트 등 리서치 기관 원문, 증권사 리서치, 국책·민간 연구원(KDI, 건산연, 주산연)
2. **주요 언론**: 연합뉴스, 뉴시스, 뉴스1, 한국경제, 매일경제, 서울경제, 머니투데이(MTN), 이데일리, 파이낸셜뉴스, 조선비즈, 아시아경제, 헤럴드경제, 비즈니스워치, SBS Biz, 서울신문 등 종합·경제 일간지 / Reuters, Bloomberg, WSJ, FT, CNBC, Nikkei
3. **사용 금지**: 블로그·카페·커뮤니티·유튜브, 바이럴·연예 매체(위키트리, 인사이트 등), 기사 재게재·요약·미러 사이트, AI 요약 사이트, 유료 기사 플랫폼, 지역지(대체 출처가 있을 때), 코인 전문 매체(코인 시세 외)

- 숫자는 1차 출처 1곳 또는 주요 언론 2곳 이상에서 확인된 것만 써요.
- 같은 기사라면 전재·포털 링크보다 원문 링크를 써요.

## 피드 형식 (요약)

```json
{
  "updatedLabel": "11월 21일(토) 오후",
  "sections": [
    {
      "id": "realestate | stocks | memory | issues",
      "title": "탭 이름",
      "headline": "한 줄 요약",
      "summary": ["..."],
      "metrics": [{"label": "", "value": "", "sub": "", "trend": "up|down|flat"}],
      "tickers": [{"symbol": "005930.KS", "name": "삼성전자"}],
      "charts": [{"title": "", "unit": "%|$", "type": "line|bar", "note": "", "points": [{"x": "", "y": 0}]}],
      "table": {"title": "", "columns": [], "rows": [[]], "note": ""},
      "outlook": [{"source": "", "text": "", "url": ""}],
      "positives": [{"title": "", "detail": "", "source": "", "url": ""}],
      "negatives": [...],
      "news": [{"category": "", "date": "", "title": "", "summary": "", "source": "", "url": ""}]
    }
  ]
}
```

## 설치

Releases에서 최신 `market-brief-*.apk`를 받아 설치하세요. 같은 서명 키로 빌드되므로 새 버전을 덮어 설치하면 돼요.
