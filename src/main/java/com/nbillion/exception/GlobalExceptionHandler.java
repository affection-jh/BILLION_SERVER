package com.nbillion.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 전역 예외 처리기
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 주식을 찾을 수 없는 경우
     */
    @ExceptionHandler(StockNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStockNotFoundException(
            StockNotFoundException e, 
            HttpServletRequest request) {
        
        log.warn("주식을 찾을 수 없음: {}", e.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.of(
            e.getErrorCode(), 
            e.getMessage(), 
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 잘못된 차트 기간이 요청된 경우
     */
    @ExceptionHandler(InvalidChartPeriodException.class)
    public ResponseEntity<ErrorResponse> handleInvalidChartPeriodException(
            InvalidChartPeriodException e, 
            HttpServletRequest request) {
        
        log.warn("잘못된 차트 기간 요청: {}", e.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.of(
            e.getErrorCode(), 
            e.getMessage(), 
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 일반적인 주식 관련 예외
     */
    @ExceptionHandler(StockException.class)
    public ResponseEntity<ErrorResponse> handleStockException(
            StockException e, 
            HttpServletRequest request) {
        
        log.error("주식 관련 예외 발생: {}", e.getMessage(), e);
        
        ErrorResponse errorResponse = ErrorResponse.of(
            e.getErrorCode(), 
            e.getMessage(), 
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 잘못된 파라미터 타입 (예: 숫자 대신 문자열 전송)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, 
            HttpServletRequest request) {
        
        log.warn("잘못된 파라미터 타입: {} = {}", e.getName(), e.getValue());
        
        ErrorResponse errorResponse = ErrorResponse.of(
            ErrorCode.INVALID_TYPE_VALUE,
            String.format("파라미터 '%s'의 값 '%s'이(가) 올바른 타입이 아닙니다.", e.getName(), e.getValue()),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 기타 예상치 못한 예외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e, 
            HttpServletRequest request) {
        
        log.error("예상치 못한 예외 발생: {}", e.getMessage(), e);
        
        ErrorResponse errorResponse = ErrorResponse.of(
            ErrorCode.INTERNAL_SERVER_ERROR,
            "서버 내부 오류가 발생했습니다.",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
} 