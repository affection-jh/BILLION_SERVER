from zoneinfo import ZoneInfo

#
# 시간대 설정
LOCAL_TZ = ZoneInfo("Asia/Seoul")

# 시간 관련 설정
UPDATE_INTERVAL = 0.5  # 데이터 업데이트 간격 (초)

# 회사 목록
COMPANIES = ["corp1", "corp2", "corp3", "corp4", "corp5", "corp6"]

# 60초(하루) * 7시간(1주) * 4주(1달) * 3달
# 최대 저장 데이터량 설정
ONE_DAY_SECONDS = 60
CANDLE_PER_DAY = 5
MAX_STORAGE_TIME = ONE_DAY_SECONDS * 7 * 4 * 3

# 초기 주식 가격 설정
INITIAL_PRICES = {
    "corp1": 50.0,
    "corp2": 70.0,
    "corp3": 100.0,
    "corp4": 120.0,
    "corp5": 170.0,
    "corp6": 200.0,
}

# 데이터 업데이트 주기 설정 (초 단위)
UPDATE_PERIODS = {
    "day": 1,      # 1초마다 업데이트
    "week": 7,    # 10초마다 업데이트
    "month": 28,   # 25초마다 업데이트
    "quarter": 80  # 80초마다 업데이트
}

PERIOD_RANGE = {
    "day": 60,
    "week": 420,
    "month": 1680,
    "quarter": 5040
}


# 초기 데이터 구조 생성
def create_initial_data():
    stock_data = {comp: [] for comp in COMPANIES}
    candle_data = {comp: [] for comp in COMPANIES}
    initial_prices = {comp: [] for comp in COMPANIES}
    return stock_data, candle_data, initial_prices 