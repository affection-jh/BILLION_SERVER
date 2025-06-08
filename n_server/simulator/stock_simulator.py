import numpy as np
from collections import deque
import random

# ==============================================================================
# 1. 전역 상수 및 상태 관리
# ==============================================================================

# 시뮬레이션 기본 상수
SIGMA = 0.025               # 기본 변동성 (작을수록 안정적)
MU = 0.00005                # 장기 기대 수익률 (매우 약한 우상향)
DT = 1.0 / 3600.0           # 시간 단위 (1초)

# 전역 상태 변수
stock_data = {}             # 모든 주식의 상태를 관리하는 중앙 저장소
hour_count = 0              # 시간 카운터
sec_count = 0               # 초 카운터

# ==============================================================================
# 2. 핵심 시뮬레이션 함수
# ==============================================================================

def generate_next_price(company_name, S0):
    """
    지정된 회사의 다음 주가를 생성하는 메인 함수.
    모든 로직의 진입점 역할을 합니다.
    """
    global sec_count, hour_count
    
    # 1. 주식 상태 초기화 (최초 호출 시)
    if company_name not in stock_data:
        _initialize_stock_state(company_name, S0)
    
    state = stock_data[company_name]
    S = state['price_history'][-1] if state['price_history'] else S0

    # 2. 이벤트 처리
    # 현재 진행 중인 특별 이벤트(개미털기, 급등)가 있는지 확인하고 처리
    if state['event']['type'] != 'none':
        event_handlers = {
            'surge': _handle_surge_event,
            'ant_shake': _handle_ant_shake_event
        }
        S = event_handlers[state['event']['type']](state, S)
    else:
        # 3. 일반 상태 처리
        # 특별 이벤트가 없을 때의 일반적인 주가 움직임
        S = _process_normal_state(state, S)
        
        # 4. 새로운 이벤트 발생 여부 확인
        _check_for_event_triggers(state, S)

    # 5. 최종 가격 조정 및 기록
    S = _finalize_price(state, S)
    
    # 6. 전역 시간 업데이트
    sec_count += 1
    if sec_count >= 3600:
        sec_count = 0
        hour_count += 1
        # 8시간마다 장기 추세 미세 조정
        if hour_count % 8 == 0:
            _adjust_long_term_trend(state)
            
    # 최종 가격 출력
    # 이벤트 발생 시에는 각 핸들러에서 메시지를 출력하므로 여기서는 일반 상태일 때만 가끔 출력
  
    ##print(f"[{company_name}] 가격: {S:.2f}")

    return S

def _process_normal_state(state, S):
    """일반 상태일 때의 주가 계산 로직"""
    
    # 1. 기본 움직임 계산 (브라운 운동 + 강력한 평균 회귀)
    base_movement = _calculate_base_movement(S, state['long_term_mean'])
    S += base_movement

    # 2. 거래량 시뮬레이션
    current_volume = _simulate_volume(state, S)
    state['volume_history'].append(current_volume)

    # 3. 지지/저항선 계산
    support, resistance = _calculate_support_resistance(state)

    # 4. 투자 심리 및 기술적 분석 효과 적용
    # (거래량, 지지/저항, 매물대 등의 영향을 종합)
    S = _apply_psychology_effects(state, S, current_volume, support, resistance)

    return S

# ==============================================================================
# 3. 이벤트 핸들러 (개미털기, 급등)
# ==============================================================================

def _handle_ant_shake_event(state, S):
    """'개미털기' 이벤트의 각 단계를 처리 (희망고문 추가)"""
    event = state['event']
    event['timer'] += 1
    
    # --- Phase 1: 급락 ---
    if event['phase'] == 'drop':
        target_drop_price = event['start_price'] * event['drop_rate']
        time_left = max(1, event['duration'] - event['timer'])
        drop_step = (S - target_drop_price) / time_left
        S -= drop_step * 1.5
        ##print(f"[{state['name']}] 🐜 개미털기 진행 중... 급락! ({S:.2f})")
        if event['timer'] >= event['duration']:
            event['phase'] = 'creep'
            event['timer'] = 0
            event['duration'] = random.randint(2700, 4500)
            ##print(f"[{state['name']}] 🥶 경고: 바닥에서 기는 '공포의 시간'이 시작됩니다... ({event['duration']//60}분 예상)")

    # --- Phase 2: 바닥 기어가기 (희망고문 추가) ---
    elif event['phase'] == 'creep':
        # 희망고문용 가짜 반등 체크
        if 'sub_phase' not in event or event['sub_phase'] == 'none':
            if random.random() < 0.005: # 매우 낮은 확률로 가짜 반등 시작
                event['sub_phase'] = 'false_hope'
                event['sub_phase_timer'] = 0
                event['sub_phase_duration'] = random.randint(15, 30) # 15-30초짜리 희망
                ##print(f"[{state['name']}] 🤔 어? 반등 신호인가...? 잠깐의 희망을 줄게.")
            else:
                # 평소의 공포 구간
                noise = random.uniform(-0.004, 0.002)
                S *= (1 + noise)
                if event['timer'] % 300 == 0:
                    remaining_time = (event['duration'] - event['timer']) // 60
                    ##print(f"[{state['name']}] 🥶 ...바닥에서 탈출 불가... 남은 공포: 약 {remaining_time}분. ({S:.2f})")
        
        # 가짜 반등 진행
        if event['sub_phase'] == 'false_hope':
            S *= 1.001 # 아주 조금씩 올리며 희망을 줌
            event['sub_phase_timer'] += 1
            if event['sub_phase_timer'] >= event['sub_phase_duration']:
                event['sub_phase'] = 'crush_hope' # 희망 짓밟기
                ##print(f"[{state['name']}] 😂 희망은 끝났어. 더 깊이 떨어져보자!")
        
        # 희망 짓밟기
        elif event['sub_phase'] == 'crush_hope':
             S *= 0.98 # 이전보다 더 강하게 하락
             event['sub_phase'] = 'none' # 다시 평소의 공포 구간으로

        # 전체 공포 시간 종료 체크
        if event['timer'] >= event['duration']:
            event['phase'] = 'recover'
            event['timer'] = 0
            event['duration'] = random.randint(300, 600)
            ##print(f"[{state['name']}] 🚀🚀🚀 진짜 V자 반등 시작! 이번엔 진짜야!")
            
    # --- Phase 3: V자 반등 및 폭등 ---
    elif event['phase'] == 'recover':
        target_recover_price = event['start_price'] * 1.20 
        time_left = max(1, event['duration'] - event['timer'])
        recover_step = (target_recover_price - S) / time_left
        S += recover_step * 1.2
        current_gain = ((S / event['start_price']) - 1) * 100
        ##print(f"[{state['name']}] 🚀 V자 반등 중! 시작가 대비 +{current_gain:.1f}% ({S:.2f})")
        if event['timer'] >= event['duration']:
            ##print(f"[{state['name']}] ✅ 개미털기 종료. 여기까지 버틴 당신이 승리자!")
            _reset_event(state)
            
    return S

def _handle_surge_event(state, S):
    """'급등 후 급락' 이벤트의 각 단계를 처리"""
    event = state['event']
    event['timer'] += 1

    # --- Phase 1: 급등 ---
    if event['phase'] == 'surge':
        target_price = event['target_price']
        surge_step = (target_price - S) / max(1, (event['duration'] - event['timer']))
        S += surge_step
        #print(f"[{state['name']}] 📈 급등주 발동! (+{event['surge_rate']*100:.1f}%) 목표가: {target_price:.2f} 현재가: {S:.2f}")

        if event['timer'] >= event['duration']:
            event['phase'] = 'peak'
            event['timer'] = 0
            event['duration'] = random.randint(20, 60) # 20-60초간 고점 유지
            #print(f"[{state['name']}] 🏔️ 고점 도달! 더 갈 수 있을까? ({S:.2f})")

    # --- Phase 2: 고점 유지 ---
    elif event['phase'] == 'peak':
        noise = random.uniform(-0.01, 0.01) # 고점에서 약한 흔들기
        S *= (1 + noise)
        ##print(f"[{state['name']}] 🏔️ 고점 파티 중... 폭탄 돌리기 마지막 주자는 누구? ({S:.2f})")

        if event['timer'] >= event['duration']:
            event['phase'] = 'crash'
            event['timer'] = 0
            event['duration'] = random.randint(60, 180) # 1-3분간 급락
            ##print(f"[{state['name']}] 💥 파티는 끝났다! 탈출구는 없어!")

    # --- Phase 3: 급락 ---
    elif event['phase'] == 'crash':
        # 기존 가격보다 더 낮은 가격으로 급락시켜 공포감 조성
        target_crash_price = event['start_price'] * 0.95 
        crash_step = (S - target_crash_price) / max(1, (event['duration'] - event['timer']))
        S -= crash_step * 1.5 # 가속 하락
        ##print(f"[{state['name']}] 💥 급락 중!!! ({S:.2f})")

        if event['timer'] >= event['duration']:
            ##print(f"[{state['name']}] ✅ 급등 사이클 종료. 다음 호구는 누구?")
            _reset_event(state)

    return S

def _handle_trap_event(state, S):
    """'통수 차트' (Bull/Bear Trap) 이벤트를 처리"""
    event = state['event']
    event['timer'] += 1
    
    # --- Phase 1: 유인 (The Lure) ---
    if event['phase'] == 'lure':
        # 목표 지점을 향해 꾸준히 이동하며 투자자를 유인
        move_step = (event['target_price'] - S) / max(1, (event['duration'] - event['timer']))
        S += move_step
        ##print(f"[{state['name']}] 🤔 뚫으러 가나...? ({S:.2f})")

        if event['timer'] >= event['duration']:
            event['phase'] = 'snap'
            event['timer'] = 0
            event['duration'] = random.randint(10, 20) # 10~20초간의 급반전
        

    # --- Phase 2: 함정 발동 (The Snap) ---
    elif event['phase'] == 'snap':
        # 유인했던 방향과 정반대로 급격히 움직임
        reverse_direction = -1 if event['trap_type'] == 'bull_trap' else 1
        snap_force = event['start_price'] * 0.005 * reverse_direction
        S += snap_force
        ##print(f"[{state['name']}] 😂 거봐, 내 그럴 줄 알았지! ({S:.2f})")

        if event['timer'] >= event['duration']:
             _reset_event(state)
             ##print(f"[{state['name']}] ✅ 함정 발동 완료. 수고~")
             
    return S

# ==============================================================================
# 4. 하위 계산 함수들
# ==============================================================================

def _calculate_base_movement(S, long_term_mean):
    """브라운 운동과 평균 회귀를 결합하여 기본 움직임을 계산"""
    # 1. 브라운 운동 (랜덤 워크)
    dW = np.random.normal(0, np.sqrt(DT))
    brownian_motion = MU * S * DT + SIGMA * S * dW
    
    # 2. 강력한 평균 회귀 (가장 중요한 안정성 장치)
    # 주가가 장기 평균에서 멀어질수록 되돌아가려는 힘이 강해짐
    reversion_force = (long_term_mean - S) * 0.005  # 회귀 강도
    
    return brownian_motion + reversion_force

def _simulate_volume(state, S):
    """현실적인 거래량을 시뮬레이션"""
    base_volume = state['base_volume']
    
    # 가격 변동이 클수록 거래량 증가
    price_change = abs(S - state['price_history'][-1]) / state['price_history'][-1]
    volatility_factor = 1 + min(price_change * 50, 3.0)
    
    random_factor = random.uniform(0.7, 1.5)
    
    volume = int(base_volume * volatility_factor * random_factor)
    return max(100, volume)

def _calculate_support_resistance(state):
    """최근 가격 히스토리를 기반으로 동적 지지/저항선 계산"""
    if len(state['price_history']) < 100:
        return state['long_term_mean'] * 0.9, state['long_term_mean'] * 1.1
    
    recent_prices = list(state['price_history'])[-300:]
    support = np.percentile(recent_prices, 25)
    resistance = np.percentile(recent_prices, 75)
    return support, resistance

def _apply_psychology_effects(state, S, volume, support, resistance):
    """거래량, 지지/저항선 등 투자 심리가 주가에 미치는 영향을 적용"""
    # 지지선 근처, 거래량 터지면 반등 확률 증가
    if S < support * 1.01 and volume > state['base_volume'] * 1.5:
        S *= (1 + random.uniform(0.001, 0.005))


    # 저항선 근처, 거래량 터지면 돌파 시도
    elif S > resistance * 0.95 and volume > state['base_volume'] * 1.5:
        S *= (1 + random.uniform(-0.001, 0.008))

        
    
        
    return S

def _check_for_event_triggers(state, S):
    """특별 이벤트(개미털기, 급등)의 발생 조건을 확인하고 트리거"""
    global sec_count
    
    # 이미 이벤트가 진행 중이면 새로운 이벤트를 발생시키지 않음
    if state['event']['type'] != 'none':
        return

    # 개미털기: 1시간(3600초)마다 3% 확률로, 변동성이 적을 때 발생
    if sec_count != 0 and sec_count % 3600 == 0 and random.random() < 0.03:
        if len(state['price_history']) > 100:
            recent_volatility = np.std(list(state['price_history'])[-100:]) / S
            if recent_volatility < 0.01:
                state['event']['type'] = 'ant_shake'
                state['event']['phase'] = 'drop'
                state['event']['timer'] = 0
                state['event']['duration'] = random.randint(60, 120)
                state['event']['start_price'] = S
                state['event']['drop_rate'] = random.uniform(0.9, 0.95)
                #print(f"[{state['name']}] 🐜 경고: 대규모 개미털기 패턴 발생!")
                return

    # 급등 후 급락: 2시간(7200초)마다 4% 확률로 발생
    if sec_count != 0 and sec_count % 7200 == 0 and random.random() < 0.04:
        state['event']['type'] = 'surge'
        state['event']['phase'] = 'surge'
        state['event']['timer'] = 0
        state['event']['duration'] = random.randint(60, 120)
        state['event']['start_price'] = S
        state['event']['surge_rate'] = random.uniform(0.1, 0.15)
        state['event']['target_price'] = S * (1 + state['event']['surge_rate'])
        #print(f"[{state['name']}] 🚀 급등주 포착! 상승 시작!")
        return

def _trigger_trap_event_if_needed(state, S, support, resistance):
    """(신규) 지지/저항선 근처에서 '통수 차트' 이벤트를 트리거"""
    if state['event']['type'] != 'none':
        return False

    # Bull Trap (상승 함정)
    if S > resistance * 0.98 and random.random() < 0.01: # 저항선 근처에서 1% 확률
        event = state['event']
        event['type'] = 'trap'
        event['trap_type'] = 'bull_trap'
        event['phase'] = 'lure'
        event['timer'] = 0
        event['duration'] = random.randint(20, 40)
        event['start_price'] = S
        event['target_price'] = resistance * 1.02 # 저항선을 살짝 넘도록 유인
        return True

    # Bear Trap (하락 함정)
    if S < support * 1.02 and random.random() < 0.01: # 지지선 근처에서 1% 확률
        event = state['event']
        event['type'] = 'trap'
        event['trap_type'] = 'bear_trap'
        event['phase'] = 'lure'
        event['timer'] = 0
        event['duration'] = random.randint(20, 40)
        event['start_price'] = S
        event['target_price'] = support * 0.98 # 지지선을 살짝 깨도록 유인
        return True

    return False
    
# ==============================================================================
# 5. 유틸리티 함수
# ==============================================================================

def _initialize_stock_state(company_name, S0):
    """새로운 주식에 대한 상태 정보를 초기화"""
    stock_data[company_name] = {
        'name': company_name,
        'price_history': deque(maxlen=1000),
        'volume_history': deque(maxlen=1000),
        'long_term_mean': S0,
        'base_volume': random.randint(100000, 300000),
        'event': {
            'type': 'none', 'phase': '', 'timer': 0, 'duration': 0, 'start_price': 0,
            'sub_phase': 'none', 'sub_phase_timer': 0, 'sub_phase_duration': 0, # 희망고문용
            'trap_type': '', 'target_price': 0, 'drop_rate': 0, 'surge_rate': 0 # 이벤트별 데이터
        }
    }
    stock_data[company_name]['price_history'].append(S0)
    #print(f"'{company_name}' 주식 시뮬레이션 시작. (초기 가격: {S0})")

def _reset_event(state):
    """이벤트 상태를 초기화"""
    state['event'] = {
        'type': 'none', 'phase': '', 'timer': 0, 'duration': 0, 'start_price': 0,
        'sub_phase': 'none', 'sub_phase_timer': 0, 'sub_phase_duration': 0,
        'trap_type': '', 'target_price': 0, 'drop_rate': 0, 'surge_rate': 0
    }

def _finalize_price(state, S):
    """최종 가격을 결정하고 히스토리에 기록"""
    # 절대 가격 제한
    S = max(10, min(S, state['long_term_mean'] * 4))
    S = round(S, 2)
    state['price_history'].append(S)
    return S

def _adjust_long_term_trend(state):
    """8시간마다 주식의 장기 평균을 미세하게 조정"""
    global MU
    # 전역 시장 트렌드 변경
    MU += random.uniform(-0.00001, 0.00001)
    
    # 개별 종목의 장기 평균(가치) 변경
    shift = random.uniform(-0.01, 0.01) # 최대 1% 내외로 변화
    state['long_term_mean'] *= (1 + shift)
    #print(f"[{state['name']}] ๘ 장기 가치 변동: {shift*100:+.2f}%. 새로운 가치: {state['long_term_mean']:.2f}")

def brownian_motion(S0, company_name=None):
    """
    외부 호출을 위한 진입점.
    새로운 시뮬레이션 함수를 호출합니다.
    """
    if company_name is None:
        company_name = 'default_stock'
    return generate_next_price(company_name, S0)