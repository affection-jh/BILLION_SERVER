from collections import deque
from .config import COMPANIES, INITIAL_PRICES, MAX_STORAGE_TIME, ONE_DAY_SECONDS, CANDLE_PER_DAY
import time
#
class MarketData:
    def __init__(self):
        # 주가 데이터 초기화
        self.stock_data = {
            company: deque([{
                "time": time.time(),
                "price": INITIAL_PRICES[company]
            }], maxlen=MAX_STORAGE_TIME)
            for company in COMPANIES
        }
        
        # 캔들 데이터 초기화
        self.candle_data = {
            company: {
                'day': deque(maxlen=15),
                'week': deque(maxlen=7),
                'month': deque(maxlen=30),
                'quarter': deque(maxlen=30)
            }
            for company in COMPANIES
        }      

        self.aggregated_data = {
            company: {
                'day': deque(maxlen=ONE_DAY_SECONDS),
                'week': deque(maxlen=ONE_DAY_SECONDS * 7),
                'month': deque(maxlen=ONE_DAY_SECONDS * 30),
                'quarter': deque(maxlen=ONE_DAY_SECONDS * 90)
            }
            for company in COMPANIES
        }

        self.max_storage_time = MAX_STORAGE_TIME 