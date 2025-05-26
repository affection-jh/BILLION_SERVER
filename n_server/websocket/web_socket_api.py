# ws_server.py
import time
import threading

from flask import logging
from flask_socketio import SocketIO
from simulator.config import UPDATE_INTERVAL

class WebSocketManager:
    def __init__(self):
        self.stock_data = None
        self.companies = None
        self.socketio = None
        self._emit_thread = None

    def init_websocket(self, stock_data, companies):
        self.stock_data = stock_data
        self.companies = companies

    def _start_emit_thread(self):
        if self._emit_thread is None or not self._emit_thread.is_alive():
            self._emit_thread = threading.Thread(target=self.emit_stock_prices, daemon=True)
            self._emit_thread.start()

    def handle_connect(self):
        try:
            self.socketio.emit("message", {
                "type": "welcome",
                "msg": "주가 실시간 스트리밍 시작"
            })
        except Exception as e:
            logging.error(f"웹소켓 연결 오류: {e}")

    def emit_stock_prices(self):
        while True:
            try:
                if not self.stock_data or not self.companies:
                    time.sleep(UPDATE_INTERVAL)
                    continue

                price_data = {
                    comp: self.stock_data[comp][-1]["price"]
                    for comp in self.companies
                    if comp in self.stock_data and self.stock_data[comp]
                }

                # 데이터 방출
                if price_data and self.socketio:
                    self.socketio.emit("price_update", price_data)

            except Exception as e:
                logging.error(f"실시간 데이터 송출 중 오류 발생: {e}")
            
            time.sleep(UPDATE_INTERVAL)

    def start(self, app):
        # SocketIO 인스턴스 생성
        self.socketio = SocketIO(
            app,
            cors_allowed_origins="*",
            async_mode='threading',
            logger=False,
            engineio_logger=False,
            ping_timeout=60,
            ping_interval=25,
            host='0.0.0.0',  # 모든 네트워크 인터페이스에서 접속 허용
            allow_upgrades=True,  # WebSocket 업그레이드 허용
            transports=['websocket', 'polling']  # 클라이언트와 동일한 전송 방식 지원
        )
        
        # 이벤트 핸들러 등록
        # 데이터 송출 스레드 시작
        self._start_emit_thread()
            
        return self.socketio

def create_websocket_manager():
    return WebSocketManager()
