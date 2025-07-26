package com.nbillion.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 코드와 메시지를 정의하는 enum
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    
    // 공통 에러 (1000번대)
    INTERNAL_SERVER_ERROR(1003, "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(1004, "잘못된 타입의 값입니다."),
    
    // 주식 관련 에러 (2000번대)
    STOCK_NOT_FOUND(2000, "요청한 주식을 찾을 수 없습니다."),
    INVALID_CHART_PERIOD(2002, "지원하지 않는 차트 기간입니다. 지원되는 기간: 1min, 5min, 10min, 30min, 60min, 1day, 1week, 1month");
    
    private final int code;
    private final String message;
} 