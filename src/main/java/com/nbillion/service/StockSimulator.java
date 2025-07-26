package com.nbillion.service;

import com.nbillion.config.CompanyConfig;
import com.nbillion.model.Company;
import com.nbillion.model.TickerMessage;
import org.springframework.stereotype.Service;
import com.nbillion.websocket.StockWebSocketHandler;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.nbillion.model.CandleData1Min;
import com.nbillion.repository.CandleData1MinRepository;
import org.springframework.stereotype.Component;
import java.util.concurrent.ScheduledFuture;

@Component
public class StockSimulator {
    
    // 시뮬레이션 상수 (변동률 대폭 감소)
    private static final double SIGMA = 0.015;          // 기본 변동성 (0.08 → 0.015로 대폭 감소)
    private static final double MU = 0.00002;           // 장기 기대 수익률 (0.0001 → 0.00002로 감소)
    private static final double DT = 1.0 / 3600.0;     // 시간 단위 (1초)
    
    private final StockWebSocketHandler webSocketHandler;
    private final CompanyConfig companyConfig;
    private final CandleData1MinRepository candle1MinRepo;
    private final SimpleCandleManager candleManager; // 파이썬 스타일 캔들 매니저 추가
    
    // 주식 상태 관리
    private final Map<String, Company> companies = new ConcurrentHashMap<>();
    private final Map<String, StockState> stockStates = new ConcurrentHashMap<>();
    private final Map<String, Double> currentPrices = new ConcurrentHashMap<>();
    private final Map<String, Double> previousPrices = new ConcurrentHashMap<>();
    private final Map<String, Double> previousDayClose = new ConcurrentHashMap<>();
    
    // 시가 저장용 (상승률 계산을 위해)
    private final Map<String, Double> todayOpenPrices = new ConcurrentHashMap<>();
    
    // 기간별 기준 가격 (변동률 계산용)
    private final Map<String, Double> weekAgoPrices = new ConcurrentHashMap<>();
    private final Map<String, Double> monthAgoPrices = new ConcurrentHashMap<>();
    
    // 실시간 인기 주식 리스트 (상승률 높은 5개) - 동적 선정
    private final List<String> trendingStocks = new ArrayList<>();
    
    // 홈화면 구성 (총 10개)
    private final List<String> homeRecommendedStocks = new ArrayList<>(); // 전체 10개
    private final List<String> leaderStocks = new ArrayList<>(); // 대장주 1개 (100-200% 상승)
    private final List<String> highTrendingStocks = new ArrayList<>(); // 일반 인기주 3개 (50% 상승)
    private final List<String> mediumTrendingStocks = new ArrayList<>(); // 나머지 인기주 6개 (10-30% 상승)
    
    // 가격 변동률과 거래량 데이터
   
    private final Map<String, Long> volumeData = new ConcurrentHashMap<>();
    
    // 캔들 누적 데이터
    private final Map<String, CandleAccumulator> candleAccumulators = new ConcurrentHashMap<>();
    
    // 스케줄러
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    
    // 시간 카운터
    private int hourCount = 0;
    private int secCount = 0;
    
    // 인기주 관리
    private final Set<String> previousTrendingStocks = new HashSet<>(); // 이전 인기주
    private final Map<String, LocalDateTime> trendingStartTime = new ConcurrentHashMap<>(); // 인기주 시작 시간
    
    // 매수 압력 추적 (돈 읽는 사람들)
    private final Map<String, Integer> buyPressure = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastBuyTime = new ConcurrentHashMap<>();
    
    // 개별 주식 업데이트 타이머
    private final Map<String, ScheduledFuture<?>> individualStockTimers = new ConcurrentHashMap<>();
    
    // 배치 전송 타이머
    private ScheduledFuture<?> batchSender;
    
    // 배치 업데이트 저장소 (파이썬과 동일한 구조)
    private final Map<String, Double> batchUpdates = new ConcurrentHashMap<>();
    
    // 파이썬 서버와 동일한 설정
    private static final double UPDATE_INTERVAL = 0.8; // 0.8초마다 업데이트 (빠른 반응)
    private static final int ONE_DAY_SECONDS = 1440; // 1440분 = 1일 (0.8초 = 1분, 1440분 = 1일)
    
    /**
     * 주식 상태 클래스
     */
    private static class StockState {
        String name;
        ArrayList<Double> priceHistory = new ArrayList<>();
        ArrayList<Long> volumeHistory = new ArrayList<>();
        double longTermMean;
        long baseVolume;
        EventState event = new EventState();
        
        StockState(String name, double initialPrice) {
            this.name = name;
            this.longTermMean = initialPrice;
            this.baseVolume = ThreadLocalRandom.current().nextLong(10000, 100000);
            
            // 초기 가격과 거래량 추가
            if (initialPrice > 0 && !Double.isNaN(initialPrice) && !Double.isInfinite(initialPrice)) {
                this.priceHistory.add(initialPrice);
            }
            this.volumeHistory.add(this.baseVolume);
            
            // 히스토리 크기 제한
            limitHistorySize();
        }
        
        void limitHistorySize() {
            // 가격 히스토리 크기 제한 (최대 1000개)
            while (priceHistory.size() > 1000) {
                priceHistory.remove(0);
            }
            // 거래량 히스토리 크기 제한 (최대 1000개)
            while (volumeHistory.size() > 1000) {
                volumeHistory.remove(0);
            }
        }
    }
    
    /**
     * 이벤트 상태 클래스
     */
    private static class EventState {
        String type = "none";
        String phase = "";
        int timer = 0;
        int duration = 0;
        double startPrice = 0;
        String subPhase = "none";
        int subPhaseTimer = 0;
        int subPhaseDuration = 0;

        double targetPrice = 0;
        double dropRate = 0;
        double surgeRate = 0;
        
        void reset() {
            type = "none";
            phase = "";
            timer = 0;
            duration = 0;
            startPrice = 0;
            subPhase = "none";
            subPhaseTimer = 0;
            subPhaseDuration = 0;
           
            targetPrice = 0;
            dropRate = 0;
            surgeRate = 0;
        }
    }
    
    /**
     * 1분봉 누적 데이터 클래스
     */
    private static class CandleAccumulator {
        private double open;
        private double high;
        private double low;
        private double close;
        private long volume;
        private LocalDateTime startTime;
        private boolean isInitialized;
        private boolean hasUpdates;
        
        public CandleAccumulator(double initialPrice) {
            this.open = initialPrice;
            this.high = initialPrice;
            this.low = initialPrice;
            this.close = initialPrice;
            this.volume = 0;
            this.startTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            this.isInitialized = false;
            this.hasUpdates = false;
        }
        
        public void updatePrice(double newPrice, long tradeVolume) {
            if (!isInitialized) {
                this.open = newPrice;
                this.isInitialized = true;
            }
            
            this.high = Math.max(this.high, newPrice);
            this.low = Math.min(this.low, newPrice);
            this.close = newPrice;
            this.volume += tradeVolume;
            this.hasUpdates = true;
        }
        
        public void reset(double newPrice) {
            this.open = newPrice;
            this.high = newPrice;
            this.low = newPrice;
            this.close = newPrice;
            this.volume = 0;
            this.startTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            this.isInitialized = false;
            this.hasUpdates = false;
        }
        
        public boolean isComplete() {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            return hasUpdates && now.isAfter(startTime);
        }
        
        public boolean hasData() {
            return hasUpdates;
        }
    }

    public StockSimulator(StockWebSocketHandler webSocketHandler, 
                         CompanyConfig companyConfig, 
                         CandleData1MinRepository candle1MinRepo,
                         SimpleCandleManager candleManager) { // 생성자에 추가
        this.webSocketHandler = webSocketHandler;
        this.companyConfig = companyConfig;
        this.candle1MinRepo = candle1MinRepo;
        this.candleManager = candleManager; // 주입
    }

    @PostConstruct
    public void initialize() {
        System.out.println("🚀 주식 시뮬레이터 초기화 시작...");
        
        // 회사 목록 초기화
        initializeCompanies();
        
        // 간단한 캔들 매니저 초기화
        initializeSimpleCandleManager();
        
        // 개별 주식 업데이트 시작 (파이썬과 동일한 주기)
        startIndividualStockUpdates();
        
        System.out.println("✅ 주식 시뮬레이터 초기화 완료!");
    }

    /**
     * 1분봉 생성 시작
     */
    private void startCandleGeneration() {
        scheduler.scheduleAtFixedRate(() -> {
            generate1MinCandles();
        }, 60, 60, TimeUnit.SECONDS);
        
        // 매일 자정에 전날 종가 업데이트
        scheduler.scheduleAtFixedRate(() -> {
            updatePreviousDayClose();
        }, getDelayUntilMidnight(), 24 * 60 * 60 * 1000, TimeUnit.MILLISECONDS); // 24시간마다
    }
    
    /**
     * 자정까지의 지연 시간 계산
     */
    private long getDelayUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return ChronoUnit.MILLIS.between(now, midnight);
    }
    
    /**
     * 전날 종가 업데이트 (매일 자정)
     */
    private void updatePreviousDayClose() {
        for (String symbol : companies.keySet()) {
            double currentClose = companies.get(symbol).getClose();
            previousDayClose.put(symbol, currentClose);
            System.out.println("📅 전날 종가 업데이트: " + symbol + " = " + String.format("%.2f", currentClose));
        }
    }
    
    /**
     * 누적된 1분봉 데이터 반환
     */
    public CandleData1Min getAccumulated1MinCandle(String symbol, LocalDateTime timestamp) {
        CandleAccumulator accumulator = candleAccumulators.get(symbol);
        if (accumulator != null && accumulator.hasData()) {
            // 1분봉 데이터 생성
            CandleData1Min candle = new CandleData1Min(
                symbol, timestamp, 
                accumulator.open, accumulator.high, accumulator.low, accumulator.close, 
                accumulator.volume
            );
            
            // 누적 데이터 초기화
            accumulator.reset(accumulator.close);
            
            System.out.println("🕯️ 1분봉 생성: " + symbol + 
                             " O:" + String.format("%.2f", candle.getOpen()) +
                             " H:" + String.format("%.2f", candle.getHigh()) +
                             " L:" + String.format("%.2f", candle.getLow()) +
                             " C:" + String.format("%.2f", candle.getClose()) +
                             " V:" + candle.getVolume());
            
            return candle;
        }
        return null;
    }
    
    /**
     * 1분봉 생성
     */
    private void generate1MinCandles() {
    
        
        for (String symbol : companies.keySet()) {
            CandleAccumulator accumulator = candleAccumulators.get(symbol);
            if (accumulator != null && accumulator.isComplete()) {
                // 1분봉 데이터 생성
                double open = accumulator.open;
                double high = accumulator.high;
                double low = accumulator.low;
                double close = accumulator.close;
                long volume = accumulator.volume;
                
                // CandleService에 1분봉 데이터 전달 (이벤트 방식으로 구현 필요)
                System.out.println("🕯️ 1분봉 생성: " + symbol + 
                                 " O:" + String.format("%.2f", open) +
                                 " H:" + String.format("%.2f", high) +
                                 " L:" + String.format("%.2f", low) +
                                 " C:" + String.format("%.2f", close) +
                                 " V:" + volume);
                
                // 누적 데이터 초기화
                accumulator.reset(close);
            }
        }
    }
    
    /**
     * 회사 데이터 초기화
     */
    private void initializeCompanies() {
        System.out.println("📊 회사 정보 초기화 중...");
        
        try {
            companies.clear();
            stockStates.clear();
            currentPrices.clear();
            previousPrices.clear();
            previousDayClose.clear(); // 전날 종가 초기화
            todayOpenPrices.clear(); // 시가 초기화
            
            List<CompanyConfig.CompanyData> companyDataList = companyConfig.getCompanies();
            for (CompanyConfig.CompanyData companyData : companyDataList) {
                // CompanyData를 Company 객체로 변환
                Company company = Company.builder()
                        .symbol(companyData.getSymbol())
                        .name(companyData.getName())
                        .sector(companyData.getSector())
                        .description(companyData.getDescription())
                        .open(companyData.getInitialPrice())
                        .close(companyData.getInitialPrice())
                        .volume(ThreadLocalRandom.current().nextLong(10000, 100000))
                        .turnover(ThreadLocalRandom.current().nextLong(1000000, 10000000))
                        .per(ThreadLocalRandom.current().nextDouble(10, 30))
                        .pbr(ThreadLocalRandom.current().nextDouble(0.5, 3.0))
                        .psr(ThreadLocalRandom.current().nextDouble(1.0, 5.0))
                        .marketCap(ThreadLocalRandom.current().nextLong(100000000, 1000000000))
                        .dividendYield(ThreadLocalRandom.current().nextDouble(0, 5))
                        .roe(ThreadLocalRandom.current().nextDouble(5, 20))
                        .build();
                
                companies.put(company.getSymbol(), company);
                
                // 초기 가격 설정
                double initialPrice = companyData.getInitialPrice();
                currentPrices.put(company.getSymbol(), initialPrice);
                previousPrices.put(company.getSymbol(), initialPrice);
                
                // 시가 설정 (현재가보다 약간 낮게 설정하여 상승률 생성)
                double openPrice = initialPrice * ThreadLocalRandom.current().nextDouble(0.95, 1.05);
                todayOpenPrices.put(company.getSymbol(), openPrice);
                
                // 전날 종가를 현재가보다 약간 다르게 설정 (변동률 생성을 위해)
                // 더 균형잡힌 변동률 분포를 위해 가중치 랜덤 분포 사용
                double changePercent;
                double rand = Math.random();
                if (rand < 0.4) { // 40% 확률로 작은 변동률 (-2% ~ +2%)
                    changePercent = ThreadLocalRandom.current().nextDouble(-2.0, 2.0);
                } else if (rand < 0.7) { // 30% 확률로 중간 변동률 (-5% ~ +5%)
                    changePercent = ThreadLocalRandom.current().nextDouble(-5.0, 5.0);
                } else if (rand < 0.85) { // 15% 확률로 큰 변동률 (-15% ~ +15%)
                    changePercent = ThreadLocalRandom.current().nextDouble(-15.0, 15.0);
                } else if (rand < 0.95) { // 10% 확률로 매우 큰 변동률 (-30% ~ +30%)
                    changePercent = ThreadLocalRandom.current().nextDouble(-30.0, 30.0);
                } else { // 5% 확률로 극단적 변동률 (-50% ~ +100%)
                    if (Math.random() < 0.6) { // 상승 편향
                        changePercent = ThreadLocalRandom.current().nextDouble(30.0, 100.0);
                    } else { // 하락
                        changePercent = ThreadLocalRandom.current().nextDouble(-50.0, -20.0);
                    }
                }
                double previousDayPrice = initialPrice / (1 + changePercent / 100.0);
                previousDayPrice = Math.max(previousDayPrice, initialPrice * 0.3); // 최소 30%까지만 하락
                previousDayClose.put(company.getSymbol(), previousDayPrice);
                
                // 주봉, 월봉 기준 가격도 초기화 (랜덤한 과거 가격)
                double weekAgoPrice = initialPrice * ThreadLocalRandom.current().nextDouble(0.8, 1.2);
                double monthAgoPrice = initialPrice * ThreadLocalRandom.current().nextDouble(0.7, 1.3);
                weekAgoPrices.put(company.getSymbol(), weekAgoPrice);
                monthAgoPrices.put(company.getSymbol(), monthAgoPrice);
                
                // StockState 생성
                StockState state = new StockState(company.getSymbol(), initialPrice);
                stockStates.put(company.getSymbol(), state);
                
                // 거래량 데이터 초기화
                volumeData.put(company.getSymbol(), ThreadLocalRandom.current().nextLong(10000, 100000));
                
                System.out.println("  ✅ " + company.getSymbol() + 
                                 ": 현재가=" + String.format("%.2f", initialPrice) + 
                                 ", 시가=" + String.format("%.2f", openPrice) + 
                                 ", 시가기준변동률=" + String.format("%.2f", ((initialPrice - openPrice) / openPrice) * 100) + "%");
            }
            
            System.out.println("✅ 회사 정보 초기화 완료: " + companies.size() + "개 종목");
            
        } catch (Exception e) {
            System.err.println("❌ 회사 정보 초기화 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    

    
    /**
     * 고급 가격 시뮬레이션 시작 (Python 알고리즘 기반)
     */
    private void startAdvancedPriceSimulation() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<String> changedStocks = new ArrayList<>();
                
                for (String symbol : companies.keySet()) {
                    // 대장주는 별도 로직으로 처리하므로 제외
                    if (leaderStocks.contains(symbol)) {
                        continue;
                    }
                    
                    // 일반 종목들의 업데이트 확률을 크게 증가 (30% → 70%)
                    if (Math.random() < 0.7) {
                        double currentPrice = currentPrices.get(symbol);
                        double newPrice = generateNextPrice(symbol, currentPrice);
                        
                        if (Math.abs(newPrice - currentPrice) > 0.01) {
                            currentPrices.put(symbol, newPrice);
                            updateCompanyData(symbol, newPrice);
                            changedStocks.add(symbol);
                            
                            // 캔들 누적기 업데이트
                            CandleAccumulator accumulator = candleAccumulators.get(symbol);
                            if (accumulator != null) {
                                long volume = ThreadLocalRandom.current().nextLong(1000, 50000);
                                accumulator.updatePrice(newPrice, volume);
                            }
                        }
                    }
                }
                
                // 시간 카운터 업데이트
                secCount++;
                if (secCount >= 3600) {
                    secCount = 0;
                    hourCount++;
                    
                    // 8시간마다 장기 추세 조정
                    if (hourCount % 8 == 0) {
                        adjustLongTermTrends();
                    }
                }
                
            } catch (Exception e) {
                System.err.println("❌ 고급 가격 시뮬레이션 실패: " + e.getMessage());
            }
        }, 0, 1500, TimeUnit.MILLISECONDS); // 3초 → 1.5초로 단축
        
        // 대장주 전용 업데이트 (0.3초마다)
        startLeaderStockUpdates();
    }

    /**
     * 대장주 전용 업데이트 시작
     */
    private void startLeaderStockUpdates() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!leaderStocks.isEmpty()) {
                    for (String symbol : leaderStocks) {
                        if (Math.random() < 0.8) { // 70% → 80% 확률로 증가
                            updateLeaderStock(symbol);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ 대장주 업데이트 실패: " + e.getMessage());
            }
        }, 0, 250, TimeUnit.MILLISECONDS); // 0.3초 → 0.25초로 단축
        
        // 일반 인기주 전용 업데이트 (0.5초마다)
        startHighTrendingStockUpdates();
        
        System.out.println("👑 대장주 전용 업데이트 시작 (0.25초마다, 80% 확률)");
        System.out.println("🚀 일반 인기주 전용 업데이트 시작 (0.5초마다, 70% 확률)");
    }

    /**
     * 일반 인기주 전용 업데이트 시작
     */
    private void startHighTrendingStockUpdates() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!highTrendingStocks.isEmpty()) {
                    for (String symbol : highTrendingStocks) {
                        if (Math.random() < 0.7) { // 50% → 70% 확률로 증가
                            updateHighTrendingStock(symbol);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ 일반 인기주 업데이트 실패: " + e.getMessage());
            }
        }, 0, 500, TimeUnit.MILLISECONDS); // 0.8초 → 0.5초로 단축
    }

    /**
     * 대장주 업데이트 (강력한 상승 편향 - 50-400% 목표)
     */
    private void updateLeaderStock(String symbol) {
        double currentPrice = currentPrices.get(symbol);
        StockState state = stockStates.get(symbol);
        double newPrice;
        
        // 현재 상승률 계산
        double currentSurgePercent = ((currentPrice - state.longTermMean) / state.longTermMean) * 100.0;
        
        // 평균회귀 적용 - 현재가가 평균의 5배 이상이면 강제 하락 (400% 이상)
        if (currentPrice > state.longTermMean * 5.0) {
            double dropRate = ThreadLocalRandom.current().nextDouble(0.03, 0.08); // 3-8% 하락
            newPrice = currentPrice * (1 - dropRate);
            System.out.println("📉 [" + symbol + "] 👑대장주 평균회귀 하락 -" + String.format("%.1f", dropRate * 100) + "% (현재상승률: " + String.format("%.1f", currentSurgePercent) + "%)");
        } else {
            // 95% 확률로 상승, 5% 확률로 하락 (더욱 강한 상승 편향)
            if (Math.random() < 0.95) {
                // 상승률에 따른 차등 적용 (더 높은 상승률)
                double surgeRate;
                if (currentSurgePercent < 50) {
                    // 50% 미만: 5-12% 상승 (매우 빠른 상승)
                    surgeRate = ThreadLocalRandom.current().nextDouble(0.05, 0.12);
                } else if (currentSurgePercent < 100) {
                    // 50-100%: 3-8% 상승 (빠른 상승)
                    surgeRate = ThreadLocalRandom.current().nextDouble(0.03, 0.08);
                } else if (currentSurgePercent < 200) {
                    // 100-200%: 2-5% 상승 (중간 상승)
                    surgeRate = ThreadLocalRandom.current().nextDouble(0.02, 0.05);
                } else if (currentSurgePercent < 300) {
                    // 200-300%: 1-3% 상승 (안정적 상승)
                    surgeRate = ThreadLocalRandom.current().nextDouble(0.01, 0.03);
                } else {
                    // 300% 이상: 0.5-2% 상승 (조심스러운 상승)
                    surgeRate = ThreadLocalRandom.current().nextDouble(0.005, 0.02);
                }
                
                newPrice = currentPrice * (1 + surgeRate);
                System.out.println("👑 [" + symbol + "] 대장주 급등! +" + String.format("%.1f", surgeRate * 100) + "% (현재상승률: " + String.format("%.1f", currentSurgePercent) + "%)");
            } else {
                double dropRate = ThreadLocalRandom.current().nextDouble(0.003, 0.008); // 0.3-0.8% 하락 (매우 작은 하락)
                newPrice = currentPrice * (1 - dropRate);
                System.out.println("📉 [" + symbol + "] 👑대장주 조정 -" + String.format("%.1f", dropRate * 100) + "% (현재상승률: " + String.format("%.1f", currentSurgePercent) + "%)");
            }
        }
        
        // 점진적 가격 변동 적용
        applyGradualPriceChange(symbol, newPrice, ThreadLocalRandom.current().nextLong(500000, 1200000)); // 더 높은 거래량
    }

    /**
     * 일반 인기주 업데이트 (중간 상승 편향 - 30-50% 목표)
     */
    private void updateHighTrendingStock(String symbol) {
        double currentPrice = currentPrices.get(symbol);
        StockState state = stockStates.get(symbol);
        double newPrice;
        
        // 평균회귀 적용 - 현재가가 평균의 2배 이상이면 강제 하락
        if (currentPrice > state.longTermMean * 2.0) {
            double dropRate = ThreadLocalRandom.current().nextDouble(0.02, 0.05); // 2-5% 하락
            newPrice = currentPrice * (1 - dropRate);
            System.out.println("📉 [" + symbol + "] 🚀일반인기주 평균회귀 하락 -" + String.format("%.1f", dropRate * 100) + "% (점진적)");
        } else {
            // 75% 확률로 상승, 25% 확률로 하락 (강한 상승 편향)
            if (Math.random() < 0.75) {
                double surgeRate = ThreadLocalRandom.current().nextDouble(0.01, 0.03); // 1-3% 상승
                newPrice = currentPrice * (1 + surgeRate);
                System.out.println("🚀 [" + symbol + "] 일반인기주 상승! +" + String.format("%.1f", surgeRate * 100) + "% (점진적)");
            } else {
                double dropRate = ThreadLocalRandom.current().nextDouble(0.005, 0.015); // 0.5-1.5% 하락
                newPrice = currentPrice * (1 - dropRate);
                System.out.println("📉 [" + symbol + "] 🚀일반인기주 조정 -" + String.format("%.1f", dropRate * 100) + "% (점진적)");
            }
        }
        
        // 점진적 가격 변동 적용
        applyGradualPriceChange(symbol, newPrice, ThreadLocalRandom.current().nextLong(150000, 400000)); // 중간 거래량
    }

    /**
     * 다음 가격 생성 (Python 알고리즘 기반)
     */
    private double generateNextPrice(String symbol, double currentPrice) {
        // 주식 상태 초기화 (최초 호출 시)
        if (!stockStates.containsKey(symbol)) {
            stockStates.put(symbol, new StockState(symbol, currentPrice));
        }
        
        StockState state = stockStates.get(symbol);
        double S = currentPrice;
        
        // 이벤트 처리
        if (!"none".equals(state.event.type)) {
            S = processEventState(state, S);
        } else {
            // 일반 상태 처리
            S = processNormalState(state, S);
            
            // 새로운 이벤트 발생 여부 확인
            checkForEventTriggers(state, S);
        }
        
        // 최종 가격 조정
        S = finalizePrice(state, S);
        
        return S;
    }

    /**
     * 변동성 레벨 결정 (안정적이고 균형잡힌 변동률)
     */
    private double determineVolatilityLevel(StockState state, String symbol) {
        double random = Math.random();
        boolean isLeader = leaderStocks.contains(symbol);
        boolean isHighTrending = highTrendingStocks.contains(symbol);
        boolean isMediumTrending = mediumTrendingStocks.contains(symbol);
        boolean isRecommended = homeRecommendedStocks.contains(symbol);
        
        if (isLeader) {
            // 대장주: 개미털기용 화끈한 변동! 🔥
            if (random < 0.08) { // 8% 확률로 극대 상승 (2% → 8%로 증가)
                double extremeSurge = ThreadLocalRandom.current().nextDouble(0.05, 0.15);
                System.out.println("🔥 [" + symbol + "] 대장주 극대 상승! 개미들 털기 시작! " + String.format("%.1f", extremeSurge * 100) + "%");
                return extremeSurge;
            } else if (random < 0.20) { // 12% 확률로 큰 상승 (13% → 20%로 증가)
                double bigSurge = ThreadLocalRandom.current().nextDouble(0.03, 0.08);
                System.out.println("👑 [" + symbol + "] 대장주 큰 상승! " + String.format("%.1f", bigSurge * 100) + "%");
                return bigSurge;
            } else if (random < 0.35) { // 15% 확률로 중간 상승
                double mediumSurge = ThreadLocalRandom.current().nextDouble(0.015, 0.04);
                return mediumSurge;
            } else { // 65% 확률로 작은 변동 (83% → 65%로 감소)
                double smallChange = ThreadLocalRandom.current().nextDouble(0.005, 0.02);
                return smallChange;
            }
        } else if (isHighTrending) {
            // 일반 인기주: 20-60% 상승 범위
            if (random < 0.03) { // 3% 확률로 큰 상승
                double bigSurge = ThreadLocalRandom.current().nextDouble(0.02, 0.05);
                System.out.println("🚀 [" + symbol + "] 인기주 큰 상승! " + String.format("%.1f", bigSurge * 100) + "%");
                return bigSurge;
            } else if (random < 0.20) { // 17% 확률로 중간 상승
                double mediumSurge = ThreadLocalRandom.current().nextDouble(0.008, 0.02);
                return mediumSurge;
            } else { // 80% 확률로 작은 변동
                double smallChange = ThreadLocalRandom.current().nextDouble(0.001, 0.008);
                return smallChange;
            }
        } else if (isMediumTrending) {
            // 나머지 인기주: 5-25% 상승 범위
            if (random < 0.05) { // 5% 확률로 중간 상승
                double mediumSurge = ThreadLocalRandom.current().nextDouble(0.01, 0.025);
                return mediumSurge;
            } else if (random < 0.25) { // 20% 확률로 작은 상승
                double smallSurge = ThreadLocalRandom.current().nextDouble(0.003, 0.01);
                return smallSurge;
            } else { // 75% 확률로 매우 작은 변동
                double tinyChange = ThreadLocalRandom.current().nextDouble(0.0005, 0.005);
                return tinyChange;
            }
        } else {
            // 일반 종목: 안정적이고 균형잡힌 변동 (변동성 낮춤)
            if (random < 0.02) { // 2% 확률로 중간 변동 (5% → 2%로 감소)
                double mediumChange = ThreadLocalRandom.current().nextDouble(0.005, 0.015);
                return mediumChange;
            } else if (random < 0.15) { // 13% 확률로 작은 변동 (20% → 13%로 감소)
                double smallChange = ThreadLocalRandom.current().nextDouble(0.001, 0.005);
                return smallChange;
            } else { // 85% 확률로 매우 작은 변동 (75% → 85%로 증가)
                double tinyChange = ThreadLocalRandom.current().nextDouble(0.0002, 0.002);
                return tinyChange;
            }
        }
    }

    /**
     * 일반 상태 처리 (개선된 변동성)
     */
    private double processNormalState(StockState state, double S) {
        try {
            // 1. 기본 움직임 계산 (브라운 운동 + 평균 회귀)
            double baseMovement = calculateBaseMovement(S, state.longTermMean);
            S += baseMovement;
            
            // 2. 거래량 시뮬레이션
            long currentVolume = simulateVolume(state, S);
            state.volumeHistory.add(currentVolume);
            
            // 히스토리 크기 제한
            if (state.volumeHistory.size() > 1000) {
                if (!state.volumeHistory.isEmpty()) {
                    state.volumeHistory.remove(0);
                }
            }
            
            // 3. 변동성 레벨 결정 (종목별 차별화)
            double volatilityLevel = determineVolatilityLevel(state, state.name);
            
            // 4. 변동성 적용 (더 균형잡힌 방향성)
            double direction;
            boolean isLeader = leaderStocks.contains(state.name);
            boolean isHighTrending = highTrendingStocks.contains(state.name);
            boolean isMediumTrending = mediumTrendingStocks.contains(state.name);
            
            if (isLeader) {
                // 대장주: 75% 상승, 25% 하락 (조금 더 강한 상승)
                direction = Math.random() < 0.95 ? 1 : -1;
            } else if (isHighTrending) {
                // 일반 인기주: 65% 상승, 35% 하락 (하락 확률 증가)
                direction = Math.random() < 0.62 ? 1 : -1;
            } else if (isMediumTrending) {
                // 나머지 인기주: 60% 상승, 40% 하락 (하락 확률 증가)
                direction = Math.random() < 0.52 ? 1 : -1;
            } else {
                // 일반 종목: 45% 상승, 45% 하락 (하락 확률 증가)
                direction = Math.random() < 0.49 ? 1 : -1;
            }
            
            S = S * (1 + direction * volatilityLevel);
            
            // 5. 지지/저항선 계산
            double[] supportResistance = calculateSupportResistance(state);
            double support = supportResistance[0];
            double resistance = supportResistance[1];
            
            // 6. 투자 심리 효과 적용
            S = applyPsychologyEffects(state, S, currentVolume, support, resistance);
            
            // 7. 이벤트 트리거 체크
            checkForEventTriggers(state, S);
            
            // 8. 가격 유효성 검사 및 제한
            S = Math.max(10, Math.min(S, state.longTermMean * 10)); // 최대 10배 제한
            S = Math.round(S * 100.0) / 100.0; // 소수점 2자리
            
            // 9. 가격 히스토리에 추가
            state.priceHistory.add(S);
            if (state.priceHistory.size() > 1000) {
                if (!state.priceHistory.isEmpty()) {
                    state.priceHistory.remove(0);
                }
            }
            
            return S;
            
        } catch (Exception e) {
            System.err.println("❌ 일반 상태 처리 실패: " + e.getMessage());
            return state.longTermMean; // 에러 시 평균값 반환
        }
    }

    /**
     * 기본 움직임 계산 (평균 회귀 강화)
     */
    private double calculateBaseMovement(double S, double longTermMean) {
        // 1. 브라운 운동 (랜덤 워크) - 변동성 감소
        double dW = ThreadLocalRandom.current().nextGaussian() * Math.sqrt(DT);
        double brownianMotion = MU * S * DT + SIGMA * S * dW;
        
        // 2. 평균 회귀 강화 (더 강한 회귀력)
        double distance = Math.abs(S - longTermMean) / longTermMean;
        double reversionStrength = 0.005 + distance * 0.01; // 회귀력 증가
        double reversionForce = (longTermMean - S) * reversionStrength;
        
        // 3. 최소 변동 줄임 (0.5% 변동)
        double minMovement = S * 0.005 * (Math.random() < 0.5 ? 1 : -1);
        
        return brownianMotion + reversionForce + minMovement;
    }

    /**
     * 현실적인 거래량 시뮬레이션 (개선)
     */
    private long simulateVolume(StockState state, double S) {
        long baseVolume = state.baseVolume;
        
        // 1. 기본 거래량 (시간대별 변동)
        double timeFactor = 1.0;
        int currentHour = LocalDateTime.now().getHour();
        if (currentHour >= 9 && currentHour <= 15) {
            timeFactor = 1.2; // 장 시간대 거래량 증가
        } else if (currentHour >= 16 || currentHour <= 8) {
            timeFactor = 0.3; // 장 외 시간대 거래량 감소
        }
        
        // 2. 가격 변동에 따른 거래량 변화 (강화)
        double priceChangeFactor = 1.0;
        if (!state.priceHistory.isEmpty()) {
            double lastPrice = state.priceHistory.get(state.priceHistory.size() - 1);
            double priceChange = Math.abs(S - lastPrice) / lastPrice;
            
            // 가격 변동이 클수록 거래량 증가 (더 강한 연관성)
            if (priceChange > 0.1) { // 10% 이상 변동
                priceChangeFactor = 1.0 + Math.min(priceChange * 15, 8.0); // 최대 8배
            } else if (priceChange > 0.05) { // 5% 이상 변동
                priceChangeFactor = 1.0 + Math.min(priceChange * 10, 3.0); // 최대 3배
            } else if (priceChange < 0.01) { // 1% 미만 변동
                priceChangeFactor = 0.2 + priceChange * 30; // 최소 0.2배
            }
        }
        
        // 3. 랜덤 요소
        double randomFactor = ThreadLocalRandom.current().nextDouble(0.8, 1.2);
        
        // 4. 급등락 시 거래량 폭증
        double surgeFactor = 1.0;
        if (state.event.type.equals("surge")) {
            surgeFactor = ThreadLocalRandom.current().nextDouble(5.0, 15.0); // 5-15배
        }
        
        // 5. 최종 거래량 계산
        long finalVolume = Math.max(100, (long) (baseVolume * timeFactor * priceChangeFactor * randomFactor * surgeFactor));
        
        return finalVolume;
    }

    /**
     * 지지/저항선 계산 (null 값 처리 추가)
     */
    private double[] calculateSupportResistance(StockState state) {
        if (state.priceHistory.size() < 100) {
            return new double[]{state.longTermMean * 0.9, state.longTermMean * 1.1};
        }
        
        List<Double> recentPrices = new ArrayList<>(state.priceHistory.subList(
            Math.max(0, state.priceHistory.size() - 300), state.priceHistory.size()));
        
        // null 값 제거
        recentPrices.removeIf(Objects::isNull);
        
        // 유효한 데이터가 없으면 기본값 반환
        if (recentPrices.isEmpty()) {
            return new double[]{state.longTermMean * 0.9, state.longTermMean * 1.1};
        }
        
        Collections.sort(recentPrices);
        int size = recentPrices.size();
        double support = recentPrices.get(size / 4); // 25 percentile
        double resistance = recentPrices.get(size * 3 / 4); // 75 percentile
        
        return new double[]{support, resistance};
    }

    /**
     * 투자 심리 효과 적용 (거래량 기반 강화)
     */
    private double applyPsychologyEffects(StockState state, double S, long volume, double support, double resistance) {
        double volumeRatio = (double) volume / state.baseVolume;
        
        // 1. 거래량 폭증 시 심리적 효과 강화
        if (volumeRatio >= 3.0) {
            // 지지선 근처에서 거래량 터지면 강한 반등
            if (S < support * 1.02) {
                double bounceRate = ThreadLocalRandom.current().nextDouble(0.05, 0.15); // 5-15% 반등
                S *= (1 + bounceRate);
                System.out.println("💪 [" + state.name + "] 지지선 반등! 거래량 " + 
                                 String.format("%.1f", volumeRatio) + "배 → " + 
                                 String.format("%.1f", bounceRate * 100) + "% 반등");
            }
            // 저항선 근처에서 거래량 터지면 돌파 시도
            else if (S > resistance * 0.98) {
                double breakoutRate = ThreadLocalRandom.current().nextDouble(0.03, 0.12); // 3-12% 돌파
                S *= (1 + breakoutRate);
                System.out.println("🚀 [" + state.name + "] 저항선 돌파! 거래량 " + 
                                 String.format("%.1f", volumeRatio) + "배 → " + 
                                 String.format("%.1f", breakoutRate * 100) + "% 돌파");
            }
            // 중간 지점에서 거래량 터지면 방향성 확립
            else {
                double directionRate = ThreadLocalRandom.current().nextDouble(-0.08, 0.12); // -8~+12%
                S *= (1 + directionRate);
            }
        }
        // 2. 거래량 부족 시 횡보 강화
        else if (volumeRatio <= 0.5) {
            // 거래량이 적으면 횡보 확률 증가
            if (Math.random() < 0.9) {
                double sidewaysRate = ThreadLocalRandom.current().nextDouble(-0.02, 0.02); // -2~+2%
                S *= (1 + sidewaysRate);
            }
        }
        // 3. 일반적인 지지/저항 효과
        else {
            // 지지선 근처, 거래량 터지면 반등
            if (S < support * 1.01 && volume > state.baseVolume * 1.5) {
                S *= (1 + ThreadLocalRandom.current().nextDouble(0.01, 0.03));
            }
            // 저항선 근처, 거래량 터지면 돌파 시도
            else if (S > resistance * 0.95 && volume > state.baseVolume * 1.5) {
                S *= (1 + ThreadLocalRandom.current().nextDouble(-0.01, 0.05));
            }
        }
        
        return S;
    }

    /**
     * 이벤트 상태에 따른 가격 계산
     */
    private double processEventState(StockState state, double S) {
        EventState event = state.event;
        event.timer++;
        
        switch (event.type) {
            case "surge":
                return processSurgeEvent(state, S);
            case "ant_shake":
                return processAntShakeEvent(state, S);
            case "mean_reversion":
                return processMeanReversionEvent(state, S);
            default:
                return processNormalState(state, S);
        }
    }

    /**
     * 평균 회귀 이벤트 처리
     */
    private double processMeanReversionEvent(StockState state, double S) {
        EventState event = state.event;
        
        // 평균으로 천천히 회귀
        double targetMean = state.longTermMean;
        double reversionRate = 0.02; // 2% 회귀율
        
        if (S > targetMean) {
            S = S * (1 - reversionRate);
        } else {
            S = S * (1 + reversionRate);
        }
        
        // 이벤트 종료 조건
        if (event.timer >= event.duration || Math.abs(S - targetMean) / targetMean < 0.05) {
            event.type = "none";
            event.timer = 0;
            System.out.println("📉 [" + state.name + "] 평균 회귀 완료: " + String.format("%.2f", S));
        }
        
        return S;
    }

    /**
     * 이벤트 트리거 확인
     */
    private void checkForEventTriggers(StockState state, double S) {
        if (!"none".equals(state.event.type)) {
            return;
        }
        
        // 개미털기: 1시간마다 4% 확률 (3% → 4%로 증가)
        if (secCount != 0 && secCount % 3600 == 0 && ThreadLocalRandom.current().nextDouble() < 0.04) {
            if (state.priceHistory.size() > 100) {
                // 변동성이 적을 때 발생
                double recentVolatility = calculateRecentVolatility(state, S);
                if (recentVolatility < 0.01) {
                    triggerAntShakeEvent(state, S);
                    return;
                }
            }
        }
        
        // 대장주 전용 추가 개미털기: 30분마다 8% 확률
        boolean isLeader = leaderStocks.contains(state.name);
        if (isLeader && secCount != 0 && secCount % 1800 == 0 && ThreadLocalRandom.current().nextDouble() < 0.08) {
            if (state.priceHistory.size() > 50) {
                System.out.println("🔥 [" + state.name + "] 대장주 전용 개미털기 기회! 추가 털기 시작!");
                triggerAntShakeEvent(state, S);
                return;
            }
        }
        
        // 급등 후 급락: 2시간마다 5% 확률 (4% → 5%로 증가)
        if (secCount != 0 && secCount % 7200 == 0 && ThreadLocalRandom.current().nextDouble() < 0.05) {
            triggerSurgeEvent(state, S);
        }
    }

    /**
     * 개미털기 이벤트 트리거
     */
    private void triggerAntShakeEvent(StockState state, double S) {
        EventState event = state.event;
        event.type = "ant_shake";
        event.phase = "drop";
        event.timer = 0;
        event.duration = ThreadLocalRandom.current().nextInt(60, 121);
        event.startPrice = S;
        
        // 대장주는 더 화끈한 개미털기!
        boolean isLeader = leaderStocks.contains(state.name);
        if (isLeader) {
            event.dropRate = ThreadLocalRandom.current().nextDouble(0.7, 0.85); // 15-30% 급락
            System.out.println("🔥 [" + state.name + "] 대장주 개미털기 시작! 15-30% 급락 예정!");
        } else {
            event.dropRate = ThreadLocalRandom.current().nextDouble(0.9, 0.95); // 5-10% 급락
            System.out.println("🐜 [" + state.name + "] 개미털기 패턴 발생!");
        }
    }

    /**
     * 급등 이벤트 트리거
     */
    private void triggerSurgeEvent(StockState state, double S) {
        EventState event = state.event;
        event.type = "surge";
        event.phase = "surge";
        event.timer = 0;
        event.duration = ThreadLocalRandom.current().nextInt(60, 121);
        event.startPrice = S;
        
        // 대장주는 더 화끈한 급등!
        boolean isLeader = leaderStocks.contains(state.name);
        if (isLeader) {
            event.surgeRate = ThreadLocalRandom.current().nextDouble(0.2, 0.4); // 20-40% 급등
            System.out.println("🔥 [" + state.name + "] 대장주 화끈한 급등 시작! 20-40% 상승 예정!");
        } else {
            event.surgeRate = ThreadLocalRandom.current().nextDouble(0.1, 0.15); // 10-15% 급등
            System.out.println("🚀 [" + state.name + "] 급등주 포착!");
        }
        event.targetPrice = S * (1 + event.surgeRate);
    }

    /**
     * 급등 이벤트 처리
     */
    private double processSurgeEvent(StockState state, double S) {
        EventState event = state.event;
        
        switch (event.phase) {
            case "surge":
                // 급등 단계
                double surgeStep = (event.targetPrice - S) / Math.max(1, (event.duration - event.timer));
                S += surgeStep;
                
                if (event.timer >= event.duration) {
                    event.phase = "peak";
                    event.timer = 0;
                    event.duration = ThreadLocalRandom.current().nextInt(20, 61);
                    System.out.println("🏔️ [" + state.name + "] 고점 도달!");
                }
                    break;
                
            case "peak":
                // 고점 유지
                double noise = ThreadLocalRandom.current().nextDouble(-0.01, 0.01);
                S *= (1 + noise);
                
                if (event.timer >= event.duration) {
                    event.phase = "crash";
                    event.timer = 0;
                    event.duration = ThreadLocalRandom.current().nextInt(60, 181);
                    System.out.println("💥 [" + state.name + "] 급락 시작!");
                }
                    break;
                
            case "crash":
                // 급락 단계
                double targetCrashPrice = event.startPrice * 0.95;
                double crashStep = (S - targetCrashPrice) / Math.max(1, (event.duration - event.timer));
                S -= crashStep * 1.5;
                
                if (event.timer >= event.duration) {
                    System.out.println("✅ [" + state.name + "] 급등 사이클 종료");
                    event.reset();
                }
                    break;
        }
        
        return S;
    }

    /**
     * 개미털기 이벤트 처리
     */
    private double processAntShakeEvent(StockState state, double S) {
        EventState event = state.event;
        
        switch (event.phase) {
            case "drop":
                // 급락 단계
                double targetDropPrice = event.startPrice * event.dropRate;
                int timeLeft = Math.max(1, event.duration - event.timer);
                double dropStep = (S - targetDropPrice) / timeLeft;
                S -= dropStep * 1.5;
                
                if (event.timer >= event.duration) {
                    event.phase = "creep";
                    event.timer = 0;
                    event.duration = ThreadLocalRandom.current().nextInt(2700, 4501);
                    System.out.println("🥶 [" + state.name + "] 공포의 시간 시작...");
                }
                    break;
                
            case "creep":
                // 바닥 기어가기
                if (!"false_hope".equals(event.subPhase) && ThreadLocalRandom.current().nextDouble() < 0.005) {
                    event.subPhase = "false_hope";
                    event.subPhaseTimer = 0;
                    event.subPhaseDuration = ThreadLocalRandom.current().nextInt(15, 31);
                    System.out.println("🤔 [" + state.name + "] 어? 반등 신호인가...?");
                } else if ("false_hope".equals(event.subPhase)) {
                    S *= 1.001; // 희망 주기
                    event.subPhaseTimer++;
                    if (event.subPhaseTimer >= event.subPhaseDuration) {
                        event.subPhase = "crush_hope";
                        System.out.println("😂 [" + state.name + "] 희망은 끝났어!");
                    }
                } else if ("crush_hope".equals(event.subPhase)) {
                    S *= 0.98; // 희망 짓밟기
                    event.subPhase = "none";
                } else {
                    // 평소의 공포 구간
                    double noise = ThreadLocalRandom.current().nextDouble(-0.004, 0.002);
                    S *= (1 + noise);
                }
                
                if (event.timer >= event.duration) {
                    event.phase = "recover";
                    event.timer = 0;
                    event.duration = ThreadLocalRandom.current().nextInt(300, 601);
                    System.out.println("🚀🚀🚀 [" + state.name + "] V자 반등 시작!");
                }
                break;
                
            case "recover":
                // V자 반등
                double targetRecoverPrice = event.startPrice * 1.20;
                int recoverTimeLeft = Math.max(1, event.duration - event.timer);
                double recoverStep = (targetRecoverPrice - S) / recoverTimeLeft;
                S += recoverStep * 1.2;
                
                if (event.timer >= event.duration) {
                    System.out.println("✅ [" + state.name + "] 개미털기 종료");
                    event.reset();
                }
                break;
        }
        
        return S;
    }

    /**
     * 최근 변동성 계산
     */
    private double calculateRecentVolatility(StockState state, double currentPrice) {
        if (state.priceHistory.size() < 100) {
            return 0.1;
        }
        
        List<Double> recent = new ArrayList<>(state.priceHistory.subList(
            Math.max(0, state.priceHistory.size() - 100), state.priceHistory.size()));
        
        // null 값 제거
        recent.removeIf(Objects::isNull);
        
        // 유효한 데이터가 없으면 기본값 반환
        if (recent.isEmpty()) {
            return 0.1;
        }
        
        double mean = recent.stream().mapToDouble(d -> d).average().orElse(currentPrice);
        double variance = recent.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        
        return Math.sqrt(variance) / currentPrice;
    }

    /**
     * 급등/급락 후 평균 조정 (아주 가끔 발생)
     */
    private void checkForMeanAdjustment(StockState state, double S) {
        // 이벤트 종료 후 0.5% 확률로 평균 조정
        if ("none".equals(state.event.type) && ThreadLocalRandom.current().nextDouble() < 0.005) {
            double currentPrice = S;
            double currentMean = state.longTermMean;
            
            // 현재가와 평균의 차이가 클 때만 조정
            double priceDifference = Math.abs(currentPrice - currentMean) / currentMean;
            
            if (priceDifference > 0.3) { // 30% 이상 차이날 때
                // 평균을 현재가 쪽으로 50% 조정
                double newMean = (currentMean + currentPrice) / 2;
                state.longTermMean = newMean;
                
                System.out.println("🎯 [" + state.name + "] 평균 조정! " + 
                                 String.format("%.2f", currentMean) + " → " + 
                                 String.format("%.2f", newMean) + 
                                 " (현재가: " + String.format("%.2f", currentPrice) + ")");
            }
        }
    }

    /**
     * 최종 가격 조정 (평균 조정 체크 포함)
     */
    private double finalizePrice(StockState state, double S) {
        // 평균 조정 체크
        checkForMeanAdjustment(state, S);
        
        // 절대 가격 제한
        S = Math.max(10, Math.min(S, state.longTermMean * 4));
        S = Math.round(S * 100.0) / 100.0; // 소수점 2자리
        
        state.priceHistory.add(S);
        if (state.priceHistory.size() > 1000) {
            if (!state.priceHistory.isEmpty()) {
                state.priceHistory.remove(0);
            }
        }
        
        return S;
    }

    /**
     * 장기 트렌드 조정 (1시간마다)
     */
    private void adjustLongTermTrends() {
        for (StockState state : stockStates.values()) {
            // 5% 확률로 장기 평균 조정
            if (Math.random() < 0.05) {
                double adjustment = ThreadLocalRandom.current().nextDouble(-0.02, 0.02);
                state.longTermMean *= (1 + adjustment);
                System.out.println("📊 [" + state.name + "] 장기 트렌드 조정: " + 
                                 String.format("%.2f", state.longTermMean));
            }
        }
    }



    /**
     * 인기주 목록 업데이트 (24시간마다 - 새로운 인기주만 급등)
     */
    private void updateTrendingStocks() {
        System.out.println("🔄 인기주 선정 시작...");
        
        // 1. 이전 인기주 저장
        previousTrendingStocks.clear();
        previousTrendingStocks.addAll(trendingStocks);
        
        // 2. 새로운 인기주 선정 (랜덤하게 5개)
        List<String> allSymbols = new ArrayList<>(companies.keySet());
        Collections.shuffle(allSymbols);
        
        trendingStocks.clear();
        trendingStocks.addAll(allSymbols.subList(0, Math.min(5, allSymbols.size())));
        
    
        
        // 4. 새로운 인기주에 급등 이벤트 설정
        LocalDateTime now = LocalDateTime.now();
        for (String symbol : trendingStocks) {
            if (!previousTrendingStocks.contains(symbol)) {
                // 새로운 인기주 - 급등 시작
                trendingStartTime.put(symbol, now);
                triggerNewTrendingSurge(symbol);
                System.out.println("🚀 새로운 인기주 급등 시작: " + symbol);
            } else {
                // 기존 인기주 - 계속 유지
                System.out.println("📈 기존 인기주 유지: " + symbol);
            }
        }
        
        // 5. 이전 인기주 중 제외된 종목들 평균 회귀 시작
        for (String symbol : previousTrendingStocks) {
            if (!trendingStocks.contains(symbol)) {
                triggerMeanReversion(symbol);
                System.out.println("📉 이전 인기주 평균 회귀 시작: " + symbol);
                
                // 10% 확률로 평균 상향 조정
                if (Math.random() < 0.1) {
                    adjustMeanUpward(symbol);
                    System.out.println("⬆️ 평균 상향 조정: " + symbol);
                }
            }
        }
        
        System.out.println("✅ 인기주 선정 완료: " + trendingStocks.size() + "개");
    }

    /**
     * 추천주 목록 업데이트 (홈화면용 10개 = 대장주 1개 + 일반인기주 3개 + 나머지인기주 6개)
     */
    private void updateRecommendedStocks() {
        // 1. 전체 종목에서 랜덤 선택
        List<String> allSymbols = new ArrayList<>(companies.keySet());
        Collections.shuffle(allSymbols);
        
        // 2. 홈화면 구성 초기화
        leaderStocks.clear();
        highTrendingStocks.clear();
        mediumTrendingStocks.clear();
        homeRecommendedStocks.clear();
        
        // 3. 대장주 1개 (50-150% 상승) - 더 안정적인 범위
        if (allSymbols.size() >= 1) {
            leaderStocks.add(allSymbols.get(0));
            homeRecommendedStocks.addAll(leaderStocks);
        }
        
        // 4. 일반 인기주 3개 (20-60% 상승) - 고정 3개로 설정
        int highTrendingCount = 3;
        if (allSymbols.size() >= 1 + highTrendingCount) {
            for (int i = 1; i <= highTrendingCount && i < allSymbols.size(); i++) {
                highTrendingStocks.add(allSymbols.get(i));
            }
            homeRecommendedStocks.addAll(highTrendingStocks);
        }
        
        // 5. 나머지 인기주 6개 (5-25% 상승) - 더 안정적인 범위
        int remainingCount = 10 - leaderStocks.size() - highTrendingStocks.size();
        int startIndex = 1 + highTrendingCount;
        if (allSymbols.size() >= startIndex + remainingCount) {
            for (int i = startIndex; i < startIndex + remainingCount && i < allSymbols.size(); i++) {
                mediumTrendingStocks.add(allSymbols.get(i));
            }
            homeRecommendedStocks.addAll(mediumTrendingStocks);
        }
        
        // 6. 기존 trendingStocks도 업데이트 (호환성을 위해)
        trendingStocks.clear();
        trendingStocks.addAll(homeRecommendedStocks);
        
        System.out.println("🏠 홈화면 구성 완료: 총 " + homeRecommendedStocks.size() + "개");
        System.out.println("  👑 대장주: " + leaderStocks.size() + "개 (50-400% 상승) - " + leaderStocks);
        System.out.println("  🚀 일반인기주: " + highTrendingStocks.size() + "개 (20-60% 상승) - " + highTrendingStocks);
        System.out.println("  📈 나머지인기주: " + mediumTrendingStocks.size() + "개 (5-25% 상승) - " + mediumTrendingStocks);
    }
    
    /**
     * 회사 데이터 업데이트
     */
    private void updateCompanyData(String symbol, double newPrice) {
        Company company = companies.get(symbol);
        if (company != null) {
            // 이전 가격을 previousPrices에 저장
            previousPrices.put(symbol, company.getClose());
            
            company.setClose(newPrice);
            
            // 거래량 랜덤 증가
            long volumeChange = ThreadLocalRandom.current().nextLong(1000, 50000);
            company.setVolume(company.getVolume() + volumeChange);
            
            // 거래량 데이터 업데이트 (추천주 선정용)
            volumeData.put(symbol, company.getVolume());
            
            // 거래대금 업데이트
            company.setTurnover((long) (newPrice * company.getVolume()));
            
            // 시가총액 업데이트
            company.setMarketCap((long) (newPrice * ThreadLocalRandom.current().nextLong(10000000, 100000000)));
        }
    }


    
    /**
     * TickerMessage 생성 (전날 종가 기준 퍼센트 계산)
     */
    private TickerMessage createTickerMessage(List<String> symbols) {
        List<List<Object>> data = new ArrayList<>();
        
        for (String symbol : symbols) {
            double currentPrice = currentPrices.get(symbol);
            Double previousDayClosePrice = previousDayClose.get(symbol);
            
            // 전날 종가가 없거나 0이면 현재 가격을 기준으로 설정 (첫 번째 데이터)
            if (previousDayClosePrice == null || previousDayClosePrice == 0) {
                previousDayClosePrice = currentPrice;
                previousDayClose.put(symbol, currentPrice);
            }
            
            double changePercent = ((currentPrice - previousDayClosePrice) / previousDayClosePrice) * 100;
            
            // 디버깅용 로그 (모든 변동률 출력)
            System.out.println("🔍 변동률 계산 - " + symbol + 
                             ": 현재가=" + String.format("%.2f", currentPrice) + 
                             ", 전날종가=" + String.format("%.2f", previousDayClosePrice) + 
                             ", 변동률=" + String.format("%.2f", changePercent) + "%");
            
            List<Object> tickerData = Arrays.asList(symbol, currentPrice, changePercent);
            data.add(tickerData);
        }
        
        TickerMessage tickerMessage = new TickerMessage();
        tickerMessage.setType("ticker");
        tickerMessage.setData(data);
        return tickerMessage;
    }
    
    /**
     * 회사 정보 조회
     */
    public Company getCompany(String symbol) {
        Company company = companies.get(symbol);
        if (company != null) {
            double currentPrice = currentPrices.get(symbol);
            double todayOpenPrice = todayOpenPrices.getOrDefault(symbol, currentPrice);
            
            // 현재 가격으로 업데이트된 정보 반환 (저장된 시가 사용)
            Company updatedCompany = Company.builder()
                    .symbol(company.getSymbol())
                    .name(company.getName())
                    .sector(company.getSector())
                    .description(company.getDescription())
                    .open(todayOpenPrice) // 저장된 시가 사용
                    .close(currentPrice)
                    .volume(company.getVolume())
                    .turnover(company.getTurnover())
                    .per(company.getPer())
                    .pbr(company.getPbr())
                    .psr(company.getPsr())
                    .marketCap(company.getMarketCap())
                    .dividendYield(company.getDividendYield())
                    .roe(company.getRoe())
                    .build();
            return updatedCompany;
        }
        return null;
    }
    
    /**
     * 추천 종목 리스트 조회
     */
    public List<Company> getRecommendedStocks() {
        // 동적으로 거래량 높은 20개 선정
        updateRecommendedStocks();
        
        List<Company> recommended = new ArrayList<>();
        for (String symbol : homeRecommendedStocks) {
            Company company = getCompany(symbol);
            if (company != null) {
                recommended.add(company);
            }
        }
        return recommended;
    }

    /**
     * 실시간 인기주 리스트 조회 (상승률 높은 5개)
     */
    public List<Company> getTrendingStocks() {
        // 동적으로 상승률 높은 5개 선정
        updateTrendingStocks();
        
        List<Company> trending = new ArrayList<>();
        for (String symbol : trendingStocks) {
            Company company = getCompany(symbol);
            if (company != null) {
                trending.add(company);
            }
        }
        return trending;
    }
    
    /**
     * 기존 DB 데이터에서 가격 복원
     */
    private void restorePricesFromDatabase() {
        System.out.println("🔄 기존 DB 데이터에서 가격 복원 중...");
        
        try {
        for (String symbol : companies.keySet()) {
                // 1분봉에서 최신 데이터 조회
                Optional<CandleData1Min> latest1MinOpt = candle1MinRepo.findLatestBySymbol(symbol);
                
                if (latest1MinOpt.isPresent()) {
                    CandleData1Min latest1Min = latest1MinOpt.get();
                    double restoredPrice = latest1Min.getClose();
                    currentPrices.put(symbol, restoredPrice);
                    previousPrices.put(symbol, restoredPrice);
                    
                    // 전날 종가 설정 (이미 generatePreviousDayDataIfNeeded에서 설정됨)
                    double previousDayPrice = previousDayClose.getOrDefault(symbol, companies.get(symbol).getClose());
                    
                    // 회사 정보 업데이트
                    Company company = companies.get(symbol);
                    if (company != null) {
                        company.setClose(restoredPrice);
                    }
                    
                    System.out.println("  ✅ " + symbol + ": " + restoredPrice + " (전날종가: " + previousDayPrice + ")");
                } else {
                    // DB에 데이터가 없으면 초기 가격 사용
                    Company company = companies.get(symbol);
                    if (company != null) {
                        double initialPrice = company.getClose();
                        currentPrices.put(symbol, initialPrice);
                        previousPrices.put(symbol, initialPrice);
                        previousDayClose.put(symbol, initialPrice); // 전날 종가도 초기화
                        System.out.println("  ⚠️ " + symbol + ": 초기 가격 사용 (" + initialPrice + ")");
                    }
                }
            }
            
            System.out.println("✅ 가격 복원 완료 (" + currentPrices.size() + "개 종목)");
            
        } catch (Exception e) {
            System.err.println("❌ 가격 복원 실패: " + e.getMessage());
            // 실패 시 초기 가격으로 설정
            for (String symbol : companies.keySet()) {
                Company company = companies.get(symbol);
                if (company != null) {
                    double initialPrice = company.getClose();
                    currentPrices.put(symbol, initialPrice);
                    previousPrices.put(symbol, initialPrice);
                    previousDayClose.put(symbol, initialPrice); // 전날 종가도 초기화
                }
            }
        }
    }
    
    /**
     * 캔들 누적기 초기화 (복원된 가격으로)
     */
    private void initializeCandleAccumulators() {
        System.out.println("🔄 캔들 누적기 초기화 중...");
        
        for (String symbol : companies.keySet()) {
            double currentPrice = currentPrices.getOrDefault(symbol, 0.0);
            if (currentPrice > 0) {
                CandleAccumulator accumulator = new CandleAccumulator(currentPrice);
                candleAccumulators.put(symbol, accumulator);
            }
        }
        
        System.out.println("✅ 캔들 누적기 초기화 완료 (" + candleAccumulators.size() + "개)");
    }

    /**
     * 전날 데이터 빠르게 생성 (DB에 데이터가 없을 경우)
     */
    private void generatePreviousDayDataIfNeeded() {
        System.out.println("🔄 전날 데이터 빠르게 생성 중...");
        try {
            for (String symbol : companies.keySet()) {
                // 1분봉에서 최신 데이터 조회
                Optional<CandleData1Min> latest1MinOpt = candle1MinRepo.findLatestBySymbol(symbol);
                
                if (!latest1MinOpt.isPresent()) {
                    // DB에 데이터가 없으면 현재가와 비슷한 전날 종가 생성
                    double initialPrice = companies.get(symbol).getClose();
                    
                    // 전날 종가를 현재가의 90~110% 범위로 설정 (현실적인 변동률)
                    double previousDayPrice = initialPrice * ThreadLocalRandom.current().nextDouble(0.9, 1.1);
                    
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime previousDay = now.minusDays(1).withHour(23).withMinute(59).withSecond(0).withNano(0);
                    
                    // 전날 종가만 저장 (1개 캔들)
                    CandleData1Min candle = new CandleData1Min(
                        symbol, previousDay,
                        previousDayPrice, previousDayPrice, previousDayPrice, previousDayPrice,
                        ThreadLocalRandom.current().nextLong(1000, 50000)
                    );
                    candle1MinRepo.save(candle);
                    
                    // 전날 종가 설정
                    previousDayClose.put(symbol, previousDayPrice);
                    
                    System.out.println("  ✅ 전날 종가 생성: " + symbol + 
                                     " (초기가: " + String.format("%.2f", initialPrice) + 
                                     ", 전날종가: " + String.format("%.2f", previousDayPrice) + 
                                     ", 예상변동률: " + String.format("%.2f", ((initialPrice - previousDayPrice) / previousDayPrice) * 100) + "%)");
                } else {
                    System.out.println("  ⚠️ 전날 데이터 생성 불필요: " + symbol + " (최신 데이터 있음)");
                }
            }
            System.out.println("✅ 전날 데이터 생성 완료");
        } catch (Exception e) {
            System.err.println("❌ 전날 데이터 생성 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 현재 가격 조회
     */
    public double getCurrentPrice(String symbol) {
        return currentPrices.getOrDefault(symbol, 0.0);
    }
    
    /**
     * 모든 회사 심볼 조회
     */
    public Set<String> getAllSymbols() {
        return companies.keySet();
    }

    /**
     * 전체 종목 목록 조회 (페이지네이션 지원)
     */
    public List<Company> getAllStocks(int page, int size) {
        List<Company> allCompanies = new ArrayList<>();
        
        for (String symbol : companies.keySet()) {
            Company company = getCompany(symbol);
            if (company != null) {
                allCompanies.add(company);
            }
        }
        
        // 페이지네이션 적용
        int start = page * size;
        int end = Math.min(start + size, allCompanies.size());
        
        if (start >= allCompanies.size()) {
            return new ArrayList<>();
        }
        
        return allCompanies.subList(start, end);
    }
    
    /**
     * 전체 종목 수 조회
     */
    public int getTotalStockCount() {
        return companies.size();
    }
    

    /**
     * 새로운 인기주 급등 트리거
     */
    private void triggerNewTrendingSurge(String symbol) {
        EventState event = stockStates.get(symbol).event;
        event.type = "surge";
        event.phase = "surge";
        event.timer = 0;
        event.duration = ThreadLocalRandom.current().nextInt(60, 121);
        event.startPrice = currentPrices.get(symbol);
        event.surgeRate = ThreadLocalRandom.current().nextDouble(0.1, 0.15);
        event.targetPrice = event.startPrice * (1 + event.surgeRate);
        System.out.println("🚀 [" + symbol + "] 새로운 인기주 급등 시작!");
    }

    /**
     * 기존 인기주 평균 회귀 트리거
     */
    private void triggerMeanReversion(String symbol) {
        EventState event = stockStates.get(symbol).event;
        event.type = "mean_reversion";
        event.phase = "revert";
        event.timer = 0;
        event.duration = ThreadLocalRandom.current().nextInt(300, 601); // 평균 회귀 시간
        event.startPrice = currentPrices.get(symbol);
        System.out.println("📉 [" + symbol + "] 기존 인기주 평균 회귀 시작!");
    }

    /**
     * 평균 상향 조정
     */
    private void adjustMeanUpward(String symbol) {
        double currentMean = stockStates.get(symbol).longTermMean;
        double currentPrice = currentPrices.get(symbol);
        
        // 현재가와 평균의 차이가 클 때만 조정
        double priceDifference = Math.abs(currentPrice - currentMean) / currentMean;
        
        if (priceDifference > 0.3) { // 30% 이상 차이날 때
            // 평균을 현재가 쪽으로 50% 조정
            double newMean = (currentMean + currentPrice) / 2;
            stockStates.get(symbol).longTermMean = newMean;
            
            System.out.println("⬆️ [" + symbol + "] 평균 상향 조정! " + 
                             String.format("%.2f", currentMean) + " → " + 
                             String.format("%.2f", newMean) + 
                             " (현재가: " + String.format("%.2f", currentPrice) + ")");
        }
    }


    /**
     * 주식 업데이트 및 브로드캐스트 통합 메서드
     */
    private void broadcastStockUpdate(String symbol, double newPrice, long volume) {
        double currentPrice = currentPrices.get(symbol);
        
        if (Math.abs(newPrice - currentPrice) > 0.01) {
            currentPrices.put(symbol, newPrice);
            updateCompanyData(symbol, newPrice);
            
            // 캔들 누적기 업데이트
            CandleAccumulator accumulator = candleAccumulators.get(symbol);
            if (accumulator != null) {
                accumulator.updatePrice(newPrice, volume);
            }
            
            // WebSocket 브로드캐스트 (모든 구독자에게 전송)
            TickerMessage message = createTickerMessage(Collections.singletonList(symbol));
            webSocketHandler.broadcastToAllStocks(message);
            
            // 상세보기 구독자에게 전송
            webSocketHandler.broadcastToDetail(symbol, message);
        }
    }


    /**
     * 매수 압력 시뮬레이션 (돈 읽는 사람들)
     */
    private void simulateBuyPressure() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (String symbol : companies.keySet()) {
                    // 랜덤하게 매수 관심 증가 (더 자주)
                    if (Math.random() < 0.35) { // 25% → 35% 확률로 증가
                        buyPressure.put(symbol, buyPressure.getOrDefault(symbol, 0) + 1);
                        lastBuyTime.put(symbol, LocalDateTime.now());
                        
                        // 매수 압력이 높으면 "돈 읽는 사람" 효과 발동
                        int pressure = buyPressure.get(symbol);
                        if (pressure >= 2) { // 2명 이상 관심 시 유지
                            System.out.println("💰 [" + symbol + "] 돈 읽는 사람들 몰림! 매수 압력: " + pressure);
                            triggerBuyPressureEffect(symbol, pressure);
                        }
                    }
                }
                
                // 매수 압력 자연 감소 (시간이 지나면 관심 줄어듦)
                LocalDateTime now = LocalDateTime.now();
                buyPressure.entrySet().removeIf(entry -> {
                    String symbol = entry.getKey();
                    LocalDateTime lastTime = lastBuyTime.get(symbol);
                    if (lastTime != null && Duration.between(lastTime, now).toSeconds() > 20) { // 25초 → 20초로 단축
                        System.out.println("📉 [" + symbol + "] 매수 관심 감소");
                        return true;
                    }
                    return false;
                });
                
            } catch (Exception e) {
                System.err.println("❌ 매수 압력 시뮬레이션 실패: " + e.getMessage());
            }
        }, 0, 3000, TimeUnit.MILLISECONDS); // 5초 → 3초로 단축하여 더 자주 체크
    }

    /**
     * 매수 압력 효과 발동 (점진적 내가 사면 떨어져 현상)
     */
    private void triggerBuyPressureEffect(String symbol, int pressure) {
        double currentPrice = currentPrices.get(symbol);
        
        // 1단계: 초기 상승 (돈 읽는 사람들이 몰림) - 점진적 적용
        double initialSurge = ThreadLocalRandom.current().nextDouble(0.01, 0.03); // 1-3% 상승
        double newPrice = currentPrice * (1 + initialSurge);
        
        System.out.println("📈 [" + symbol + "] 초기 상승! +" + String.format("%.1f", initialSurge * 100) + "% (점진적)");
        
        // 점진적 상승 적용
        applyGradualPriceChange(symbol, newPrice, ThreadLocalRandom.current().nextLong(80000, 150000));
        
        // 2단계: 지연 후 급락 (내가 사면 떨어져) - 점진적 적용
        scheduler.schedule(() -> {
            try {
                double currentPriceNow = currentPrices.get(symbol);
                double dropRate = ThreadLocalRandom.current().nextDouble(0.02, 0.05); // 2-5% 급락
                double droppedPrice = currentPriceNow * (1 - dropRate);
                
                System.out.println("💸 [" + symbol + "] 내가 사면 떨어져! -" + String.format("%.1f", dropRate * 100) + "% (점진적)");
                
                // 점진적 급락 적용
                applyGradualPriceChange(symbol, droppedPrice, ThreadLocalRandom.current().nextLong(120000, 200000));
                
                // 매수 압력 초기화
                buyPressure.remove(symbol);
                lastBuyTime.remove(symbol);
                
            } catch (Exception e) {
                System.err.println("❌ 급락 효과 실패: " + e.getMessage());
            }
        }, ThreadLocalRandom.current().nextLong(3000, 8000), TimeUnit.MILLISECONDS); // 3-8초 후 급락
    }

    /**
     * 점진적 가격 변동 (한번에 0.5% 이하로 제한)
     */
    private void applyGradualPriceChange(String symbol, double targetPrice, long baseVolume) {
        double currentPrice = currentPrices.get(symbol);
        double totalChange = targetPrice - currentPrice;
        double totalChangePercent = Math.abs(totalChange / currentPrice);
        
        // 변동폭이 0.5% 이하면 즉시 적용
        if (totalChangePercent <= 0.005) {
            broadcastStockUpdate(symbol, targetPrice, baseVolume);
            return;
        }
        
        // 0.5%씩 나누어 적용하기 위해 필요한 단계 수 계산
        int steps = (int) Math.ceil(totalChangePercent / 0.005); // 0.5%씩 나누기
        double stepChange = totalChange / steps;
        
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            final double stepPrice = currentPrice + (stepChange * step);
            final long stepVolume = baseVolume + ThreadLocalRandom.current().nextLong(0, 5000);
            
            scheduler.schedule(() -> {
                try {
                    broadcastStockUpdate(symbol, stepPrice, stepVolume);
                } catch (Exception e) {
                    System.err.println("❌ 점진적 가격 변동 실패 [" + symbol + "]: " + e.getMessage());
                }
            }, i * 400, TimeUnit.MILLISECONDS); // 0.4초 간격으로 적용
        }
        
        // 디버그 로그
        System.out.println("🔄 [" + symbol + "] 점진적 변동: " + String.format("%.1f", totalChangePercent * 100) + "% → " + steps + "단계 (0.2초 간격)");
    }

    private void startIndividualStockUpdates() {
        System.out.println("🔄 산발적 개별 주식 업데이트 시작 (파이썬 스타일: 0.5초 주기)");
        
        // 각 주식마다 다른 시작 시간과 업데이트 확률을 설정
        List<String> allSymbols = new ArrayList<>(companies.keySet());
        Collections.shuffle(allSymbols); // 랜덤 순서로 섞기
        
        for (int i = 0; i < allSymbols.size(); i++) {
            String symbol = allSymbols.get(i);
            
            // 각 주식마다 다른 시작 지연 시간 (0~2초 랜덤)
            long initialDelay = ThreadLocalRandom.current().nextLong(0, 2000);
            
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> {
                    // 업데이트 확률을 랜덤하게 설정 (30%~80%) - 산발적 업데이트
                    double updateProbability = ThreadLocalRandom.current().nextDouble(0.3, 0.8);
                    if (Math.random() < updateProbability) {
                        updateIndividualStock(symbol);
                    }
                },
                initialDelay, // 랜덤한 시작 지연
                (long)(UPDATE_INTERVAL * 1000), // 0.8초를 밀리초로 변환
                TimeUnit.MILLISECONDS
            );
            individualStockTimers.put(symbol, future);
        }
        
        // 배치 전송 시작 (0.5초마다 - 빠른 반응)
        batchSender = scheduler.scheduleAtFixedRate(
            this::sendBatchUpdates,
            500, // 0.5초 후 시작
            500, // 0.5초마다 배치 전송
            TimeUnit.MILLISECONDS
        );
        
        System.out.println("✅ 산발적 업데이트 설정 완료: " + allSymbols.size() + "개 종목 (0.8초 주기, 30-80% 확률)");
    }
    
    /**
     * 간단한 캔들 매니저 초기화 (파이썬과 동일한 구조)
     */
    private void initializeSimpleCandleManager() {
        System.out.println("📊 파이썬 스타일 간단한 캔들 매니저 초기화 시작...");
        for (String symbol : companies.keySet()) {
            double currentPrice = currentPrices.get(symbol);
            candleManager.initializeStock(symbol, currentPrice);
        }
        System.out.println("✅ 파이썬 스타일 간단한 캔들 매니저 초기화 완료!");
    }

    /**
     * 파이썬과 동일한 API: 샘플링된 데이터 조회
     */
    public List<List<Object>> getSampledData(String symbol, String period) {
        return candleManager.getSampledData(symbol, period);
    }
    
    /**
     * 파이썬과 동일한 API: 현재 가격들 조회
     */
    public Map<String, Double> getCurrentPrices() {
        return candleManager.getCurrentPrices();
    }
    
    /**
     * 파이썬과 동일한 API: 모든 종목 심볼 조회
     */
    public List<String> getAllStockSymbols() {
        return new ArrayList<>(companies.keySet());
    }
    
    /**
     * 파이썬과 동일한 API: 간단한 캔들 데이터 조회
     */
    public List<Map<String, Object>> getSimpleCandles(String symbol, String period) {
        List<SimpleCandleManager.CandleData> candles = candleManager.getCandles(symbol, period);
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (SimpleCandleManager.CandleData candle : candles) {
            Map<String, Object> candleMap = new HashMap<>();
            candleMap.put("time", candle.time);
            candleMap.put("open", candle.open);
            candleMap.put("high", candle.high);
            candleMap.put("low", candle.low);
            candleMap.put("close", candle.close);
            candleMap.put("volume", candle.volume);
            result.add(candleMap);
        }
        
        return result;
    }
    
    /**
     * 캔들 형태의 데이터 조회 (Flutter 클라이언트용)
     */
    public List<Map<String, Object>> getCandleData(String symbol, String period) {
        return candleManager.getCandleData(symbol, period);
    }
    
    /**
     * 고급 캔들 데이터 조회 (실제 OHLCV 계산)
     */
    public List<Map<String, Object>> getAdvancedCandleData(String symbol, String period) {
        return candleManager.getAdvancedCandleData(symbol, period);
    }

    /**
     * 개별 주식 업데이트 (파이썬과 동일한 로직)
     */
    private void updateIndividualStock(String symbol) {
        try {
            double currentPrice = currentPrices.get(symbol);
            if (currentPrice <= 0) {
                System.out.println("⚠️ [" + symbol + "] 현재 가격이 0 이하: " + currentPrice);
                return;
            }
            
            // 파이썬의 brownian_motion과 동일한 가격 생성
            double newPrice = generateNextPrice(symbol, currentPrice);
            
            // 가격 변화 확인
            double changePercent = ((newPrice - currentPrice) / currentPrice) * 100.0;
            System.out.println("💰 [" + symbol + "] 가격 변동: " + String.format("%.2f", currentPrice) + " → " + String.format("%.2f", newPrice) + " (" + String.format("%.2f", changePercent) + "%)");
            
            // 가격 업데이트
            currentPrices.put(symbol, newPrice);
            
            // 배치 업데이트에 추가
            batchUpdates.put(symbol, newPrice);
            
            // 간단한 캔들 매니저에 가격 데이터 추가 (가상 시간 사용)
            candleManager.addPricePoint(symbol, 0, newPrice); // timestamp는 무시하고 가상 시간 사용
            
        } catch (Exception e) {
            System.err.println("❌ [" + symbol + "] 개별 주식 업데이트 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    

    
    /**
     * 배치 업데이트 전송 ([symbol, price, day%, week%, month%] 형태)
     */
    private void sendBatchUpdates() {
        if (batchUpdates.isEmpty()) return;
        
        try {
            List<List<Object>> data = new ArrayList<>();
            for (Map.Entry<String, Double> entry : batchUpdates.entrySet()) {
                String symbol = entry.getKey();
                double newPrice = entry.getValue();
                
                // 기간별 변동률 계산
                double dayChangePercent = calculateDayChangePercent(symbol, newPrice);
                double weekChangePercent = calculateWeekChangePercent(symbol, newPrice);
                double monthChangePercent = calculateMonthChangePercent(symbol, newPrice);
                
                // [symbol, price, day%, week%, month%] 형태로 데이터 추가
                data.add(Arrays.asList(symbol, newPrice, dayChangePercent, weekChangePercent, monthChangePercent));
            }
            
            Map<String, Object> batchMessage = new HashMap<>();
            batchMessage.put("type", "ticker");
            batchMessage.put("data", data);
            
            // 웹소켓으로 배치 전송
            webSocketHandler.sendBatchUpdate(batchMessage);
            
            // 배치 크기 로그 출력
            System.out.println("📦 배치 업데이트 전송: " + data.size() + "개 종목");
            
            // 이전 가격 업데이트 (다음 계산을 위해)
            for (Map.Entry<String, Double> entry : batchUpdates.entrySet()) {
                previousPrices.put(entry.getKey(), entry.getValue());
            }
            
            // 배치 클리어
            batchUpdates.clear();
            
        } catch (Exception e) {
            System.err.println("❌ 배치 업데이트 전송 실패: " + e.getMessage());
        }
    }
    
    /**
     * 일봉 변동률 계산 (시가 기준)
     */
    private double calculateDayChangePercent(String symbol, double newPrice) {
        double todayOpenPrice = todayOpenPrices.getOrDefault(symbol, newPrice);
        if (todayOpenPrice == 0) return 0.0;
        
        double changePercent = ((newPrice - todayOpenPrice) / todayOpenPrice) * 100.0;
        return Math.round(changePercent * 100.0) / 100.0;
    }
    
    /**
     * 주봉 변동률 계산 (1주일 전 기준)
     */
    private double calculateWeekChangePercent(String symbol, double newPrice) {
        double weekAgoPrice = weekAgoPrices.getOrDefault(symbol, newPrice);
        if (weekAgoPrice == 0) return 0.0;
        
        double changePercent = ((newPrice - weekAgoPrice) / weekAgoPrice) * 100.0;
        return Math.round(changePercent * 100.0) / 100.0;
    }
    
    /**
     * 월봉 변동률 계산 (1개월 전 기준)
     */
    private double calculateMonthChangePercent(String symbol, double newPrice) {
        double monthAgoPrice = monthAgoPrices.getOrDefault(symbol, newPrice);
        if (monthAgoPrice == 0) return 0.0;
        
        double changePercent = ((newPrice - monthAgoPrice) / monthAgoPrice) * 100.0;
        return Math.round(changePercent * 100.0) / 100.0;
    }
    
    /**
     * 변동률 계산 (전날 종가 기준) - 기존 호환성 유지
     */
    private double calculateChangePercent(String symbol, double newPrice) {
        return calculateDayChangePercent(symbol, newPrice);
    }

}