package com.nbillion.exception;

/**
 * 주식 관련 기본 예외 클래스
 */
public class StockException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public StockException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public StockException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public StockException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
} 