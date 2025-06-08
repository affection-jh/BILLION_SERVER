from zoneinfo import ZoneInfo

#
# 시간대 설정
LOCAL_TZ = ZoneInfo("Asia/Seoul")

# 시간 관련 설정
UPDATE_INTERVAL = 0.4  # 데이터 업데이트 간격 (초)

# 회사 목록
COMPANIES = ["corp1", "corp2", "corp3", "corp4", "corp5", "corp6", "corp7", "corp8", "corp9", "corp10", "corp11", "corp12", "corp13", "corp14", "corp15", "corp16", "corp17", "corp18", "corp19"]

# 60초(하루) * 7시간(1주) * 4주(1달) * 3달
# 최대 저장 데이터량 설정
ONE_DAY_SECONDS = 60
CANDLE_PER_DAY = 5
MAX_STORAGE_TIME = ONE_DAY_SECONDS * 7 * 4 * 3

# 초기 주식 가격 설정
INITIAL_PRICES = {
    "corp1": 30.0,
    "corp2": 40.0,
    "corp3": 50.0,
    "corp4": 60.0,
    "corp5": 70.0,
    "corp6": 80.0,
    "corp7": 100.0,
    "corp8": 120.0,
    "corp9": 150.0,
    "corp10": 170.0,
    "corp11": 190.0,
    "corp12": 210.0,
    "corp13": 250.0,
    "corp14": 300.0,
    "corp15": 350.0,
    "corp16": 400.0,
    "corp17": 450.0,
    "corp18": 500.0,
    "corp19": 550.0,
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