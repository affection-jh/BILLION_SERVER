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
import java.util.ArrayList;
import java.util.Arrays;
import org.springframework.http.HttpStatus;

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
     * 지원하는 차트 기간 목록 조회 (간단한 버전)
     * GET /api/stocks/chart/periods
     */
    @GetMapping("/chart/periods")
    public ResponseEntity<List<String>> getSupportedPeriods() {
        List<String> periods = List.of("1min", "5min", "10min", "30min", "60min", "1hour", "1day");
        return ResponseEntity.ok(periods);
    }

    /**
     * 모든 주식 조회 (페이징 지원)
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllStocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            List<Company> allStocks = stockService.getAllStocks(page, size);
            int totalCount = stockService.getTotalStockCount();
            
            Map<String, Object> response = new HashMap<>();
            response.put("stocks", allStocks);
            response.put("page", page);
            response.put("size", size);
            response.put("totalCount", totalCount);
            response.put("hasNext", (page + 1) * size < totalCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "서버 내부 오류 발생");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
        }
    }

    /**
     * 모든 주식 조회 + WebSocket 구독 정보 포함
     */
    @GetMapping("/all/with-subscription")
    public ResponseEntity<Map<String, Object>> getAllStocksWithSubscription(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            List<Company> allStocks = stockService.getAllStocks(page, size);
            int totalCount = stockService.getTotalStockCount();
            
            Map<String, Object> response = new HashMap<>();
            response.put("stocks", allStocks);
            response.put("page", page);
            response.put("size", size);
            response.put("totalCount", totalCount);
            response.put("hasNext", (page + 1) * size < totalCount);
            response.put("websocket_subscription", Map.of(
                "url", "ws://localhost:8080/ws/stocks",
                "message", Map.of(
                    "type", "subscribe_all"
                ),
                "description", "이 메시지를 WebSocket으로 전송하면 모든 종목의 실시간 데이터를 받을 수 있습니다."
            ));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "서버 내부 오류 발생");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
        }
    }

    /**
     * 파이썬과 동일한 API: 샘플링된 가격 데이터 [timestamp, price] 형태
     * GET /api/stocks/{symbol}/{period}
     */
    @GetMapping("/{symbol}/{period}")
    public ResponseEntity<List<List<Object>>> getStockHistory(
            @PathVariable String symbol,
            @PathVariable String period) {
        try {
            List<List<Object>> historyData = stockService.getSampledData(symbol, period);
            return ResponseEntity.ok(historyData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ArrayList<>());
        }
    }
    
    /**
     * 파이썬과 동일한 API: 현재 가격들
     * GET /api/stocks/current_prices
     */
    @GetMapping("/current_prices")
    public ResponseEntity<Map<String, Double>> getCurrentPrices() {
        try {
            Map<String, Double> prices = stockService.getCurrentPrices();
            return ResponseEntity.ok(prices);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new HashMap<>());
        }
    }

    /**
     * 파이썬과 동일한 API: 초기 가격들
     * GET /api/stocks/initial_prices
     */
    @GetMapping("/initial_prices")
    public ResponseEntity<Map<String, Map<String, Double>>> getInitialPrices() {
        try {
            Map<String, Map<String, Double>> result = new HashMap<>();
            List<String> periods = Arrays.asList("day", "week", "month", "quarter");
            
            for (String symbol : stockService.getAllStockSymbols()) {
                Map<String, Double> symbolPrices = new HashMap<>();
                for (String period : periods) {
                    List<List<Object>> sampledData = stockService.getSampledData(symbol, period);
                    if (!sampledData.isEmpty()) {
                        symbolPrices.put(period, (Double) sampledData.get(0).get(1));
                    }
                }
                result.put(symbol, symbolPrices);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new HashMap<>());
        }
    }

   

    // ==============================================================================
    // 독립적인 캔들 기간 엔드포인트들
    // ==============================================================================
    
    /**
     * 1분봉
     * GET /api/stocks/{symbol}/chart/1min?page=0&size=30
     */
    @GetMapping("/{symbol}/chart/1min")
    public ResponseEntity<Map<String, Object>> getChart1Min(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return getChartDataByPeriod(symbol, "1min", "1min", page, size);
    }
    
    /**
     * 5분봉
     * GET /api/stocks/{symbol}/chart/5min?page=0&size=30
     */
    @GetMapping("/{symbol}/chart/5min")
    public ResponseEntity<Map<String, Object>> getChart5Min(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return getChartDataByPeriod(symbol, "5min", "5min", page, size);
    }
    
    /**
     * 10분봉
     * GET /api/stocks/{symbol}/chart/10min?page=0&size=30
     */
    @GetMapping("/{symbol}/chart/10min")
    public ResponseEntity<Map<String, Object>> getChart10Min(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return getChartDataByPeriod(symbol, "10min", "10min", page, size);
    }
    
    /**
     * 30분봉
     * GET /api/stocks/{symbol}/chart/30min?page=0&size=30
     */
    @GetMapping("/{symbol}/chart/30min")
    public ResponseEntity<Map<String, Object>> getChart30Min(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return getChartDataByPeriod(symbol, "30min", "30min", page, size);
    }
    
    /**
     * 60분봉
     * GET /api/stocks/{symbol}/chart/60min?page=0&size=30
     */
    @GetMapping("/{symbol}/chart/60min")
    public ResponseEntity<Map<String, Object>> getChart60Min(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return getChartDataByPeriod(symbol, "60min", "60min", page, size);
    }
    
    /**
     * 1시간봉
     * GET /api/stocks/{symbol}/chart/1hour?page=0&size=30
     */
    @GetMapping("/{symbol}/chart/1hour")
    public ResponseEntity<Map<String, Object>> getChart1Hour(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return getChartDataByPeriod(symbol, "1hour", "1hour", page, size);
    }
    
    /**
     * 1일봉
     * GET /api/stocks/{symbol}/chart/1day?page=0&size=30
     */
    @GetMapping("/{symbol}/chart/1day")
    public ResponseEntity<Map<String, Object>> getChart1Day(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return getChartDataByPeriod(symbol, "1day", "1day", page, size);
    }

    /**
     * 공통 차트 데이터 조회 메서드 (페이지네이션 포함)
     */
    private ResponseEntity<Map<String, Object>> getChartDataByPeriod(String symbol, String fullPeriod, String displayPeriod, int page, int size) {
        try {
            List<Map<String, Object>> allData = stockService.getCandleData(symbol, fullPeriod);
            
            // 페이지네이션 계산
            int totalCount = allData.size();
            int totalPages = (int) Math.ceil((double) totalCount / size);
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, totalCount);
            
            // 페이지 데이터 추출
            List<Map<String, Object>> pageData = new ArrayList<>();
            if (startIndex < totalCount) {
                pageData = allData.subList(startIndex, endIndex);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("period", displayPeriod);
            response.put("data", pageData);
            response.put("count", pageData.size());
            response.put("totalCount", totalCount);
            response.put("page", page);
            response.put("size", size);
            response.put("totalPages", totalPages);
            response.put("hasNext", page < totalPages - 1);
            response.put("hasPrevious", page > 0);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "서버 내부 오류 발생");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
        }
    }


  
}