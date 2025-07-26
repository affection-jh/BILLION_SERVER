# 주식 시뮬레이션 서버 API 명세서

## 📋 개요
실시간 주식 시뮬레이션 서버로, 35개 가상 기업의 주가를 실시간으로 생성하고 WebSocket을 통해 실시간 데이터를 제공합니다.

**Base URL**: `http://localhost:8080`

---

## 🔌 WebSocket API

### WebSocket 연결
```
ws://localhost:8080/websocket
```

### 구독 메시지 형식

#### 1. 인기주 + 추천주 구독
```json
{
  "type": "subscribe_trending"
}
```

#### 2. 전체 종목 구독 (낮은 빈도)
```json
{
  "type": "subscribe_all_stocks"
}
```

#### 3. 특정 종목 상세 구독
```json
{
  "type": "subscribe_detail",
  "symbol": "ABC"
}
```

#### 4. 구독 해제
```json
{
  "type": "unsubscribe_detail",
  "symbol": "ABC"
}
```

### 실시간 데이터 형식

#### Ticker 메시지 (가격 변동 시에만 전송)
```json
{
  "type": "ticker",
  "data": [
    ["ABC", 125.50, 2.34],
    ["XYZ", 89.20, -1.15],
    ["DEF", 156.80, 5.67]
  ]
}
```

**데이터 구조**: `[종목코드, 현재가, 변동률(%)]`

---

## 📊 REST API

### 1. 회사 기본 정보 API

#### 1.1 모든 회사 목록 조회
```
GET /api/companies
```

**응답 예시**:
```json
[
  {
    "symbol": "ABC",
    "name": "Alpha Tech",
    "sector": "Technology",
    "description": "AI 기반 소프트웨어 솔루션",
    "open": 120.50,
    "close": 125.50,
    "volume": 1250000,
    "turnover": 156875000,
    "per": 28.5,
    "pbr": 3.2,
    "psr": 5.1,
    "market_cap": 5000000000,
    "dividend_yield": 1.75,
    "roe": 15.3
  }
]
```

#### 1.2 특정 회사 정보 조회
```
GET /api/companies/{symbol}
```

**응답 예시**:
```json
{
  "symbol": "ABC",
  "name": "Alpha Tech",
  "sector": "Technology",
  "description": "AI 기반 소프트웨어 솔루션",
  "open": 120.50,
  "close": 125.50,
  "volume": 1250000,
  "turnover": 156875000,
  "per": 28.5,
  "pbr": 3.2,
  "psr": 5.1,
  "market_cap": 5000000000,
  "dividend_yield": 1.75,
  "roe": 15.3
}
```

### 2. 인기주 API

#### 2.1 인기주 목록 조회
```
GET /api/stocks/trending
```

**응답 예시**:
```json
[
  {
    "symbol": "ABC",
    "name": "Alpha Tech",
    "sector": "Technology",
    "close": 125.50,
    "volume": 1250000,
    "changePercent": 15.67
  }
]
```

### 3. 추천주 API

#### 3.1 추천주 목록 조회 (홈화면용 10개)
```
GET /api/stocks/recommended
```

**응답 예시**:
```json
[
  {
    "symbol": "XYZ",
    "name": "Beta Corp",
    "sector": "Healthcare",
    "close": 89.20,
    "volume": 890000,
    "changePercent": 3.45
  }
]
```

### 4. 전체 종목 API

#### 4.1 전체 종목 목록 조회 (페이지네이션)
```
GET /api/stocks/all?page={page}&size={size}
```

**파라미터**:
- `page`: 페이지 번호 (기본값: 0)
- `size`: 페이지 크기 (기본값: 20)

**응답 예시**:
```json
{
  "stocks": [
    {
      "symbol": "ABC",
      "name": "Alpha Tech",
      "sector": "Technology",
      "close": 125.50,
      "volume": 1250000,
      "changePercent": 15.67
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 35,
  "totalPages": 2,
  "hasNext": true,
  "hasPrevious": false
}
```

#### 4.2 전체 종목 목록 조회 (구독 정보 포함)
```
GET /api/stocks/all/with-subscription?page={page}&size={size}
```

**응답 예시**:
```json
{
  "stocks": [...],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3,
  "hasNext": true,
  "hasPrevious": false,
  "subscription": {
    "type": "subscribe_all_stocks",
    "description": "전체 종목 실시간 구독 (3초마다 업데이트, 50개 종목)"
  }
}
```

### 5. 차트 데이터 API

#### 4.1 특정 종목 차트 데이터 조회
```
GET /api/charts/{symbol}?period={period}&page={page}&size={size}
```

**파라미터**:
- `period`: 차트 기간 (1min, 5min, 10min, 30min, 60min, 1day, 1week, 1month)
- `page`: 페이지 번호 (기본값: 0)
- `size`: 페이지 크기 (기본값: 100, 최대: 1000)

**응답 예시**:
```json
{
  "symbol": "ABC",
  "period": "1min",
  "data": [
    {
      "timestamp": 1680000000000,
      "open": 100.5,
      "high": 102.3,
      "low": 99.8,
      "close": 101.7,
      "volume": 15000
    },
    {
      "timestamp": 1680000060000,
      "open": 101.7,
      "high": 103.0,
      "low": 101.5,
      "close": 102.3,
      "volume": 12000
    }
  ],
  "page": {
    "number": 0,
    "size": 100,
    "totalElements": 1440,
    "totalPages": 15
  }
}
```

#### 4.2 최신 캔들 데이터 조회
```
GET /api/charts/{symbol}/latest
```

**응답 예시**:
```json
{
  "symbol": "ABC",
  "period": "1min",
  "data": {
    "timestamp": 1680000000000,
    "open": 100.5,
    "high": 102.3,
    "low": 99.8,
    "close": 101.7,
    "volume": 15000
  }
}
```

#### 4.3 모든 종목 최신 캔들 조회
```
GET /api/charts/latest
```

**응답 예시**:
```json
{
  "data": [
    {
      "symbol": "ABC",
      "period": "1min",
      "data": {
        "timestamp": 1680000000000,
        "open": 100.5,
        "high": 102.3,
        "low": 99.8,
        "close": 101.7,
        "volume": 15000
      }
    }
  ]
}
```

### 5. 시스템 정보 API

#### 5.1 캐시 메모리 정보 조회
```
GET /api/cache/memory
```

**응답 예시**:
```json
{
  "cacheSize": 35,
  "memoryUsage": "2.5MB",
  "lastUpdate": "2024-01-15T10:30:00"
}
```

---

## 🔄 실시간 데이터 특성

### 가격 변동 주기
- **일반 종목**: 1초마다 불규칙적 변동
- **인기주**: 0.5초마다 고빈도 변동
- **전체 종목**: 3초마다 50개 종목 동시 업데이트
- **거래량 기반**: 거래량 폭증 시 급등, 부족 시 횡보

### 변동률 계산
- **기준**: 전날 종가 대비 변동률
- **범위**: -50% ~ +300% (현실적 한계)

### 거래량 특성
- **시간대별**: 장 시간대(9-15시) 1.2배, 장외 시간대 0.3배
- **가격 연관**: 5% 이상 변동 시 거래량 최대 5배 증가
- **급등락 시**: 3-8배 거래량 폭증

---

## 📈 데이터 보관 정책

### 캔들 데이터
- **보관 기간**: 1년
- **자동 정리**: 1년 초과 데이터 자동 삭제
- **페이지네이션**: 대용량 데이터 효율적 조회

### 실시간 데이터
- **메모리 캐시**: 최신 1000개 가격/거래량 데이터
- **자동 제한**: 메모리 사용량 최적화

---

## 🚨 에러 응답 형식

### 400 Bad Request
```json
{
  "error": "INVALID_PERIOD",
  "message": "지원하지 않는 차트 기간입니다",
  "timestamp": "2024-01-15T10:30:00"
}
```

### 404 Not Found
```json
{
  "error": "STOCK_NOT_FOUND",
  "message": "종목을 찾을 수 없습니다: ABC",
  "timestamp": "2024-01-15T10:30:00"
}
```

### 500 Internal Server Error
```json
{
  "error": "INTERNAL_ERROR",
  "message": "서버 내부 오류가 발생했습니다",
  "timestamp": "2024-01-15T10:30:00"
}
```

---

## 🔧 클라이언트 사용 예시

### JavaScript WebSocket 클라이언트
```javascript
const ws = new WebSocket('ws://localhost:8080/websocket');

// 인기주 + 추천주 구독
ws.send(JSON.stringify({
  type: 'subscribe_trending'
}));

// 특정 종목 상세 구독
ws.send(JSON.stringify({
  type: 'subscribe_detail',
  symbol: 'ABC'
}));

// 메시지 수신
ws.onmessage = function(event) {
  const data = JSON.parse(event.data);
  if (data.type === 'ticker') {
    data.data.forEach(([symbol, price, change]) => {
      console.log(`${symbol}: ${price} (${change}%)`);
    });
  }
};
```

### REST API 호출 예시
```javascript
// 인기주 목록 조회
fetch('/api/stocks/trending')
  .then(response => response.json())
  .then(data => console.log(data));

// 차트 데이터 조회
fetch('/api/charts/ABC?period=1min&page=0&size=100')
  .then(response => response.json())
  .then(data => console.log(data));
```

---

## 📝 참고사항

1. **실시간성**: WebSocket은 push-on-change 방식으로 변동 시에만 전송
2. **성능**: 페이지네이션으로 대용량 데이터 효율적 처리
3. **안정성**: 1년 데이터 보관으로 장기 운영 지원
4. **현실성**: 거래량 기반 가격 변동으로 실제 시장과 유사한 패턴 