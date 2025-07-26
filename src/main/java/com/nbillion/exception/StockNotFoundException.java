package com.nbillion.exception;

/**
 * 요청한 주식을 찾을 수 없을 때 발생하는 예외
 */
public class StockNotFoundException extends StockException {
    
    public StockNotFoundException(String symbol) {
        super(ErrorCode.STOCK_NOT_FOUND, String.format("주식 심볼 '%s'을(를) 찾을 수 없습니다.", symbol));
    }
    
    public StockNotFoundException(String symbol, Throwable cause) {
        super(ErrorCode.STOCK_NOT_FOUND, String.format("주식 심볼 '%s'을(를) 찾을 수 없습니다.", symbol), cause);
    }
} 