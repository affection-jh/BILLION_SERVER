package com.nbillion.exception;

/**
 * 지원하지 않는 차트 기간이 요청되었을 때 발생하는 예외
 */
public class InvalidChartPeriodException extends StockException {
    
    public InvalidChartPeriodException(String period) {
        super(ErrorCode.INVALID_CHART_PERIOD, String.format("지원하지 않는 차트 기간입니다: '%s'. 지원되는 기간: 1min, 5min, 10min, 30min, 60min, 1day, 1week, 1month", period));
    }
    
    public InvalidChartPeriodException(String period, Throwable cause) {
        super(ErrorCode.INVALID_CHART_PERIOD, String.format("지원하지 않는 차트 기간입니다: '%s'. 지원되는 기간: 1min, 5min, 10min, 30min, 60min, 1day, 1week, 1month", period), cause);
    }
} 