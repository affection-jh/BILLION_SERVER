# 📈 주식 시뮬레이션 서버 API 가이드

## 🔌 WebSocket 연결

### 연결 URL
```
ws://localhost:8080/ws/stocks
```

### 구독 메시지 형식

#### 1. 실시간 인기주 구독 (상승률 높은 5개)
```json
{
  "type": "subscribe_trending"
}
```

#### 2. 추천주 구독 (20개)
```json
{
  "type": "subscribe_recommended"
}
```

#### 3. 상세보기 구독 (개별 종목)
```json
{
  "type": "subscribe_detail",
  "symbol": "XPL"
}
```

#### 4. 구독 해제
```json
{
  "type": "unsubscribe_detail",
  "symbol": "XPL"
}
```

#### 5. 모든 구독 해제
```json
{
  "type": "unsubscribe_all"
}
```

### 실시간 데이터 수신

#### 인기주 데이터 (상승률 높은 5개)
```json
{
  "type": "ticker",
  "data": [
    ["XPL", 132.4, 4.62],
    ["ABC", 245.8, 12.35],
    ["HIJ", 189.2, 8.91],
    ["AAA", 456.7, 23.45],
    ["NNN", 789.1, 34.67]
  ]
}
```

#### 추천주 데이터 (20개)
```json
{
  "type": "ticker",
  "data": [
    ["ZNT", 87.1, -1.35],
    ["DEF", 95.3, 2.14],
    ["GHI", 78.9, -0.87],
    ["JKL", 156.2, 5.23],
    ["MNO", 134.7, 3.45]
    // ... 15개 더
  ]
}
```

## 🌐 REST API

### 기본 URL
```
http://localhost:8080/api/stocks
```

### 1. 회사 기본 정보 조회

#### 요청
```http
GET /api/stocks/{symbol}
```

#### 예시
```http
GET /api/stocks/XPL
```

#### 응답
```json
{
  "symbol": "XPL",
  "name": "Xplode AI",
  "sector": "Technology",
  "description": "A cutting-edge generative AI startup",
  "open": 126.55,
  "close": 132.40,
  "volume": 1250000,
  "turnover": 165350000,
  "per": 28.5,
  "pbr": 3.2,
  "psr": 5.1,
  "industry_average": {
    "per": 25.0,
    "pbr": 2.8,
    "psr": 4.7
  },
  "market_cap": 5000000000,
  "dividend_yield": 1.75,
  "roe": 15.3
}
```

### 2. 추천 주식 리스트 조회

#### 요청
```http
GET /api/stocks/recommended
```

#### 응답
```json
[
  {
    "symbol": "ZNT",
    "name": "Zenith Tech",
    "sector": "Technology",
    // ... 기타 정보
  },
  // ... 19개 더
]
```

### 2-1. 추천 주식 리스트 + WebSocket 구독 정보 (권장)

#### 요청
```http
GET /api/stocks/recommended/with-subscription
```

#### 응답
```json
{
  "stocks": [
    {
      "symbol": "ZNT",
      "name": "Zenith Tech",
      "sector": "Technology",
      // ... 기타 정보
    },
    // ... 19개 더
  ],
  "websocket_subscription": {
    "url": "ws://localhost:8080/ws/stocks",
    "message": {
      "type": "subscribe_recommended"
    },
    "description": "이 메시지를 WebSocket으로 전송하면 추천주 실시간 데이터를 받을 수 있습니다."
  }
}
```

### 2-2. 실시간 인기주 리스트 + WebSocket 구독 정보

#### 요청
```http
GET /api/stocks/trending/with-subscription
```

#### 응답
```json
{
  "stocks": [
    {
      "symbol": "XPL",
      "name": "Xplode AI",
      "sector": "Technology",
      // ... 기타 정보
    },
    // ... 4개 더
  ],
  "websocket_subscription": {
    "url": "ws://localhost:8080/ws/stocks",
    "message": {
      "type": "subscribe_trending"
    },
    "description": "이 메시지를 WebSocket으로 전송하면 실시간 인기주 데이터를 받을 수 있습니다."
  }
}
```

### 3. 모든 종목 심볼 조회

#### 요청
```http
GET /api/stocks/symbols
```

#### 응답
```json
[
  "XPL", "ZNT", "ABC", "DEF", "GHI", "JKL", "MNO", "PQR", "STU", "VWX",
  "YZA", "BCD", "EFG", "HIJ", "KLM", "NOP", "QRS", "TUV", "WXY", "ZAB",
  // ... 30개 더
]
```

### 4. 현재 가격 조회

#### 요청
```http
GET /api/stocks/{symbol}/price
```

#### 예시
```http
GET /api/stocks/XPL/price
```

#### 응답
```json
132.4
```

### 5. 차트 데이터 조회

#### 요청
```http
GET /api/stocks/{symbol}/chart?period={period}&page={page}&size={size}
```

#### 파라미터
- `period`: 기간 (1min, 5min, 10min, 30min, 60min)
- `page`: 페이지 번호 (0부터 시작)
- `size`: 페이지 크기 (기본값: 100)

#### 예시
```http
GET /api/stocks/XPL/chart?period=1min&page=0&size=50
```

#### 응답
```json
{
  "symbol": "XPL",
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
  ]
}
```

### 6. 페이징된 차트 데이터 조회

#### 요청
```http
GET /api/stocks/{symbol}/chart/paging?period={period}&page={page}&size={size}
```

#### 응답
```json
{
  "symbol": "XPL",
  "period": "1min",
  "data": [...],
  "pagination": {
    "currentPage": 0,
    "totalPages": 5,
    "totalElements": 500,
    "size": 100,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### 7. 최근 차트 데이터 조회

#### 요청
```http
GET /api/stocks/{symbol}/chart/recent?period={period}&count={count}
```

#### 예시
```http
GET /api/stocks/XPL/chart/recent?period=1min&count=20
```

### 8. 시간 범위 차트 데이터 조회

#### 요청
```http
GET /api/stocks/{symbol}/chart/range?period={period}&startTime={startTime}&endTime={endTime}
```

#### 예시
```http
GET /api/stocks/XPL/chart/range?period=1min&startTime=1680000000000&endTime=1680003600000
```

### 9. 최근 캔들 데이터 조회

#### 요청
```http
GET /api/stocks/{symbol}/candle/latest
```

### 10. 모든 종목의 최근 캔들 데이터 조회

#### 요청
```http
GET /api/stocks/candles/latest
```

### 11. 차트 데이터 통계 조회

#### 요청
```http
GET /api/stocks/{symbol}/chart/stats?period={period}
```

#### 응답
```json
{
  "dataCount": 100,
  "averageVolume": 25000.5,
  "maxPrice": 150.2,
  "minPrice": 95.8,
  "averageChange": 2.34
}
```

### 12. 지원하는 차트 기간 목록 조회

#### 요청
```http
GET /api/stocks/chart/periods
```

#### 응답
```json
["1min", "5min", "10min", "30min", "60min"]
```

## 📊 종목 정보

### 실시간 인기주 (5개)
- **XPL**: Xplode AI (AI 기술)
- **ABC**: Alpha Blockchain (블록체인)
- **HIJ**: Hyper Intelligence (초지능)
- **AAA**: Advanced Aging (노화 방지)
- **NNN**: Nuclear Fusion (핵융합)

### 추천주 (20개)
- **ZNT**: Zenith Tech (클라우드 컴퓨팅)
- **DEF**: Digital Edge (엣지 컴퓨팅)
- **GHI**: Green Hydrogen (청정 에너지)
- **JKL**: Jupiter Labs (양자 컴퓨팅)
- **MNO**: Meta Networks (메타버스)
- **PQR**: Pixel Robotics (로보틱스)
- **STU**: Solar Tech (태양광)
- **VWX**: Virtual Worlds (VR/AR)
- **YZA**: Yotta Data (빅데이터)
- **BCD**: Bio Computing (바이오 컴퓨팅)
- **EFG**: Eco Tech (환경 기술)
- **KLM**: Krypto Mining (암호화폐)
- **NOP**: Nova Bank (디지털 뱅킹)
- **QRS**: Quantum Finance (AI 금융)
- **TUV**: Trust Insurance (스마트 보험)
- **WXY**: Wealth Exchange (자산 거래)
- **ZAB**: Zenith Capital (벤처 캐피탈)
- **CDE**: Crypto Bank (암호화폐 뱅킹)
- **FGH**: Future Finance (DeFi)
- **IJK**: Investment King (알고리즘 트레이딩)

## 🚀 사용 예시

### JavaScript WebSocket 예시
```javascript
const socket = new WebSocket('ws://localhost:8080/ws/stocks');

socket.onopen = function() {
    // 인기주 구독
    socket.send(JSON.stringify({ type: 'subscribe_trending' }));
    
    // 추천주 구독
    socket.send(JSON.stringify({ type: 'subscribe_recommended' }));
    
    // 개별 종목 구독
    socket.send(JSON.stringify({ 
        type: 'subscribe_detail', 
        symbol: 'XPL' 
    }));
};

socket.onmessage = function(event) {
    const data = JSON.parse(event.data);
    console.log('받은 데이터:', data);
};
```

### JavaScript API 호출 예시
```javascript
// 회사 정보 조회
fetch('/api/stocks/XPL')
    .then(response => response.json())
    .then(data => console.log(data));

// 차트 데이터 조회
fetch('/api/stocks/XPL/chart?period=1min&page=0&size=50')
    .then(response => response.json())
    .then(data => console.log(data));

// 추천주 리스트 + WebSocket 구독 정보 (한 번에 처리)
fetch('/api/stocks/recommended/with-subscription')
    .then(response => response.json())
    .then(data => {
        console.log('추천주 리스트:', data.stocks);
        
        // WebSocket 연결 및 구독
        const socket = new WebSocket(data.websocket_subscription.url);
        socket.onopen = function() {
            socket.send(JSON.stringify(data.websocket_subscription.message));
        };
        socket.onmessage = function(event) {
            const tickerData = JSON.parse(event.data);
            console.log('실시간 추천주 데이터:', tickerData);
        };
    });

// 인기주 리스트 + WebSocket 구독 정보 (한 번에 처리)
fetch('/api/stocks/trending/with-subscription')
    .then(response => response.json())
    .then(data => {
        console.log('인기주 리스트:', data.stocks);
        
        // WebSocket 연결 및 구독
        const socket = new WebSocket(data.websocket_subscription.url);
        socket.onopen = function() {
            socket.send(JSON.stringify(data.websocket_subscription.message));
        };
        socket.onmessage = function(event) {
            const tickerData = JSON.parse(event.data);
            console.log('실시간 인기주 데이터:', tickerData);
        };
    });
```

### cURL 예시
```bash
# 회사 정보 조회
curl http://localhost:8080/api/stocks/XPL

# 차트 데이터 조회
curl "http://localhost:8080/api/stocks/XPL/chart?period=1min&page=0&size=50"

# 추천주 리스트 조회
curl http://localhost:8080/api/stocks/recommended

# 추천주 리스트 + WebSocket 구독 정보 (권장)
curl http://localhost:8080/api/stocks/recommended/with-subscription

# 인기주 리스트 + WebSocket 구독 정보
curl http://localhost:8080/api/stocks/trending/with-subscription
```

## 📝 주의사항

1. **WebSocket 연결**: 실시간 데이터는 WebSocket을 통해 전송됩니다.
2. **페이징**: 대용량 차트 데이터는 페이징을 사용하여 성능을 최적화합니다.
3. **캐싱**: 최근 데이터는 메모리 캐시에서 빠르게 조회됩니다.
4. **자동 정리**: 7일 이상 된 데이터는 자동으로 삭제됩니다.
5. **실시간 집계**: 1분봉 데이터가 자동으로 5분, 10분, 30분, 60분봉으로 집계됩니다. 