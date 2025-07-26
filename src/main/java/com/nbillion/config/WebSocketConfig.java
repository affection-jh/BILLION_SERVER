package com.nbillion.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import com.nbillion.websocket.StockWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final StockWebSocketHandler stockWebSocketHandler;

    public WebSocketConfig(StockWebSocketHandler stockWebSocketHandler) {
        this.stockWebSocketHandler = stockWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(stockWebSocketHandler, "/ws/stocks")
                .setAllowedOrigins("*"); // CORS 허용. 실제 운영시 도메인 제한 권장
    }
}