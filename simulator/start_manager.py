from simulator.market_data import MarketData
from simulator.data_genetator import generate_data, update_market_data
import threading

#
def start_market():
    # 마켓 데이터 인스턴스 생성
    market_data = MarketData()
    
    # 데이터 생성 스레드 시작
    threading.Thread(target=generate_data, args=(market_data,), daemon=True).start()
    
    # 데이터 집계 스레드 시작
    threading.Thread(target=update_market_data, args=(market_data,), daemon=True).start()
    
    return market_data 
    