package com.nbillion.controller;

import lombok.RequiredArgsConstructor;
import com.nbillion.model.Company;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.nbillion.service.StockService;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 주식 데이터 관련 컨트롤러
 *
 * - 회사 기본 정보
 * - 추천 종목 리스트
 * - 페이징된 차트 데이터
 * - 모든 종목 조회
 * - 차트 데이터 통계
 */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // CORS 허용
public class StockController {

    private final StockService stockService;

    /**
     * API 루트 엔드포인트 - 서버 상태 및 API 정보
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getApiInfo() {
        Map<String, Object> apiInfo = Map.of(
            "service", "Stock Simulation Server",
            "version", "1.0.0",
            "status", "running",
            "endpoints", Map.of(
                "recommended", "/api/stocks/recommended",
                "trending", "/api/stocks/trending/with-subscription",
                "all", "/api/stocks/stocks/all",
                "chart", "/api/stocks/{symbol}/chart",
                "company", "/api/stocks/{symbol}"
            )
        );
        return ResponseEntity.ok(apiInfo);
    }

    /**
     * 회사 기본 정보 조회
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<Company> getCompanyInfo(@PathVariable String symbol) {
        Company company = stockService.getCompanyInfo(symbol);
        return ResponseEntity.ok(company);
    }

    /**
     * 추천 주식 리스트 조회 (홈 화면용)
     */
    @GetMapping("/recommended")
    public ResponseEntity<List<Company>> getRecommendedStocks() {
        List<Company> recommendedStocks = stockService.getRecommendedStocks();
        return ResponseEntity.ok(recommendedStocks);
    }

    /**
     * 추천 주식 리스트 조회 + WebSocket 구독 정보 포함
     */
    @GetMapping("/recommended/with-subscription")
    public ResponseEntity<Map<String, Object>> getRecommendedStocksWithSubscription() {
        List<Company> recommendedStocks = stockService.getRecommendedStocks();
        
        Map<String, Object> response = Map.of(
            "stocks", recommendedStocks,
            "websocket_subscription", Map.of(
                "url", "ws://localhost:8080/ws/stocks",
                "message", Map.of(
                    "type", "subscribe_recommended"
                ),
                "description", "이 메시지를 WebSocket으로 전송하면 추천주 실시간 데이터를 받을 수 있습니다."
            )
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * 실시간 인기주 리스트 조회 + WebSocket 구독 정보 포함
     */
    @GetMapping("/trending/with-subscription")
    public ResponseEntity<Map<String, Object>> getTrendingStocksWithSubscription() {
        List<Company> trendingStocks = stockService.getTrendingStocks();
        
        Map<String, Object> response = Map.of(
            "stocks", trendingStocks,
            "websocket_subscription", Map.of(
                "url", "ws://localhost:8080/ws/stocks",
                "message", Map.of(
                    "type", "subscribe_trending"
                ),
                "description", "이 메시지를 WebSocket으로 전송하면 실시간 인기주 데이터를 받을 수 있습니다."
            )
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * 모든 종목 심볼 조회
     */
    @GetMapping("/symbols")
    public ResponseEntity<List<String>> getAllSymbols() {
        List<String> symbols = stockService.getAllSymbols();
        return ResponseEntity.ok(symbols);
    }

    /**
     * 현재 가격 조회
     */
    @GetMapping("/{symbol}/price")
    public ResponseEntity<Double> getCurrentPrice(@PathVariable String symbol) {
        double price = stockService.getCurrentPrice(symbol);
        if (price == 0.0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(price);
    }

    /**
     * 차트 데이터 조회 (요구사항 형식)
     *
     * @param symbol 종목 코드
     * @param period 기간 (1min, 5min, 10min, 30min, 60min)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기 (기본값: 100)
     * @return 차트 데이터 (symbol, period, data)
     */
    @GetMapping("/{symbol}/chart")
    public ResponseEntity<Map<String, Object>> getChartData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1min") String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        
        try {
            Page<?> chartDataPage = stockService.getChartDataWithPaging(symbol, period, page, size);
            
            // 요구사항 형식: symbol, period, data만 포함
            Map<String, Object> response = Map.of(
                "symbol", symbol,
                "period", period,
                "data", chartDataPage.getContent()
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 페이징된 차트 데이터 조회 (성능 최적화)
     *
     * @param symbol 종목 코드
     * @param period 기간 (1min, 5min, 10min, 30min, 60min)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기 (기본값: 100)
     * @return 페이징된 캔들스틱 데이터
     */
    @GetMapping("/{symbol}/chart/paging")
    public ResponseEntity<Map<String, Object>> getChartDataWithPaging(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1min") String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        
        try {
            Page<?> chartDataPage = stockService.getChartDataWithPaging(symbol, period, page, size);
            
            Map<String, Object> response = Map.of(
                "symbol", symbol,
                "period", period,
                "data", chartDataPage.getContent(),
                "pagination", Map.of(
                    "currentPage", chartDataPage.getNumber(),
                    "totalPages", chartDataPage.getTotalPages(),
                    "totalElements", chartDataPage.getTotalElements(),
                    "size", chartDataPage.getSize(),
                    "hasNext", chartDataPage.hasNext(),
                    "hasPrevious", chartDataPage.hasPrevious()
                )
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 최근 N개 차트 데이터 조회 (실시간 차트용)
     *
     * @param symbol 종목 코드
     * @param period 기간
     * @param count 조회할 데이터 개수 (기본값: 50)
     * @return 최근 캔들스틱 데이터
     */
    @GetMapping("/{symbol}/chart/recent")
    public ResponseEntity<Map<String, Object>> getRecentChartData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1min") String period,
            @RequestParam(defaultValue = "50") int count) {
        
        try {
            List<?> chartData = stockService.getRecentChartData(symbol, period, count);
            
            Map<String, Object> response = Map.of(
                "symbol", symbol,
                "period", period,
                "data", chartData,
                "count", chartData.size()
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 특정 시간 범위의 차트 데이터 조회
     *
     * @param symbol 종목 코드
     * @param period 기간
     * @param startTime 시작 시간 (밀리초)
     * @param endTime 종료 시간 (밀리초)
     * @return 시간 범위 내 캔들스틱 데이터
     */
    @GetMapping("/{symbol}/chart/range")
    public ResponseEntity<Map<String, Object>> getChartDataByTimeRange(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1min") String period,
            @RequestParam long startTime,
            @RequestParam long endTime) {
        
        try {
            List<?> chartData = stockService.getChartDataByTimeRange(symbol, period, startTime, endTime);
            
            Map<String, Object> response = Map.of(
                "symbol", symbol,
                "period", period,
                "startTime", startTime,
                "endTime", endTime,
                "data", chartData,
                "count", chartData.size()
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 최근 캔들 데이터 조회 (캐시에서 빠른 조회)
     */
    @GetMapping("/{symbol}/candle/latest")
    public ResponseEntity<Map<String, Object>> getLatestCandle(@PathVariable String symbol) {
        try {
        Object candle = stockService.getLatestCandle(symbol);
            
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("timestamp", System.currentTimeMillis());
            
        if (candle == null) {
                response.put("status", "no_data");
                response.put("message", "아직 캔들 데이터가 생성되지 않았습니다.");
                response.put("data", null);
            } else {
                response.put("status", "success");
                response.put("data", candle);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("symbol", symbol);
            errorResponse.put("status", "error");
            errorResponse.put("error", "캔들 데이터 조회 중 오류가 발생했습니다: " + e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            errorResponse.put("data", null);
            
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * 모든 종목의 최근 캔들 데이터 조회
     */
    @GetMapping("/candles/latest")
    public ResponseEntity<Map<String, Object>> getAllLatestCandles() {
        try {
        Map<String, Object> candles = stockService.getAllLatestCandles();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("candles", candles);
            response.put("count", candles.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("error", "최신 캔들 데이터 조회 중 오류가 발생했습니다: " + e.getMessage());
            errorResponse.put("candles", new HashMap<>());
            errorResponse.put("count", 0);
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * 차트 데이터 통계 정보 조회
     */
    @GetMapping("/{symbol}/chart/stats")
    public ResponseEntity<StockService.ChartDataStats> getChartDataStats(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1min") String period) {
        
        try {
            StockService.ChartDataStats stats = stockService.getChartDataStats(symbol, period);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 지원하는 차트 기간 목록 조회
     */
    @GetMapping("/chart/periods")
    public ResponseEntity<List<String>> getSupportedPeriods() {
        List<String> periods = List.of("1min", "5min", "10min", "30min", "60min", "1day", "1week", "1month");
        return ResponseEntity.ok(periods);
    }

    /**
     * 전체 종목 목록 조회 (페이지네이션)
     */
    @GetMapping("/stocks/all")
    public ResponseEntity<Map<String, Object>> getAllStocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<Company> allStocks = stockService.getAllStocks(page, size);
        int totalCount = stockService.getTotalStockCount();
        int totalPages = (int) Math.ceil((double) totalCount / size);
        
        Map<String, Object> response = new HashMap<>();
        response.put("stocks", allStocks);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", totalCount);
        response.put("totalPages", totalPages);
        response.put("hasNext", page < totalPages - 1);
        response.put("hasPrevious", page > 0);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 전체 종목 목록 조회 (구독 정보 포함)
     */
    @GetMapping("/stocks/all/with-subscription")
    public ResponseEntity<Map<String, Object>> getAllStocksWithSubscription(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<Company> allStocks = stockService.getAllStocks(page, size);
        int totalCount = stockService.getTotalStockCount();
        int totalPages = (int) Math.ceil((double) totalCount / size);
        
        Map<String, Object> response = new HashMap<>();
        response.put("stocks", allStocks);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", totalCount);
        response.put("totalPages", totalPages);
        response.put("hasNext", page < totalPages - 1);
        response.put("hasPrevious", page > 0);
        response.put("subscription", Map.of(
            "type", "subscribe_all_stocks",
            "description", "전체 종목 실시간 구독 (5초마다 업데이트)"
        ));
        
        return ResponseEntity.ok(response);
    }

  
}