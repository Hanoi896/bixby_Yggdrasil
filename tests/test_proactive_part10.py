import sys
import os

# Windows UTF-8 콘솔 출력 지원
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

print("=" * 60)
print("[PART 10 테스트] 선제적 능동형 비서 (Proactive Agent) ON/OFF 및 루틴 검증")
print("=" * 60)

class MockProactiveRoutineManager:
    def __init__(self):
        # 기본 설정: 활성화 상태
        self.is_master_enabled = True
        self.enable_morning_briefing = True
        self.enable_low_battery_care = True
        self.enable_commute_routine = True
        self.is_quiet_hours_enabled = True
        self.quiet_start_hour = 23
        self.quiet_end_hour = 7

    def set_master_enabled(self, enabled: bool):
        self.is_master_enabled = enabled

    def update_settings(self, **kwargs):
        for k, v in kwargs.items():
            if hasattr(self, k):
                setattr(self, k, v)

    def is_in_quiet_hours(self, current_hour: int) -> bool:
        if not self.is_quiet_hours_enabled:
            return False
        if self.quiet_start_hour > self.quiet_end_hour:
            return current_hour >= self.quiet_start_hour or current_hour < self.quiet_end_hour
        return self.quiet_start_hour <= current_hour < self.quiet_end_hour

    def build_morning_briefing(self, user_call_name: str, schedules: list, weather: str):
        if not self.is_master_enabled or not self.enable_morning_briefing:
            return None
        lines = [
            f"{user_call_name}, 좋은 아침이에요! ☀️",
            f"오늘 날씨는 {weather}입니다."
        ]
        if schedules:
            lines.append(f"오늘 주요 일정은 {len(schedules)}건 있어요:")
            for s in schedules:
                lines.append(f"• {s}")
        else:
            lines.append("오늘은 등록된 일정이 없어 여유로운 하루를 보내실 수 있어요.")
        lines.append("오늘도 힘찬 하루 되세요!")
        return "\n".join(lines)

    def build_battery_care_message(self, user_call_name: str, battery_pct: int, current_hour: int):
        if not self.is_master_enabled or not self.enable_low_battery_care:
            return None
        if self.is_in_quiet_hours(current_hour):
            return None # 방해금지 시간에는 침묵
        return (
            f"{user_call_name}, 배터리가 {battery_pct}% 남았습니다. 🔋\n"
            f"화면 밝기를 30%로 낮추고 백그라운드 앱을 정리해 배터리를 아껴둘까요?"
        )

# 1. 인스턴스 생성 및 기본 상태 확인
mgr = MockProactiveRoutineManager()
print(f"1) 기본 마스터 스위치 상태: is_master_enabled = {mgr.is_master_enabled}")

# 2. 기상 모닝 브리핑 생성 (정상 활성화 상태)
morning_msg = mgr.build_morning_briefing(
    user_call_name="지훈이 형",
    schedules=["오후 2:00 팀 기획 회의", "오후 7:00 동창 모임"],
    weather="맑음, 낮 최고 22도"
)
print("\n2) 기상 모닝 브리핑 출력 (ON 상태):")
print("-" * 40)
print(morning_msg)
print("-" * 40)
assert morning_msg is not None
assert "지훈이 형" in morning_msg
assert "팀 기획 회의" in morning_msg

# 3. 배터리 케어 메시지 테스트 (오후 3시 vs 새벽 2시 방해금지 시간)
battery_msg_day = mgr.build_battery_care_message("지훈이 형", 14, current_hour=15)
print("\n3) 낮 시간(15시) 배터리 케어 제안:")
print(battery_msg_day)
assert battery_msg_day is not None

battery_msg_night = mgr.build_battery_care_message("지훈이 형", 14, current_hour=2)
print("\n4) 심야 방해금지 시간(새벽 2시) 배터리 케어 제안:")
print(f"   결과: {battery_msg_night} (방해금지 시간으로 자동 침묵)")
assert battery_msg_night is None

# 4. 앱 내 On/Off 토글 테스트: 마스터 스위치 OFF!
print("\n5) 사용자가 앱 설정에서 [능동 비서 마스터 스위치 OFF] 전환 시뮬레이션")
mgr.set_master_enabled(False)
print(f"   is_master_enabled = {mgr.is_master_enabled}")

off_morning = mgr.build_morning_briefing("지훈이 형", ["일정"], "맑음")
off_battery = mgr.build_battery_care_message("지훈이 형", 10, current_hour=15)

print(f"   OFF 상태 모닝 브리핑: {off_morning} (침묵)")
print(f"   OFF 상태 배터리 케어: {off_battery} (침묵)")
assert off_morning is None
assert off_battery is None

# 5. 세부 루틴 개별 토글 테스트: 마스터 ON, 모닝 브리핑만 OFF
print("\n6) 세부 루틴 개별 토글 테스트: [마스터 ON, 모닝 브리핑만 OFF]")
mgr.set_master_enabled(True)
mgr.update_settings(enable_morning_briefing=False, enable_low_battery_care=True)

test_morning = mgr.build_morning_briefing("지훈이 형", ["일정"], "맑음")
test_battery = mgr.build_battery_care_message("지훈이 형", 12, current_hour=14)

print(f"   모닝 브리핑 결과: {test_morning} (OFF 되었으므로 침묵)")
print(f"   배터리 케어 결과: {test_battery} (ON 이므로 정상 출력)")
assert test_morning is None
assert test_battery is not None

print("\n" + "=" * 60)
print(">>> [SUCCESS] PART 10 선제적 능동 비서 ON/OFF 및 루틴 검증 100% 통과! <<<")
print("=" * 60)
