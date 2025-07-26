package com.nbillion.service;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

/**
 * 인메모리 큐 기반 간단한 캔들 관리자
 * 파이썬 버전과 동일한 시간 축소 시스템 적용
 */
@Component
public class SimpleCandleManager {
    
    // 안정적인 반응을 위한 설정
    private static final double UPDATE_INTERVAL = 10.0; // 10초마다 업데이트 (안정적인 반응)
    private static final int ONE_DAY_SECONDS = 1440; // 1440분 = 1일 (10초 = 1분, 1440분 = 1일)
    private static final int CANDLE_PER_DAY = 1440; // 하루 1440개 캔들 (1440분 ÷ 1분)
    private static final int MAX_STORAGE_TIME = 1440 * 30; // 1달치 1분봉 (1440 * 30개)
    
    // 실제 시간 기반 관리
    private long startTime = System.currentTimeMillis(); // 서버 시작 시간

    
    // 기간별 범위 (간단하게 수정)
    private static final Map<String, Integer> PERIOD_RANGE = new HashMap<>();
    
    static {
        // 기본 기간들만 (1일까지만)
        PERIOD_RANGE.put("1min", 1440 * 30);     // 1분봉: 43,200개 (1달치)
        PERIOD_RANGE.put("5min", 288);      // 5분봉: 288개 (1일치)
        PERIOD_RANGE.put("10min", 144);     // 10분봉: 144개 (1일치)
        PERIOD_RANGE.put("30min", 48);      // 30분봉: 48개 (1일치)
        PERIOD_RANGE.put("60min", 24);      // 60분봉: 24개 (1일치)
        PERIOD_RANGE.put("1hour", 24);      // 1시간봉: 24개 (1일치)
        PERIOD_RANGE.put("1day", 1);        // 1일봉: 1개 (1일치)
    }
    
    // 각 종목별 캔들 데이터 (stockData 제거하고 candleData만 사용)
    private final Map<String, Map<String, Deque<CandleData>>> candleData = new ConcurrentHashMap<>();
    
    // 1분봉 누적을 위한 변수들
    private final Map<String, CandleData> currentMinuteCandles = new ConcurrentHashMap<>(); // 현재 1분봉 누적 중인 캔들
    private final Map<String, Long> lastMinuteTime = new ConcurrentHashMap<>(); // 마지막 1분봉 생성 시간
    

    
    /**
     * 캔들 데이터
     */
    public static class CandleData {
        public final long time;
        public final double open;
        public final double high;
        public final double low;
        public final double close;
        public final long volume;
        
        public CandleData(long time, double open, double high, double low, double close, long volume) {
            this.time = time;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }
    
    /**
     * 가상 시간 업데이트 (실제 0.4초 = 가상 1초)
     */
    private long getCurrentTime() {
        long currentRealTime = System.currentTimeMillis();
        long elapsedRealSeconds = (currentRealTime - startTime) / 1000;
        
        // 디버깅용 로그 (10초마다 출력)
        if (elapsedRealSeconds % 10 == 0 && elapsedRealSeconds > 0) {
            System.out.println("⏰ 실제시간: " + elapsedRealSeconds + "초");
        }
        
        return elapsedRealSeconds;
    }
    
    /**
     * 종목 초기화 (candleData만 사용)
     */
    public void initializeStock(String symbol, double initialPrice) {
        // 캔들 데이터 초기화
        Map<String, Deque<CandleData>> symbolCandles = new HashMap<>();
        
        // 1분봉을 원본 데이터로 사용 (1달치 저장)
        symbolCandles.put("1min", new ArrayDeque<>());
        
        // 기본 기간들 초기화 (1일까지만)
        symbolCandles.put("5min", new ArrayDeque<>());
        symbolCandles.put("10min", new ArrayDeque<>());
        symbolCandles.put("30min", new ArrayDeque<>());
        symbolCandles.put("60min", new ArrayDeque<>());
        symbolCandles.put("1hour", new ArrayDeque<>());
        symbolCandles.put("1day", new ArrayDeque<>());
        
        candleData.put(symbol, symbolCandles);
        
        // 초기 캔들 생성 (실제 시간 사용)
        long currentTime = getCurrentTime();
        CandleData initialCandle = new CandleData(
            currentTime, 
            initialPrice, 
            initialPrice, 
            initialPrice, 
            initialPrice, 
            1000 // 기본 거래량
        );
        
        // 1분봉에 초기 캔들 추가
        symbolCandles.get("1min").add(initialCandle);
        
        System.out.println("📊 [" + symbol + "] 캔들 전용 매니저 초기화 완료 (실제시간: " + currentTime + "초)");
    }
    
    /**
     * 새로운 가격 데이터 추가 (candleData만 사용)
     */
    public void addPricePoint(String symbol, long timestamp, double price) {
        // 실제 시간 가져오기
        long currentTime = getCurrentTime();
        
        Map<String, Deque<CandleData>> symbolCandles = candleData.get(symbol);
        if (symbolCandles == null) return;
        
        // 1분봉에 새로운 캔들 추가
        Deque<CandleData> oneMinCandles = symbolCandles.get("1min");
        if (oneMinCandles == null) {
            oneMinCandles = new ArrayDeque<>();
            symbolCandles.put("1min", oneMinCandles);
        }
        
        // 10초마다 1분봉 생성 (안정적인 반응)
        long currentMinute = currentTime / 10; // 10초 단위로 그룹화
        Long lastMinute = lastMinuteTime.get(symbol);
        
        // 새로운 1분봉 시작 조건 (10초마다 새로운 분봉 시작)
        if (!lastMinuteTime.containsKey(symbol) || lastMinute == null || lastMinute != currentMinute) {
            // 이전 1분봉이 완성되면 저장 (10초가 지났을 때)
            if (lastMinuteTime.containsKey(symbol) && currentMinuteCandles.containsKey(symbol)) {
                CandleData completedCandle = currentMinuteCandles.get(symbol);
                if (completedCandle != null) {
                    oneMinCandles.addLast(completedCandle);
                    
                    // 최대 개수 초과시 오래된 데이터 제거
                    while (oneMinCandles.size() > MAX_STORAGE_TIME) {
                        oneMinCandles.removeFirst();
                    }
                    
                    // 다른 기간들의 캔들 데이터 생성
                    generateCandleData(symbol);
                    
                    System.out.println("✅ [" + symbol + "] 1분봉 완성! (실제시간: " + currentTime + "초, 10초간격, 시가: " + completedCandle.open + ", 종가: " + completedCandle.close + ")");
                }
            }
            
            // 새로운 1분봉 시작
            CandleData newCandle = new CandleData(
                currentTime,
                price, // 시가
                price, // 고가
                price, // 저가
                price, // 종가
                1000 + (long)(Math.random() * 5000) // 랜덤 거래량
            );
            currentMinuteCandles.put(symbol, newCandle);
            lastMinuteTime.put(symbol, currentMinute);
            
            // 새로운 1분봉 시작 로그는 줄이기 (너무 많이 출력됨)
            if (currentTime % 60 == 0) { // 1분마다만 출력
                System.out.println("🕐 [" + symbol + "] 새로운 1분봉 시작 (실제시간: " + currentTime + "초, 10초간격, 가격: " + price + ")");
            }
        } else {
            // 기존 1분봉에 누적
            CandleData currentCandle = currentMinuteCandles.get(symbol);
            if (currentCandle != null) {
                double newHigh = Math.max(currentCandle.high, price);
                double newLow = Math.min(currentCandle.low, price);
                long newVolume = currentCandle.volume + 1000 + (long)(Math.random() * 5000);
                
                CandleData updatedCandle = new CandleData(
                    currentTime,
                    currentCandle.open, // 시가는 그대로
                    newHigh,
                    newLow,
                    price, // 종가는 현재 가격으로 업데이트
                    newVolume
                );
                currentMinuteCandles.put(symbol, updatedCandle);
                
               
            }
        }
    }
    

    
    /**
     * 캔들 데이터 생성 (1분봉을 누적해서 큰 캔들 생성 - 1일까지만)
     */
    private void generateCandleData(String symbol) {
        Map<String, Deque<CandleData>> symbolCandles = candleData.get(symbol);
        if (symbolCandles == null) return;
        
        Deque<CandleData> oneMinCandles = symbolCandles.get("1min");
        if (oneMinCandles == null || oneMinCandles.size() < 5) return; // 최소 5개의 1분봉 필요
        
        // 1분봉들을 누적해서 5분봉 생성
        List<CandleData> fiveMinCandles = aggregateCandlesToCandles(new ArrayList<>(oneMinCandles), 5);
        updateCandlePeriod(symbolCandles, "5min", fiveMinCandles);
        
        // 1분봉들을 누적해서 10분봉 생성
        List<CandleData> tenMinCandles = aggregateCandlesToCandles(new ArrayList<>(oneMinCandles), 10);
        updateCandlePeriod(symbolCandles, "10min", tenMinCandles);
        
        // 1분봉들을 누적해서 30분봉 생성
        List<CandleData> thirtyMinCandles = aggregateCandlesToCandles(new ArrayList<>(oneMinCandles), 30);
        updateCandlePeriod(symbolCandles, "30min", thirtyMinCandles);
        
        // 1분봉들을 누적해서 60분봉 생성
        List<CandleData> sixtyMinCandles = aggregateCandlesToCandles(new ArrayList<>(oneMinCandles), 60);
        updateCandlePeriod(symbolCandles, "60min", sixtyMinCandles);
        
        // 1분봉들을 누적해서 1시간봉 생성 (60분봉과 동일)
        updateCandlePeriod(symbolCandles, "1hour", sixtyMinCandles);
        
        // 1분봉들을 누적해서 일봉 생성 (1440개 1분봉 = 1일)
        List<CandleData> dayCandles = aggregateCandlesToCandles(new ArrayList<>(oneMinCandles), 1440);
        updateCandlePeriod(symbolCandles, "1day", dayCandles);
    }
    
    /**
     * 특정 기간의 캔들 데이터 업데이트
     */
    private void updateCandlePeriod(Map<String, Deque<CandleData>> symbolCandles, String period, List<CandleData> newCandles) {
        if (!symbolCandles.containsKey(period)) {
            symbolCandles.put(period, new ArrayDeque<>());
        }
        
        Deque<CandleData> periodCandles = symbolCandles.get(period);
        periodCandles.clear(); // 기존 데이터 클리어
        
        // 새로운 캔들들 추가
        for (CandleData candle : newCandles) {
            periodCandles.addLast(candle);
        }
        
        // 최대 개수 제한
        while (periodCandles.size() > MAX_STORAGE_TIME) {
            periodCandles.removeFirst();
        }
    }
    

    
    /**
     * 캔들들을 누적해서 더 큰 캔들 생성
     */
    private List<CandleData> aggregateCandlesToCandles(List<CandleData> candles, int groupSize) {
        if (candles.size() < groupSize) {
            return new ArrayList<>();
        }
        
        List<CandleData> aggregatedCandles = new ArrayList<>();
        
        for (int i = 0; i < candles.size(); i += groupSize) {
            if (i + groupSize > candles.size()) {
                break;
            }
            
            List<CandleData> chunk = candles.subList(i, i + groupSize);
            if (chunk.size() < groupSize) {
                break;
            }
            
            // 첫 번째 캔들의 시가
            double open = chunk.get(0).open;
            // 마지막 캔들의 종가
            double close = chunk.get(chunk.size() - 1).close;
            // 전체 기간의 고가
            double high = chunk.stream().mapToDouble(c -> c.high).max().orElse(open);
            // 전체 기간의 저가
            double low = chunk.stream().mapToDouble(c -> c.low).min().orElse(open);
            // 전체 거래량 합계
            long volume = chunk.stream().mapToLong(c -> c.volume).sum();
            // 마지막 캔들의 시간
            long time = chunk.get(chunk.size() - 1).time;
            
            aggregatedCandles.add(new CandleData(time, open, high, low, close, volume));
        }
        
        return aggregatedCandles;
    }
    
    /**
     * 파이썬의 create_candle_from_data와 동일
     */
    private CandleData createCandleFromCandles(List<CandleData> candleList, int lookback) {
        if (candleList.size() < lookback) {
            return null;
        }
        
        List<CandleData> targetData = candleList.subList(
            candleList.size() - lookback, candleList.size());
        
        double open = targetData.get(0).open;
        double close = targetData.get(targetData.size() - 1).close;
        double high = targetData.get(0).high;
        double low = targetData.get(0).low;
        long volume = 0;
        
        for (CandleData candle : targetData) {
            if (candle.high > high) high = candle.high;
            if (candle.low < low) low = candle.low;
            volume += candle.volume;
        }
        
        return new CandleData(
            targetData.get(targetData.size() - 1).time,
            open, high, low, close, volume
        );
    }
    
    /**
     * 파이썬 스타일 샘플링된 데이터 조회 [timestamp, price] 형태
     */
    public List<List<Object>> getSampledData(String symbol, String period) {
        Map<String, Deque<CandleData>> symbolCandles = candleData.get(symbol);
        if (symbolCandles == null) return new ArrayList<>();
        
        Deque<CandleData> oneMinCandles = symbolCandles.get("1min");
        if (oneMinCandles == null || oneMinCandles.isEmpty()) return new ArrayList<>();
        
        // 1분봉에서 종가를 추출해서 [timestamp, price] 형태로 반환
        List<List<Object>> result = new ArrayList<>();
        for (CandleData candle : oneMinCandles) {
            result.add(Arrays.asList(candle.time, candle.close));
        }
        
        return result;
    }
    
    /**
     * 캔들 형태의 데이터 조회 (Flutter 클라이언트용)
     */
    public List<Map<String, Object>> getCandleData(String symbol, String period) {
        Map<String, Deque<CandleData>> symbolCandles = candleData.get(symbol);
        if (symbolCandles == null) return new ArrayList<>();
        
        Deque<CandleData> periodCandles = symbolCandles.get(period);
        if (periodCandles == null || periodCandles.isEmpty()) return new ArrayList<>();
        
        List<Map<String, Object>> candles = new ArrayList<>();
        
        for (CandleData candle : periodCandles) {
            Map<String, Object> candleMap = new HashMap<>();
            candleMap.put("timestamp", candle.time * 1000L); // 초를 밀리초로 변환
            candleMap.put("open", Math.round(candle.open * 100.0) / 100.0);
            candleMap.put("high", Math.round(candle.high * 100.0) / 100.0);
            candleMap.put("low", Math.round(candle.low * 100.0) / 100.0);
            candleMap.put("close", Math.round(candle.close * 100.0) / 100.0);
            candleMap.put("volume", candle.volume);
            
            candles.add(candleMap);
        }
        
        return candles;
    }
    
    /**
     * 고급 캔들 데이터 조회 (실제 OHLCV 계산)
     */
    public List<Map<String, Object>> getAdvancedCandleData(String symbol, String period) {
        Map<String, Deque<CandleData>> symbolCandles = candleData.get(symbol);
        if (symbolCandles == null) return new ArrayList<>();
        
        Deque<CandleData> periodCandles = symbolCandles.get(period);
        if (periodCandles == null || periodCandles.isEmpty()) return new ArrayList<>();
        
        List<Map<String, Object>> candles = new ArrayList<>();
        
        for (CandleData candle : periodCandles) {
            Map<String, Object> candleMap = new HashMap<>();
            candleMap.put("timestamp", candle.time * 1000L); // 초를 밀리초로 변환
            candleMap.put("open", Math.round(candle.open * 100.0) / 100.0);
            candleMap.put("high", Math.round(candle.high * 100.0) / 100.0);
            candleMap.put("low", Math.round(candle.low * 100.0) / 100.0);
            candleMap.put("close", Math.round(candle.close * 100.0) / 100.0);
            candleMap.put("volume", candle.volume);
            
            candles.add(candleMap);
        }
        
        return candles;
    }
    
    /**
     * 기간별 캔들 그룹 크기 결정 (1일까지만)
     */
    private int getCandleGroupSize(String period) {
        switch (period) {
            // 기본 분봉들
            case "1min": return 1;     // 1분봉: 1개 데이터 포인트
            case "5min": return 5;     // 5분봉: 5개 데이터 포인트
            case "10min": return 10;   // 10분봉: 10개 데이터 포인트
            case "30min": return 30;   // 30분봉: 30개 데이터 포인트
            case "60min": return 60;   // 60분봉: 60개 데이터 포인트
            case "1hour": return 60;   // 1시간봉: 60개 데이터 포인트 (60min과 동일)
            case "1day": return 1440;  // 1일봉: 1440개 데이터 포인트 (1440분)
            
            default: return 1;
        }
    }
    
    /**
     * 현재 가격 조회 (1분봉의 최신 종가)
     */
    public Map<String, Double> getCurrentPrices() {
        Map<String, Double> prices = new HashMap<>();
        for (Map.Entry<String, Map<String, Deque<CandleData>>> entry : candleData.entrySet()) {
            String symbol = entry.getKey();
            Map<String, Deque<CandleData>> symbolCandles = entry.getValue();
            Deque<CandleData> oneMinCandles = symbolCandles.get("1min");
            if (oneMinCandles != null && !oneMinCandles.isEmpty()) {
                prices.put(symbol, oneMinCandles.peekLast().close);
            }
        }
        return prices;
    }
    
    /**
     * 캔들 데이터 조회 (기존 JSON 구조 유지)
     */
    public List<CandleData> getCandles(String symbol, String period) {
        Map<String, Deque<CandleData>> symbolCandles = candleData.get(symbol);
        if (symbolCandles == null) return new ArrayList<>();
        
        Deque<CandleData> candles = symbolCandles.get(period);
        return candles != null ? new ArrayList<>(candles) : new ArrayList<>();
    }
    
    /**
     * 디버깅용: 현재 상태 출력
     */
    public void printStatus(String symbol) {
        Map<String, Deque<CandleData>> symbolCandles = candleData.get(symbol);
        if (symbolCandles == null) {
            System.out.println("📊 [" + symbol + "] 캔들 데이터 없음");
            return;
        }
        
        long currentTime = getCurrentTime();
        System.out.println("📊 [" + symbol + "] 캔들 전용 매니저 상태 (실제시간: " + currentTime + "초):");
        System.out.println("  - 1분봉: " + (symbolCandles.get("1min") != null ? symbolCandles.get("1min").size() : 0) + "개 (1달치 저장)");
        System.out.println("  - 5분봉: " + (symbolCandles.get("5min") != null ? symbolCandles.get("5min").size() : 0) + "개");
        System.out.println("  - 10분봉: " + (symbolCandles.get("10min") != null ? symbolCandles.get("10min").size() : 0) + "개");
        System.out.println("  - 30분봉: " + (symbolCandles.get("30min") != null ? symbolCandles.get("30min").size() : 0) + "개");
        System.out.println("  - 60분봉: " + (symbolCandles.get("60min") != null ? symbolCandles.get("60min").size() : 0) + "개");
        System.out.println("  - 1시간봉: " + (symbolCandles.get("1hour") != null ? symbolCandles.get("1hour").size() : 0) + "개");
        System.out.println("  - 1일봉: " + (symbolCandles.get("1day") != null ? symbolCandles.get("1day").size() : 0) + "개");
    }
} 