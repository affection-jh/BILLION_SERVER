package com.nbillion.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbillion.model.TickerMessage;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class StockWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 연결된 모든 클라이언트 세션 관리
    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    
    // 전체 종목 구독 세션 (모든 35개 종목)
    private final Set<WebSocketSession> allStocksSubscribers = ConcurrentHashMap.newKeySet();
    
    // 상세보기 구독 세션 (개별 종목)
    private final Map<String, Set<WebSocketSession>> detailSubscribers = new ConcurrentHashMap<>();

    // 세션별 동기화를 위한 락
    private final Map<WebSocketSession, Object> sessionLocks = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        sessionLocks.put(session, new Object());
        System.out.println("WebSocket 연결됨: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            System.out.println("받은 메시지: " + payload);
            
            // JSON 파싱
            Map<String, Object> request = objectMapper.readValue(payload, Map.class);
            String type = (String) request.get("type");
            
            switch (type) {
                case "subscribe_all":
                    handleAllStocksSubscription(session);
                    break;
                case "subscribe_detail":
                    String symbol = (String) request.get("symbol");
                    handleDetailSubscription(session, symbol);
                    break;
                case "unsubscribe_detail":
                    String unsubSymbol = (String) request.get("symbol");
                    handleDetailUnsubscription(session, unsubSymbol);
                    break;
                case "unsubscribe_all":
                    handleUnsubscribeAll(session);
                    break;
                default:
                    sendError(session, "알 수 없는 메시지 타입: " + type);
            }
            
        } catch (Exception e) {
            System.err.println("메시지 처리 오류: " + e.getMessage());
            sendError(session, "메시지 처리 중 오류가 발생했습니다.");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        sessionLocks.remove(session);
        allStocksSubscribers.remove(session);
        
        // 상세보기 구독에서도 제거
        detailSubscribers.values().forEach(subscribers -> subscribers.remove(session));
        
        System.out.println("WebSocket 연결 종료: " + session.getId());
    }

    /**
     * 전체 종목 구독 처리 (모든 35개 종목)
     */
    private void handleAllStocksSubscription(WebSocketSession session) {
        allStocksSubscribers.add(session);
        sendMessage(session, "전체 종목 구독이 완료되었습니다. (35개 종목)");
        System.out.println("전체 종목 구독 추가: " + session.getId());
    }

    /**
     * 특정 종목 상세보기 구독 처리
     */
    private void handleDetailSubscription(WebSocketSession session, String symbol) {
        detailSubscribers.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(session);
        sendMessage(session, symbol + " 상세보기 구독이 완료되었습니다.");
        System.out.println("상세보기 구독 추가: " + session.getId() + " -> " + symbol);
    }

    /**
     * 특정 종목 상세보기 구독 해제 처리
     */
    private void handleDetailUnsubscription(WebSocketSession session, String symbol) {
        Set<WebSocketSession> subscribers = detailSubscribers.get(symbol);
        if (subscribers != null) {
            subscribers.remove(session);
            if (subscribers.isEmpty()) {
                detailSubscribers.remove(symbol);
            }
        }
        sendMessage(session, symbol + " 상세보기 구독이 해제되었습니다.");
        System.out.println("상세보기 구독 해제: " + session.getId() + " -> " + symbol);
    }

    /**
     * 모든 구독 해제 처리
     */
    private void handleUnsubscribeAll(WebSocketSession session) {
        allStocksSubscribers.remove(session);
        
        // 상세보기 구독에서도 모두 제거
        detailSubscribers.values().forEach(subscribers -> subscribers.remove(session));
        
        sendMessage(session, "모든 구독이 해제되었습니다.");
        System.out.println("모든 구독 해제: " + session.getId());
    }

    /**
     * 전체 종목 구독자들에게 실시간 데이터 전송 (통합)
     */
    public void broadcastToAllStocks(TickerMessage tickerMessage) {
        String message;
        try {
            message = objectMapper.writeValueAsString(tickerMessage);
        } catch (Exception e) {
            System.err.println("JSON 직렬화 오류: " + e.getMessage());
            return;
        }
        
        List<WebSocketSession> sessionsToRemove = new ArrayList<>();
        
        for (WebSocketSession session : allStocksSubscribers) {
            if (session.isOpen()) {
                Object lock = sessionLocks.get(session);
                if (lock != null) {
                    synchronized (lock) {
                        try {
                            session.sendMessage(new TextMessage(message));
                        } catch (Exception e) {
                            System.err.println("전체 종목 구독자 전송 실패: " + e.getMessage());
                            sessionsToRemove.add(session);
                        }
                    }
                }
            } else {
                sessionsToRemove.add(session);
            }
        }
        
        // 연결이 끊어진 세션들 제거
        sessionsToRemove.forEach(allStocksSubscribers::remove);
    }

    /**
     * 특정 종목 상세보기 구독자들에게 실시간 데이터 전송
     */
    public void broadcastToDetail(String symbol, TickerMessage tickerMessage) {
        Set<WebSocketSession> subscribers = detailSubscribers.get(symbol);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        
        String message;
        try {
            message = objectMapper.writeValueAsString(tickerMessage);
        } catch (Exception e) {
            System.err.println("JSON 직렬화 오류: " + e.getMessage());
            return;
        }
        
        List<WebSocketSession> sessionsToRemove = new ArrayList<>();
        
        for (WebSocketSession session : subscribers) {
            if (session.isOpen()) {
                Object lock = sessionLocks.get(session);
                if (lock != null) {
                    synchronized (lock) {
                        try {
                            session.sendMessage(new TextMessage(message));
                        } catch (Exception e) {
                            System.err.println("상세보기 구독자 전송 실패: " + e.getMessage());
                            sessionsToRemove.add(session);
                        }
                    }
                }
            } else {
                sessionsToRemove.add(session);
            }
        }
        
        // 연결이 끊어진 세션들 제거
        sessionsToRemove.forEach(subscribers::remove);
        if (subscribers.isEmpty()) {
            detailSubscribers.remove(symbol);
        }
    }

    /**
     * 개별 세션에 메시지 전송
     */
    private void sendMessage(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (Exception e) {
            System.err.println("메시지 전송 실패: " + e.getMessage());
        }
    }

    /**
     * 개별 세션에 에러 메시지 전송
     */
    private void sendError(WebSocketSession session, String error) {
        try {
            if (session.isOpen()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("type", "error");
                errorResponse.put("message", error);
                String errorJson = objectMapper.writeValueAsString(errorResponse);
                session.sendMessage(new TextMessage(errorJson));
            }
        } catch (Exception e) {
            System.err.println("에러 메시지 전송 실패: " + e.getMessage());
        }
    }
}