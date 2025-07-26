package com.nbillion.service;

import com.nbillion.exception.InvalidChartPeriodException;
import com.nbillion.exception.StockNotFoundException;
import com.nbillion.model.CandleData1Min;
import com.nbillion.model.Company;
import com.nbillion.model.CandleData1Day;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 주식 관련 데이터 처리 서비스
 */
@Service
public class StockService {

    private final StockSimulator simulator;
    private final CandleService candleService;
    
    public StockService(StockSimulator simulator, CandleService candleService) {
        this.simulator = simulator;
        this.candleService = candleService;
    }

    /**
     * 회사 기본 정보 조회
     */
    public Company getCompanyInfo(String symbol) {
        Company company = simulator.getCompany(symbol);
        if (company == null) {
            throw new StockNotFoundException(symbol);
        }
        return company;
    }

    /**
     * 추천 종목 리스트 조회 (홈 화면용)
     */
    public List<Company> getRecommendedStocks() {
        return simulator.getRecommendedStocks();
    }

    /**
     * 실시간 인기주 리스트 조회 (상승률 높은 5개)
     */
    public List<Company> getTrendingStocks() {
        return simulator.getTrendingStocks();
    }

    /**
     * 페이징된 차트 데이터 조회 (성능 최적화)
     */
    public Page<?> getChartDataWithPaging(String symbol, String period, int page, int size) {
        // 유효한 기간 검증
        if (!isValidPeriod(period)) {
            throw new InvalidChartPeriodException(period);
        }
        
        return candleService.getChartDataWithPaging(symbol, period, page, size);
    }

    /**
     * 최근 N개 캔들 데이터 조회 (실시간 차트용)
     */
    public List<?> getRecentChartData(String symbol, String period, int count) {
        if (!isValidPeriod(period)) {
            throw new InvalidChartPeriodException(period);
        }
        
        return candleService.getRecentCandles(symbol, period, count);
    }

    /**
     * 특정 시간 범위의 차트 데이터 조회
     */
    public List<?> getChartDataByTimeRange(String symbol, String period, long startTime, long endTime) {
        if (!isValidPeriod(period)) {
            throw new InvalidChartPeriodException(period);
        }
        
        return candleService.getCandlesByTimeRange(symbol, period, startTime, endTime);
    }

    /**
     * 기간 유효성 검증
     */
    private boolean isValidPeriod(String period) {
        return period != null && (
            "1min".equals(period) || 
            "5min".equals(period) || 
            "10min".equals(period) || 
            "30min".equals(period) || 
            "60min".equals(period) ||
            "1day".equals(period) ||
            "1week".equals(period) ||
            "1month".equals(period)
        );
    }

    /**
     * 모든 종목 심볼 조회
     */
    public List<String> getAllSymbols() {
        return new ArrayList<>(simulator.getAllSymbols());
    }

    /**
     * 현재 가격 조회
     */
    public double getCurrentPrice(String symbol) {
        return simulator.getCurrentPrice(symbol);
    }

    /**
     * 최근 캔들 데이터 조회 (캐시에서 빠른 조회)
     */
    public Object getLatestCandle(String symbol) {
        return candleService.getLatestCandle(symbol, "1min");
    }

    /**
     * 모든 종목의 최근 캔들 데이터 조회
     */
    public Map<String, Object> getAllLatestCandles() {
        return candleService.getAllLatestCandles("1min");
    }

    /**
     * 차트 데이터 통계 정보 조회
     */
    public ChartDataStats getChartDataStats(String symbol, String period) {
        if (!isValidPeriod(period)) {
            throw new InvalidChartPeriodException(period);
        }
        
        // 최근 100개 데이터로 통계 계산
        Page<?> page = candleService.getChartDataWithPaging(symbol, period, 0, 100);
        List<?> candles = page.getContent();
        
        if (candles.isEmpty()) {
            return new ChartDataStats(0, 0, 0, 0, 0);
        }
        
        // 새로운 캔들 데이터 타입들을 처리하기 위해 Object로 처리
        double totalVolume = 0;
        double maxPrice = Double.MIN_VALUE;
        double minPrice = Double.MAX_VALUE;
        double totalChange = 0;
        
        for (Object candleObj : candles) {
            // 각 캔들 타입별로 처리
            if (candleObj instanceof CandleData1Min candle) {
                totalVolume += candle.getVolume();
                maxPrice = Math.max(maxPrice, candle.getHigh());
                minPrice = Math.min(minPrice, candle.getLow());
                totalChange += (candle.getClose() - candle.getOpen());
            } else if (candleObj instanceof CandleData1Day candle) {
                totalVolume += candle.getVolume();
                maxPrice = Math.max(maxPrice, candle.getHigh());
                minPrice = Math.min(minPrice, candle.getLow());
                totalChange += (candle.getClose() - candle.getOpen());
            }
            // 다른 타입들도 필요시 추가
        }
        
        double avgVolume = totalVolume / candles.size();
        double avgChange = totalChange / candles.size();
        
        return new ChartDataStats(candles.size(), avgVolume, maxPrice, minPrice, avgChange);
    }

    /**
     * 차트 데이터 통계 클래스
     */
    public static class ChartDataStats {
        private final int dataCount;
        private final double averageVolume;
        private final double maxPrice;
        private final double minPrice;
        private final double averageChange;

        public ChartDataStats(int dataCount, double averageVolume, double maxPrice, double minPrice, double averageChange) {
            this.dataCount = dataCount;
            this.averageVolume = averageVolume;
            this.maxPrice = maxPrice;
            this.minPrice = minPrice;
            this.averageChange = averageChange;
        }

        // Getters
        public int getDataCount() { return dataCount; }
        public double getAverageVolume() { return averageVolume; }
        public double getMaxPrice() { return maxPrice; }
        public double getMinPrice() { return minPrice; }
        public double getAverageChange() { return averageChange; }
    }

    public List<Company> getAllStocks(int page, int size) {
        return simulator.getAllStocks(page, size);
    }

    public int getTotalStockCount() {
        return simulator.getTotalStockCount();
    }

    /**
     * 파이썬과 동일한 API: 샘플링된 데이터 조회
     */
    public List<List<Object>> getSampledData(String symbol, String period) {
        return simulator.getSampledData(symbol, period);
    }
    
    /**
     * 파이썬과 동일한 API: 현재 가격들 조회
     */
    public Map<String, Double> getCurrentPrices() {
        return simulator.getCurrentPrices();
    }
    
    /**
     * 파이썬과 동일한 API: 모든 종목 심볼 조회
     */
    public List<String> getAllStockSymbols() {
        return simulator.getAllStockSymbols();
    }
    
    /**
     * 파이썬과 동일한 API: 간단한 캔들 데이터 조회
     */
    public List<Map<String, Object>> getSimpleCandles(String symbol, String period) {
        return simulator.getSimpleCandles(symbol, period);
    }
    
    /**
     * 캔들 형태의 데이터 조회 (Flutter 클라이언트용)
     */
    public List<Map<String, Object>> getCandleData(String symbol, String period) {
        return simulator.getCandleData(symbol, period);
    }
    
    /**
     * 고급 캔들 데이터 조회 (실제 OHLCV 계산)
     */
    public List<Map<String, Object>> getAdvancedCandleData(String symbol, String period) {
        return simulator.getAdvancedCandleData(symbol, period);
    }

}