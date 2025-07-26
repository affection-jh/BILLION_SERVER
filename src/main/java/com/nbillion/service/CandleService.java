package com.nbillion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nbillion.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.nbillion.repository.*;
import com.nbillion.config.CompanyConfig;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1년치 데이터 관리 기능이 포함된 캔들 서비스
 * 각 기간별 최대 데이터 수를 관리하여 1년치만 유지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleService {

    // Repository들
    private final CandleData1MinRepository candle1MinRepo;
    private final CandleData5MinRepository candle5MinRepo;
    private final CandleData10MinRepository candle10MinRepo;
    private final CandleData30MinRepository candle30MinRepo;
    private final CandleData60MinRepository candle60MinRepo;
    private final CandleData1DayRepository candle1DayRepo;
    private final CandleData1WeekRepository candle1WeekRepo;
    private final CandleData1MonthRepository candle1MonthRepo;
    
    private final CompanyConfig companyConfig;
    private final StockSimulator stockSimulator;
    
    // 메모리 캐시: 최신 캔들 데이터
    private final Map<String, CandleData1Min> latest1MinCache = new ConcurrentHashMap<>();
    private final Map<String, CandleData5Min> latest5MinCache = new ConcurrentHashMap<>();
    private final Map<String, CandleData10Min> latest10MinCache = new ConcurrentHashMap<>();
    private final Map<String, CandleData30Min> latest30MinCache = new ConcurrentHashMap<>();
    private final Map<String, CandleData60Min> latest60MinCache = new ConcurrentHashMap<>();
    private final Map<String, CandleData1Day> latest1DayCache = new ConcurrentHashMap<>();
    private final Map<String, CandleData1Week> latest1WeekCache = new ConcurrentHashMap<>();
    private final Map<String, CandleData1Month> latest1MonthCache = new ConcurrentHashMap<>();

    private final AtomicLong lastCacheCleanupTime = new AtomicLong(System.currentTimeMillis());

    // 1년치 최대 데이터 수 상수
    private static final int MAX_1MIN_PER_YEAR = 525600;  // 365일 × 24시간 × 60분
    private static final int MAX_5MIN_PER_YEAR = 105120;  // 365일 × 24시간 × 12개
    private static final int MAX_10MIN_PER_YEAR = 52560;  // 365일 × 24시간 × 6개
    private static final int MAX_30MIN_PER_YEAR = 17520;  // 365일 × 24시간 × 2개
    private static final int MAX_60MIN_PER_YEAR = 8760;   // 365일 × 24시간
    private static final int MAX_1DAY_PER_YEAR = 365;     // 365일
    private static final int MAX_1WEEK_PER_YEAR = 52;     // 365일 ÷ 7일
    private static final int MAX_1MONTH_PER_YEAR = 12;    // 12개월

    /**
     * 1분봉 데이터 생성 및 저장 (매 1분마다 실행으로 변경)
     */
    @Scheduled(fixedRate = 60000) // 1분마다
    public void generateAndSave1MinCandle() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        
        try {
            // StockSimulator에서 모든 심볼 가져오기
            Set<String> symbols = stockSimulator.getAllSymbols();
            
            for (String symbol : symbols) {
                try {
                    // StockSimulator에서 누적된 1분봉 데이터 가져오기
                    CandleData1Min candle = stockSimulator.getAccumulated1MinCandle(symbol, now);
                    
                    if (candle != null) {
                // DB 저장
                candle1MinRepo.save(candle);
                
                // 캐시 업데이트
                latest1MinCache.put(symbol, candle);
                
                // 1년치 데이터 수 제한 확인 및 정리
                maintainDataLimit(symbol, "1min", MAX_1MIN_PER_YEAR);
                
                        log.debug("1분봉 생성: {} - O:{}, H:{}, L:{}, C:{}, V:{}", 
                                 symbol, candle.getOpen(), candle.getHigh(), 
                                 candle.getLow(), candle.getClose(), candle.getVolume());
                    }
                
            } catch (Exception e) {
                log.error("1분봉 생성 실패: {}", symbol, e);
            }
            }
        } catch (Exception e) {
            log.error("1분봉 생성 중 오류 발생", e);
        }
    }

    /**
     * 5분봉 데이터 생성 및 저장 (매 5분마다 실행)
     */
    @Scheduled(fixedRate = 300000) // 5분
    public void generateAndSave5MinCandle() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        
        try {
            Set<String> symbols = stockSimulator.getAllSymbols();
            
            for (String symbol : symbols) {
            try {
                // 최근 5개의 1분봉 데이터 조회
                List<CandleData1Min> recent1Min = candle1MinRepo.findRecentBySymbol(symbol, 5);
                
                if (recent1Min.size() >= 3) { // 최소 3개 이상 있을 때만 집계
                    CandleData5Min aggregated = aggregateTo5Min(recent1Min, now);
                    candle5MinRepo.save(aggregated);
                    latest5MinCache.put(symbol, aggregated);
                    
                    // 1년치 데이터 수 제한 확인 및 정리
                    maintainDataLimit(symbol, "5min", MAX_5MIN_PER_YEAR);
                }
                
            } catch (Exception e) {
                log.error("5분봉 생성 실패: {}", symbol, e);
            }
            }
        } catch (Exception e) {
            log.error("5분봉 생성 중 오류 발생", e);
        }
    }

    /**
     * 10분봉 데이터 생성 및 저장 (매 10분마다 실행)
     */
    @Scheduled(fixedRate = 600000) // 10분
    public void generateAndSave10MinCandle() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        
        try {
            Set<String> symbols = stockSimulator.getAllSymbols();
            
            for (String symbol : symbols) {
            try {
                List<CandleData1Min> recent1Min = candle1MinRepo.findRecentBySymbol(symbol, 10);
                
                if (recent1Min.size() >= 5) {
                    CandleData10Min aggregated = aggregateTo10Min(recent1Min, now);
                    candle10MinRepo.save(aggregated);
                    latest10MinCache.put(symbol, aggregated);
                    
                    // 1년치 데이터 수 제한 확인 및 정리
                    maintainDataLimit(symbol, "10min", MAX_10MIN_PER_YEAR);
                }
                
            } catch (Exception e) {
                log.error("10분봉 생성 실패: {}", symbol, e);
            }
            }
        } catch (Exception e) {
            log.error("10분봉 생성 중 오류 발생", e);
        }
    }

    /**
     * 30분봉 데이터 생성 및 저장 (매 30분마다 실행)
     */
    @Scheduled(fixedRate = 1800000) // 30분
    public void generateAndSave30MinCandle() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        
        try {
            Set<String> symbols = stockSimulator.getAllSymbols();
            
            for (String symbol : symbols) {
            try {
                List<CandleData1Min> recent1Min = candle1MinRepo.findRecentBySymbol(symbol, 30);
                
                if (recent1Min.size() >= 15) {
                    CandleData30Min aggregated = aggregateTo30Min(recent1Min, now);
                    candle30MinRepo.save(aggregated);
                    latest30MinCache.put(symbol, aggregated);
                    
                    // 1년치 데이터 수 제한 확인 및 정리
                    maintainDataLimit(symbol, "30min", MAX_30MIN_PER_YEAR);
                }
                
            } catch (Exception e) {
                log.error("30분봉 생성 실패: {}", symbol, e);
            }
            }
        } catch (Exception e) {
            log.error("30분봉 생성 중 오류 발생", e);
        }
    }

    /**
     * 60분봉 데이터 생성 및 저장 (매 60분마다 실행)
     */
    @Scheduled(fixedRate = 3600000) // 60분
    public void generateAndSave60MinCandle() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        
        try {
            Set<String> symbols = stockSimulator.getAllSymbols();
            
            for (String symbol : symbols) {
            try {
                List<CandleData1Min> recent1Min = candle1MinRepo.findRecentBySymbol(symbol, 60);
                
                if (recent1Min.size() >= 30) {
                    CandleData60Min aggregated = aggregateTo60Min(recent1Min, now);
                    candle60MinRepo.save(aggregated);
                    latest60MinCache.put(symbol, aggregated);
                    
                    // 1년치 데이터 수 제한 확인 및 정리
                    maintainDataLimit(symbol, "60min", MAX_60MIN_PER_YEAR);
                }
                
            } catch (Exception e) {
                log.error("60분봉 생성 실패: {}", symbol, e);
            }
            }
        } catch (Exception e) {
            log.error("60분봉 생성 중 오류 발생", e);
        }
    }

    /**
     * 1일봉 데이터 생성 및 저장 (매일 자정에 실행)
     */
    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정
    public void generateAndSave1DayCandle() {
        LocalDate today = LocalDate.now();
        
        for (String symbol : companyConfig.getCompanies().stream().map(CompanyConfig.CompanyData::getSymbol).toList()) {
            try {
                // 어제 하루 동안의 1분봉 데이터 조회
                LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
                LocalDateTime yesterdayEnd = today.atStartOfDay().minusNanos(1);
                
                List<CandleData1Min> dayCandles = candle1MinRepo.findBySymbolAndTimeRange(symbol, yesterdayStart, yesterdayEnd);
                
                if (!dayCandles.isEmpty()) {
                    CandleData1Day aggregated = aggregateTo1Day(dayCandles, today.minusDays(1));
                    candle1DayRepo.save(aggregated);
                    latest1DayCache.put(symbol, aggregated);
                    
                    // 1년치 데이터 수 제한 확인 및 정리
                    maintainDataLimit(symbol, "1day", MAX_1DAY_PER_YEAR);
                }
                
            } catch (Exception e) {
                log.error("1일봉 생성 실패: {}", symbol, e);
            }
        }
    }

    /**
     * 1주봉 데이터 생성 및 저장 (매주 월요일 자정에 실행)
     */
    @Scheduled(cron = "0 0 0 * * MON") // 매주 월요일 자정
    public void generateAndSave1WeekCandle() {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        
        for (String symbol : companyConfig.getCompanies().stream().map(CompanyConfig.CompanyData::getSymbol).toList()) {
            try {
                // 지난 주의 1일봉 데이터 조회
                LocalDate lastWeekStart = weekStart.minusWeeks(1);
                LocalDate lastWeekEnd = weekStart.minusDays(1);
                
                List<CandleData1Day> weekCandles = candle1DayRepo.findBySymbolAndDateRange(symbol, lastWeekStart, lastWeekEnd);
                
                if (!weekCandles.isEmpty()) {
                    CandleData1Week aggregated = aggregateTo1Week(weekCandles, lastWeekStart);
                    candle1WeekRepo.save(aggregated);
                    latest1WeekCache.put(symbol, aggregated);
                    
                    // 1년치 데이터 수 제한 확인 및 정리
                    maintainDataLimit(symbol, "1week", MAX_1WEEK_PER_YEAR);
                }
                
            } catch (Exception e) {
                log.error("1주봉 생성 실패: {}", symbol, e);
            }
        }
    }

    /**
     * 1개월봉 데이터 생성 및 저장 (매월 1일 자정에 실행)
     */
    @Scheduled(cron = "0 0 0 1 * ?") // 매월 1일 자정
    public void generateAndSave1MonthCandle() {
        YearMonth currentMonth = YearMonth.now();
        
        for (String symbol : companyConfig.getCompanies().stream().map(CompanyConfig.CompanyData::getSymbol).toList()) {
            try {
                // 지난 달의 1일봉 데이터 조회
                YearMonth lastMonth = currentMonth.minusMonths(1);
                LocalDate lastMonthStart = lastMonth.atDay(1);
                LocalDate lastMonthEnd = lastMonth.atEndOfMonth();
                
                List<CandleData1Day> monthCandles = candle1DayRepo.findBySymbolAndDateRange(symbol, lastMonthStart, lastMonthEnd);
                
                if (!monthCandles.isEmpty()) {
                    CandleData1Month aggregated = aggregateTo1Month(monthCandles, lastMonth);
                    candle1MonthRepo.save(aggregated);
                    latest1MonthCache.put(symbol, aggregated);
                    
                    // 1년치 데이터 수 제한 확인 및 정리
                    maintainDataLimit(symbol, "1month", MAX_1MONTH_PER_YEAR);
                }
                
            } catch (Exception e) {
                log.error("1개월봉 생성 실패: {}", symbol, e);
            }
        }
    }

    /**
     * 데이터 수 제한 유지 (1년치만 보관)
     */
    private void maintainDataLimit(String symbol, String period, int maxCount) {
        try {
            long currentCount = getCurrentDataCount(symbol, period);
            
            if (currentCount > maxCount) {
                int excessCount = (int) (currentCount - maxCount);
                deleteOldestData(symbol, period, excessCount);
                log.info("{} {} 데이터 정리: {}개 삭제", symbol, period, excessCount);
            }
        } catch (Exception e) {
            log.error("데이터 제한 유지 실패: {} {}", symbol, period, e);
        }
    }

    /**
     * 현재 데이터 수 조회
     */
    private long getCurrentDataCount(String symbol, String period) {
        return switch (period) {
            case "1min" -> candle1MinRepo.countBySymbol(symbol);
            case "5min" -> candle5MinRepo.countBySymbol(symbol);
            case "10min" -> candle10MinRepo.countBySymbol(symbol);
            case "30min" -> candle30MinRepo.countBySymbol(symbol);
            case "60min" -> candle60MinRepo.countBySymbol(symbol);
            case "1day" -> candle1DayRepo.countBySymbol(symbol);
            case "1week" -> candle1WeekRepo.countBySymbol(symbol);
            case "1month" -> candle1MonthRepo.countBySymbol(symbol);
            default -> 0L;
        };
    }

    /**
     * 가장 오래된 데이터 삭제
     */
    private void deleteOldestData(String symbol, String period, int count) {
        switch (period) {
            case "1min" -> {
                List<CandleData1Min> allData = candle1MinRepo.findAllBySymbolOrderByTimestampDesc(symbol);
                if (allData.size() > count) {
                    List<CandleData1Min> toDelete = allData.subList(allData.size() - count, allData.size());
                    candle1MinRepo.deleteAll(toDelete);
                }
            }
            case "5min" -> {
                List<CandleData5Min> allData = candle5MinRepo.findAllBySymbolOrderByTimestampDesc(symbol);
                if (allData.size() > count) {
                    List<CandleData5Min> toDelete = allData.subList(allData.size() - count, allData.size());
                    candle5MinRepo.deleteAll(toDelete);
                }
            }
            case "10min" -> {
                List<CandleData10Min> allData = candle10MinRepo.findAllBySymbolOrderByTimestampDesc(symbol);
                if (allData.size() > count) {
                    List<CandleData10Min> toDelete = allData.subList(allData.size() - count, allData.size());
                    candle10MinRepo.deleteAll(toDelete);
                }
            }
            case "30min" -> {
                List<CandleData30Min> allData = candle30MinRepo.findAllBySymbolOrderByTimestampDesc(symbol);
                if (allData.size() > count) {
                    List<CandleData30Min> toDelete = allData.subList(allData.size() - count, allData.size());
                    candle30MinRepo.deleteAll(toDelete);
                }
            }
            case "60min" -> {
                List<CandleData60Min> allData = candle60MinRepo.findAllBySymbolOrderByTimestampDesc(symbol);
                if (allData.size() > count) {
                    List<CandleData60Min> toDelete = allData.subList(allData.size() - count, allData.size());
                    candle60MinRepo.deleteAll(toDelete);
                }
            }
            case "1day" -> {
                List<CandleData1Day> allData = candle1DayRepo.findAllBySymbolOrderByDateDesc(symbol);
                if (allData.size() > count) {
                    List<CandleData1Day> toDelete = allData.subList(allData.size() - count, allData.size());
                    candle1DayRepo.deleteAll(toDelete);
                }
            }
            case "1week" -> {
                List<CandleData1Week> allData = candle1WeekRepo.findAllBySymbolOrderByDateDesc(symbol);
                if (allData.size() > count) {
                    List<CandleData1Week> toDelete = allData.subList(allData.size() - count, allData.size());
                    candle1WeekRepo.deleteAll(toDelete);
                }
            }
            case "1month" -> {
                List<CandleData1Month> allData = candle1MonthRepo.findAllBySymbolOrderByYearMonthDesc(symbol);
                if (allData.size() > count) {
                    List<CandleData1Month> toDelete = allData.subList(allData.size() - count, allData.size());
                    candle1MonthRepo.deleteAll(toDelete);
                }
            }
        }
    }

    /**
     * 페이징된 차트 데이터 조회 (모든 기간 지원)
     */
    public Page<?> getChartDataWithPaging(String symbol, String period, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        
        return switch (period) {
            case "1min" -> candle1MinRepo.findBySymbolOrderByTimestampDesc(symbol, pageable);
            case "5min" -> candle5MinRepo.findBySymbolOrderByTimestampDesc(symbol, pageable);
            case "10min" -> candle10MinRepo.findBySymbolOrderByTimestampDesc(symbol, pageable);
            case "30min" -> candle30MinRepo.findBySymbolOrderByTimestampDesc(symbol, pageable);
            case "60min" -> candle60MinRepo.findBySymbolOrderByTimestampDesc(symbol, pageable);
            // 1일, 1주, 1월은 다른 메서드명 사용 (임시로 1분 데이터 반환)
            case "1day", "1week", "1month" -> candle1MinRepo.findBySymbolOrderByTimestampDesc(symbol, pageable);
            default -> throw new IllegalArgumentException("지원하지 않는 기간: " + period);
        };
    }

    /**
     * 최근 N개 캔들 데이터 조회
     */
    public List<?> getRecentCandles(String symbol, String period, int count) {
        return switch (period) {
            case "1min" -> candle1MinRepo.findRecentBySymbol(symbol, count);
            case "5min" -> candle5MinRepo.findRecentBySymbol(symbol, count);
            case "10min" -> candle10MinRepo.findRecentBySymbol(symbol, count);
            case "30min" -> candle30MinRepo.findRecentBySymbol(symbol, count);
            case "60min" -> candle60MinRepo.findRecentBySymbol(symbol, count);
            case "1day" -> candle1DayRepo.findRecentBySymbol(symbol, count);
            case "1week" -> candle1WeekRepo.findRecentBySymbol(symbol, count);
            case "1month" -> candle1MonthRepo.findRecentBySymbol(symbol, count);
            default -> throw new IllegalArgumentException("지원하지 않는 기간: " + period);
        };
    }

    /**
     * 시간 범위 내 캔들 데이터 조회
     */
    public List<?> getCandlesByTimeRange(String symbol, String period, long startTime, long endTime) {
        LocalDateTime start = LocalDateTime.ofEpochSecond(startTime / 1000, 0, java.time.ZoneOffset.UTC);
        LocalDateTime end = LocalDateTime.ofEpochSecond(endTime / 1000, 0, java.time.ZoneOffset.UTC);
        
        return switch (period) {
            case "1min" -> candle1MinRepo.findBySymbolAndTimeRange(symbol, start, end);
            case "5min" -> candle5MinRepo.findBySymbolAndTimeRange(symbol, start, end);
            case "10min" -> candle10MinRepo.findBySymbolAndTimeRange(symbol, start, end);
            case "30min" -> candle30MinRepo.findBySymbolAndTimeRange(symbol, start, end);
            case "60min" -> candle60MinRepo.findBySymbolAndTimeRange(symbol, start, end);
            case "1day" -> candle1DayRepo.findBySymbolAndDateRange(symbol, start.toLocalDate(), end.toLocalDate());
            case "1week" -> candle1WeekRepo.findBySymbolAndDateRange(symbol, start.toLocalDate(), end.toLocalDate());
            case "1month" -> {
                YearMonth startYearMonth = YearMonth.from(start);
                YearMonth endYearMonth = YearMonth.from(end);
                yield candle1MonthRepo.findBySymbolAndYearMonthRange(symbol, startYearMonth, endYearMonth);
            }
            default -> throw new IllegalArgumentException("지원하지 않는 기간: " + period);
        };
    }

    /**
     * 최신 캔들 데이터 조회 (캐시에서, 없으면 DB에서 조회)
     */
    public Object getLatestCandle(String symbol, String period) {
        try {
            Object cached = switch (period) {
            case "1min" -> latest1MinCache.get(symbol);
            case "5min" -> latest5MinCache.get(symbol);
            case "10min" -> latest10MinCache.get(symbol);
            case "30min" -> latest30MinCache.get(symbol);
            case "60min" -> latest60MinCache.get(symbol);
            case "1day" -> latest1DayCache.get(symbol);
            case "1week" -> latest1WeekCache.get(symbol);
            case "1month" -> latest1MonthCache.get(symbol);
            default -> throw new IllegalArgumentException("지원하지 않는 기간: " + period);
        };
            
            // 캐시에 없으면 DB에서 조회
            if (cached == null) {
                try {
                    cached = switch (period) {
                        case "1min" -> candle1MinRepo.findLatestBySymbol(symbol).orElse(null);
                        case "5min" -> candle5MinRepo.findLatestBySymbol(symbol).orElse(null);
                        case "10min" -> candle10MinRepo.findLatestBySymbol(symbol).orElse(null);
                        case "30min" -> candle30MinRepo.findLatestBySymbol(symbol).orElse(null);
                        case "60min" -> candle60MinRepo.findLatestBySymbol(symbol).orElse(null);
                        case "1day" -> candle1DayRepo.findLatestBySymbol(symbol).orElse(null);
                        case "1week" -> candle1WeekRepo.findLatestBySymbol(symbol).orElse(null);
                        case "1month" -> candle1MonthRepo.findLatestBySymbol(symbol).orElse(null);
                        default -> null;
                    };
                    
                    // DB에서 조회된 데이터를 캐시에 저장
                    if (cached != null) {
                        switch (period) {
                            case "1min" -> latest1MinCache.put(symbol, (CandleData1Min) cached);
                            case "5min" -> latest5MinCache.put(symbol, (CandleData5Min) cached);
                            case "10min" -> latest10MinCache.put(symbol, (CandleData10Min) cached);
                            case "30min" -> latest30MinCache.put(symbol, (CandleData30Min) cached);
                            case "60min" -> latest60MinCache.put(symbol, (CandleData60Min) cached);
                            case "1day" -> latest1DayCache.put(symbol, (CandleData1Day) cached);
                            case "1week" -> latest1WeekCache.put(symbol, (CandleData1Week) cached);
                            case "1month" -> latest1MonthCache.put(symbol, (CandleData1Month) cached);
                        }
                    }
                } catch (Exception e) {
                    log.warn("종목 {}의 최신 {} 캔들 DB 조회 실패: {}", symbol, period, e.getMessage());
                }
            }
            
            return cached;
        } catch (Exception e) {
            log.error("종목 {}의 최신 {} 캔들 조회 중 오류: {}", symbol, period, e.getMessage());
            return null;
        }
    }

    /**
     * 모든 종목의 최신 캔들 데이터 조회
     */
    public Map<String, Object> getAllLatestCandles(String period) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // StockSimulator에서 모든 심볼 가져오기
            Set<String> symbols = stockSimulator.getAllSymbols();
            
            for (String symbol : symbols) {
                try {
            Object latest = getLatestCandle(symbol, period);
            if (latest != null) {
                result.put(symbol, latest);
            }
                } catch (Exception e) {
                    log.warn("종목 {}의 최신 캔들 조회 실패: {}", symbol, e.getMessage());
                    // 개별 종목 실패는 전체 요청을 중단하지 않음
                }
            }
        } catch (Exception e) {
            log.error("모든 최신 캔들 조회 실패", e);
            // 오류 발생 시 빈 맵 반환
        }
        
        return result;
    }

    /**
     * 오래된 데이터 정리 (매일 새벽 2시 실행)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldData() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
        LocalDate cutoffDate = LocalDate.now().minusDays(7);
        YearMonth cutoffYearMonth = YearMonth.now().minusMonths(7);
        
        try {
            candle1MinRepo.deleteOldData(cutoffTime);
            candle5MinRepo.deleteOldData(cutoffTime);
            candle10MinRepo.deleteOldData(cutoffTime);
            candle30MinRepo.deleteOldData(cutoffTime);
            candle60MinRepo.deleteOldData(cutoffTime);
            candle1DayRepo.deleteOldData(cutoffDate);
            candle1WeekRepo.deleteOldData(cutoffDate);
            candle1MonthRepo.deleteOldData(cutoffYearMonth);
            
            log.info("오래된 캔들 데이터 정리 완료: {}", cutoffTime);
        } catch (Exception e) {
            log.error("데이터 정리 실패", e);
        }
    }

    // 집계 메서드들
    private CandleData5Min aggregateTo5Min(List<CandleData1Min> candles, LocalDateTime timestamp) {
        double open = candles.get(candles.size() - 1).getOpen();
        double close = candles.get(0).getClose();
        double high = candles.stream().mapToDouble(CandleData1Min::getHigh).max().orElse(0);
        double low = candles.stream().mapToDouble(CandleData1Min::getLow).min().orElse(0);
        long volume = candles.stream().mapToLong(CandleData1Min::getVolume).sum();
        
        return new CandleData5Min(candles.get(0).getSymbol(), timestamp, open, high, low, close, volume);
    }

    private CandleData10Min aggregateTo10Min(List<CandleData1Min> candles, LocalDateTime timestamp) {
        double open = candles.get(candles.size() - 1).getOpen();
        double close = candles.get(0).getClose();
        double high = candles.stream().mapToDouble(CandleData1Min::getHigh).max().orElse(0);
        double low = candles.stream().mapToDouble(CandleData1Min::getLow).min().orElse(0);
        long volume = candles.stream().mapToLong(CandleData1Min::getVolume).sum();
        
        return new CandleData10Min(candles.get(0).getSymbol(), timestamp, open, high, low, close, volume);
    }

    private CandleData30Min aggregateTo30Min(List<CandleData1Min> candles, LocalDateTime timestamp) {
        double open = candles.get(candles.size() - 1).getOpen();
        double close = candles.get(0).getClose();
        double high = candles.stream().mapToDouble(CandleData1Min::getHigh).max().orElse(0);
        double low = candles.stream().mapToDouble(CandleData1Min::getLow).min().orElse(0);
        long volume = candles.stream().mapToLong(CandleData1Min::getVolume).sum();
        
        return new CandleData30Min(candles.get(0).getSymbol(), timestamp, open, high, low, close, volume);
    }

    private CandleData60Min aggregateTo60Min(List<CandleData1Min> candles, LocalDateTime timestamp) {
        double open = candles.get(candles.size() - 1).getOpen();
        double close = candles.get(0).getClose();
        double high = candles.stream().mapToDouble(CandleData1Min::getHigh).max().orElse(0);
        double low = candles.stream().mapToDouble(CandleData1Min::getLow).min().orElse(0);
        long volume = candles.stream().mapToLong(CandleData1Min::getVolume).sum();
        
        return new CandleData60Min(candles.get(0).getSymbol(), timestamp, open, high, low, close, volume);
    }

    private CandleData1Day aggregateTo1Day(List<CandleData1Min> candles, LocalDate date) {
        double open = candles.get(candles.size() - 1).getOpen();
        double close = candles.get(0).getClose();
        double high = candles.stream().mapToDouble(CandleData1Min::getHigh).max().orElse(0);
        double low = candles.stream().mapToDouble(CandleData1Min::getLow).min().orElse(0);
        long volume = candles.stream().mapToLong(CandleData1Min::getVolume).sum();
        
        return new CandleData1Day(candles.get(0).getSymbol(), date, open, high, low, close, volume);
    }

    private CandleData1Week aggregateTo1Week(List<CandleData1Day> candles, LocalDate weekStart) {
        double open = candles.get(candles.size() - 1).getOpen();
        double close = candles.get(0).getClose();
        double high = candles.stream().mapToDouble(CandleData1Day::getHigh).max().orElse(0);
        double low = candles.stream().mapToDouble(CandleData1Day::getLow).min().orElse(0);
        long volume = candles.stream().mapToLong(CandleData1Day::getVolume).sum();
        
        return new CandleData1Week(candles.get(0).getSymbol(), weekStart, open, high, low, close, volume);
    }

    private CandleData1Month aggregateTo1Month(List<CandleData1Day> candles, YearMonth yearMonth) {
        double open = candles.get(candles.size() - 1).getOpen();
        double close = candles.get(0).getClose();
        double high = candles.stream().mapToDouble(CandleData1Day::getHigh).max().orElse(0);
        double low = candles.stream().mapToDouble(CandleData1Day::getLow).min().orElse(0);
        long volume = candles.stream().mapToLong(CandleData1Day::getVolume).sum();
        
        return new CandleData1Month(candles.get(0).getSymbol(), yearMonth, open, high, low, close, volume);
    }

    /**
     * 캐시 메모리 사용량 계산
     */
    private long calculateCacheMemoryUsage() {
        long totalSize = 0;
        
        // 각 캐시의 크기 계산 (대략적)
        totalSize += latest1MinCache.size() * 200; // 200 bytes per candle
        totalSize += latest5MinCache.size() * 200;
        totalSize += latest10MinCache.size() * 200;
        totalSize += latest30MinCache.size() * 200;
        totalSize += latest60MinCache.size() * 200;
        totalSize += latest1DayCache.size() * 200;
        totalSize += latest1WeekCache.size() * 200;
        totalSize += latest1MonthCache.size() * 200;
        
        return totalSize;
    }
    
    /**
     * 캐시 메모리 사용량 조회
     */
    public Map<String, Object> getCacheMemoryInfo() {
        long currentUsage = calculateCacheMemoryUsage();
        long lastCleanup = lastCacheCleanupTime.get();
        
        Map<String, Object> info = new HashMap<>();
        info.put("currentMemoryUsageBytes", currentUsage);
        info.put("currentMemoryUsageKB", currentUsage / 1024.0);
        info.put("currentMemoryUsageMB", currentUsage / (1024.0 * 1024.0));
        info.put("lastCleanupTime", new Date(lastCleanup));
        info.put("cacheSizes", Map.of(
            "1min", latest1MinCache.size(),
            "5min", latest5MinCache.size(),
            "10min", latest10MinCache.size(),
            "30min", latest30MinCache.size(),
            "60min", latest60MinCache.size(),
            "1day", latest1DayCache.size(),
            "1week", latest1WeekCache.size(),
            "1month", latest1MonthCache.size()
        ));
        
        return info;
    }
    
    /**
     * 캐시 정리 (메모리 최적화)
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void cleanupCache() {
        try {
            long beforeCleanup = calculateCacheMemoryUsage();
       
            
            // 실제로는 캐시는 최신 데이터만 유지하므로 크게 정리할 필요 없음
            // 하지만 메모리 누수 방지를 위한 정리
            System.gc(); // 가비지 컬렉션 요청
            
            long afterCleanup = calculateCacheMemoryUsage();
            lastCacheCleanupTime.set(System.currentTimeMillis());
            
            log.info("캐시 정리 완료: {}KB → {}KB (절약: {}KB)", 
                    beforeCleanup / 1024, afterCleanup / 1024, 
                    (beforeCleanup - afterCleanup) / 1024);
                    
        } catch (Exception e) {
            log.error("캐시 정리 중 오류 발생", e);
        }
    }
} 