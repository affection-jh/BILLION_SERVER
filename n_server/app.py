from flask import Flask
from simulator.start_manager import start_market
from routes.stock_routes import stock_bp, init_routes
from simulator.config import COMPANIES
from websocket.web_socket_api import create_websocket_manager
import logging  # flask.logging 대신 기본 logging 모듈 사용

#
# Flask 앱 생성
app = Flask(__name__)

# 마켓 데이터 초기화
market_data = start_market()

# 블루프린트 등록
app.register_blueprint(stock_bp)

# 라우트 초기화
init_routes((
    market_data.stock_data, 
    market_data.candle_data, 
    market_data.aggregated_data
))

# WebSocket 매니저 초기화
ws_manager = create_websocket_manager()
ws_manager.init_websocket(
    market_data.stock_data,
    COMPANIES
)
socketio = ws_manager.start(app)

if __name__ == "__main__":
    # 서버 시작
    logging.info("서버 시작")
    socketio.run(app, host="0.0.0.0", port=5000, debug=False, allow_unsafe_werkzeug=True)
