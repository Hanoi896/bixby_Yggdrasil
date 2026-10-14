import React, { useState } from 'react'
import { User, Bell, Lock, Database, AlertOctagon, Check, Trash2, ShieldCheck, ChevronRight } from 'lucide-react'

export default function Settings() {
    // 1. 개인화 페르소나
    const [userCallName, setUserCallName] = useState('지훈이 형')
    const [aiName, setAiName] = useState('이그드라실')
    const [tone, setTone] = useState<'secretary' | 'friend' | 'witty'>('friend')

    // 2. 선제적 능동 비서 루틴
    const [isProactiveMaster, setIsProactiveMaster] = useState(true)
    const [enableMorning, setEnableMorning] = useState(true)
    const [enableBatteryCare, setEnableBatteryCare] = useState(true)
    const [enableCommute, setEnableCommute] = useState(true)

    // 3. 프라이버시 시크릿 모드
    const [isIncognito, setIsIncognito] = useState(false)

    // 4. 스토리지 캐시 상태
    const [cleaned, setCleaned] = useState(false)
    const [savedNotification, setSavedNotification] = useState(false)

    // 5. 권한 상태 (Black & White)
    const permissions = [
        { name: '접근성 서비스 (화면 조작)', desc: 'AI 화면 자동 터치/타이핑', granted: true, critical: true },
        { name: '알림 접근 허용 (후킹)', desc: '카카오톡, 문자, 중요 메일 수신 요약', granted: true, critical: true },
        { name: '배터리 최적화 예외', desc: '절전 모드 시 백그라운드 AI 프로세스 지속', granted: true, critical: true },
        { name: '다른 앱 위에 표시', desc: '긴급 오버레이 중단 버튼 표시', granted: true, critical: true },
        { name: '캘린더 읽기/쓰기', desc: '구글 캘린더 일정 조회 및 등록', granted: true, critical: false },
        { name: '연락처(주소록) 검색', desc: '상대방 전화번호 및 연락처 조회', granted: true, critical: false },
    ]

    const handleSave = () => {
        setSavedNotification(true)
        setTimeout(() => setSavedNotification(false), 2500)
    }

    const handleCleanCache = () => {
        setCleaned(true)
        setTimeout(() => setCleaned(false), 3000)
    }

    return (
        <div className="min-h-screen bg-black text-white p-8 max-w-4xl mx-auto space-y-6">
            <div className="flex items-center justify-between pb-4 border-b border-neutral-800">
                <div>
                    <h1 className="text-2xl font-bold tracking-tight">설정</h1>
                    <p className="text-neutral-500 text-xs mt-1">온디바이스 시스템 제어 및 개인화 관리</p>
                </div>
                <button
                    onClick={handleSave}
                    className="flex items-center gap-2 bg-white hover:bg-neutral-200 text-black px-4 py-2 rounded font-semibold text-sm transition"
                >
                    <Check size={16} />
                    설정 저장
                </button>
            </div>

            {savedNotification && (
                <div className="bg-neutral-900 border border-neutral-700 text-white px-4 py-3 rounded text-sm flex items-center gap-2">
                    <Check size={16} className="text-white" />
                    기기 내부 보안 볼트에 성공적으로 저장되었습니다.
                </div>
            )}

            {/* 카드 1: 필수 권한 허용 상태 (신규) */}
            <div className="bg-neutral-950 border border-neutral-800 rounded-lg p-6 space-y-4">
                <div className="flex items-center justify-between">
                    <h2 className="text-white font-semibold text-base flex items-center gap-2">
                        <ShieldCheck size={18} className="text-white" />
                        스마트폰 필수 권한 허용 상태
                    </h2>
                    <span className="text-xs text-neutral-400">6개 모두 정상 가동 중</span>
                </div>
                <div className="divide-y divide-neutral-900 pt-1">
                    {permissions.map((p, idx) => (
                        <div key={idx} className="py-2.5 flex items-center justify-between text-xs">
                            <div>
                                <div className="text-white font-medium flex items-center gap-1.5">
                                    {p.name}
                                    {p.critical && (
                                        <span className="bg-white text-black text-[10px] font-bold px-1.5 py-0.5 rounded">
                                            필수
                                        </span>
                                    )}
                                </div>
                                <div className="text-neutral-500 mt-0.5">{p.desc}</div>
                            </div>
                            <span className="text-neutral-400 font-medium">허용됨</span>
                        </div>
                    ))}
                </div>
            </div>

            {/* 카드 2: 페르소나 설정 */}
            <div className="bg-neutral-950 border border-neutral-800 rounded-lg p-6 space-y-4">
                <h2 className="text-white font-semibold text-base flex items-center gap-2">
                    <User size={18} className="text-white" />
                    내 맞춤 페르소나
                </h2>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-neutral-500 text-xs mb-1">사용자 호칭</label>
                        <input
                            type="text"
                            value={userCallName}
                            onChange={(e) => setUserCallName(e.target.value)}
                            className="w-full bg-black border border-neutral-800 rounded px-3 py-2 text-white text-sm focus:outline-none focus:border-white"
                        />
                    </div>
                    <div>
                        <label className="block text-neutral-500 text-xs mb-1">AI 이름</label>
                        <input
                            type="text"
                            value={aiName}
                            onChange={(e) => setAiName(e.target.value)}
                            className="w-full bg-black border border-neutral-800 rounded px-3 py-2 text-white text-sm focus:outline-none focus:border-white"
                        />
                    </div>
                </div>

                <div className="pt-2">
                    <label className="block text-neutral-500 text-xs mb-2">어투(말투)</label>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                        {[
                            { id: 'secretary', title: '정중한 비서 모드', desc: '격식 있는 경어체 (~하셨습니다)' },
                            { id: 'friend', title: '친근한 친구 모드', desc: '다정한 반말 어투 (~했어?, ~해둘게!)' },
                            { id: 'witty', title: '위트 있는 조수 모드', desc: '재치와 유머가 섞인 스마트한 말투' }
                        ].map((item) => (
                            <div
                                key={item.id}
                                onClick={() => setTone(item.id as any)}
                                className={`cursor-pointer p-3 rounded border transition ${
                                    tone === item.id
                                        ? 'bg-neutral-900 border-white text-white'
                                        : 'bg-black border-neutral-800 text-neutral-500 hover:border-neutral-700'
                                }`}
                            >
                                <div className="font-semibold text-xs">{item.title}</div>
                                <div className="text-[11px] text-neutral-500 mt-1">{item.desc}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* 카드 3: 선제적 능동 비서 */}
            <div className="bg-neutral-950 border border-neutral-800 rounded-lg p-6 space-y-4">
                <div className="flex items-center justify-between">
                    <h2 className="text-white font-semibold text-base flex items-center gap-2">
                        <Bell size={18} className="text-white" />
                        선제적 능동 비서
                    </h2>
                    <input
                        type="checkbox"
                        checked={isProactiveMaster}
                        onChange={(e) => setIsProactiveMaster(e.target.checked)}
                        className="w-4 h-4 accent-white cursor-pointer"
                    />
                </div>

                {isProactiveMaster && (
                    <div className="divide-y divide-neutral-900 pt-2 space-y-2 text-xs">
                        <div className="flex items-center justify-between pt-2">
                            <div>
                                <div className="text-white font-medium">기상 모닝 브리핑</div>
                                <div className="text-neutral-500">아침 알람 해제 시 일정 및 날씨 요약</div>
                            </div>
                            <input
                                type="checkbox"
                                checked={enableMorning}
                                onChange={(e) => setEnableMorning(e.target.checked)}
                                className="w-4 h-4 accent-white"
                            />
                        </div>
                        <div className="flex items-center justify-between pt-2">
                            <div>
                                <div className="text-white font-medium">배터리 긴급 케어</div>
                                <div className="text-neutral-500">15% 이하 시 절전 모드 제안</div>
                            </div>
                            <input
                                type="checkbox"
                                checked={enableBatteryCare}
                                onChange={(e) => setEnableBatteryCare(e.target.checked)}
                                className="w-4 h-4 accent-white"
                            />
                        </div>
                        <div className="flex items-center justify-between pt-2">
                            <div>
                                <div className="text-white font-medium">퇴근/귀가 케어</div>
                                <div className="text-neutral-500">귀가 시 조명과 에어컨 가동 제안</div>
                            </div>
                            <input
                                type="checkbox"
                                checked={enableCommute}
                                onChange={(e) => setEnableCommute(e.target.checked)}
                                className="w-4 h-4 accent-white"
                            />
                        </div>
                    </div>
                )}
            </div>

            {/* 카드 4: 프라이버시 */}
            <div className="bg-neutral-950 border border-neutral-800 rounded-lg p-6 space-y-3">
                <div className="flex items-center justify-between">
                    <h2 className="text-white font-semibold text-base flex items-center gap-2">
                        <Lock size={18} className="text-white" />
                        온디바이스 프라이버시
                    </h2>
                    <input
                        type="checkbox"
                        checked={isIncognito}
                        onChange={(e) => setIsIncognito(e.target.checked)}
                        className="w-4 h-4 accent-white"
                    />
                </div>
                <p className="text-neutral-500 text-xs">
                    학습 일시정지(시크릿 모드): 켜져 있는 동안 대화는 DB에 저장되지 않고 휘발성 메모리에서 즉시 소멸됩니다.
                </p>
                <div className="text-neutral-400 text-xs border-t border-neutral-900 pt-2">
                    • 성생활/의료/금융 정보는 스위치 상태와 무관하게 24시간 자동 영구 격리 차단됩니다.
                </div>
            </div>

            {/* 카드 5: 스토리지 볼트 */}
            <div className="bg-neutral-950 border border-neutral-800 rounded-lg p-6 space-y-4">
                <h2 className="text-white font-semibold text-base flex items-center gap-2">
                    <Database size={18} className="text-white" />
                    격리 스토리지 볼트
                </h2>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center text-xs">
                    <div className="bg-black p-3 rounded border border-neutral-800">
                        <div className="text-neutral-500">NPU 모델</div>
                        <div className="text-base font-bold text-white mt-1">2.17 GB</div>
                    </div>
                    <div className="bg-black p-3 rounded border border-neutral-800">
                        <div className="text-neutral-500">암호화 DB</div>
                        <div className="text-base font-bold text-white mt-1">128 KB</div>
                    </div>
                    <div className="bg-black p-3 rounded border border-neutral-800">
                        <div className="text-neutral-500">캡처 캐시</div>
                        <div className="text-base font-bold text-white mt-1">{cleaned ? '0 KB' : '1.4 MB'}</div>
                    </div>
                    <div className="bg-black p-3 rounded border border-neutral-800">
                        <div className="text-neutral-500">ZIP 캐시</div>
                        <div className="text-base font-bold text-white mt-1">{cleaned ? '0 KB' : '4.8 MB'}</div>
                    </div>
                </div>

                <button
                    onClick={handleCleanCache}
                    className="w-full bg-white hover:bg-neutral-200 text-black py-2 rounded text-xs font-bold transition flex items-center justify-center gap-2"
                >
                    <Trash2 size={14} />
                    {cleaned ? '만료된 임시 캐시가 완전히 비워졌습니다' : '만료된 임시 캐시 즉시 비우기 (TTL GC)'}
                </button>
            </div>

            {/* 카드 6: 비상 방어 프로토콜 */}
            <div className="border border-neutral-700 bg-neutral-950 rounded-lg p-5 flex items-center justify-between">
                <div>
                    <h3 className="text-white font-bold text-sm flex items-center gap-2">
                        <AlertOctagon size={16} />
                        비상 방어 프로토콜 (Break-Glass)
                    </h3>
                    <p className="text-neutral-500 text-xs mt-0.5">이상 행동 감지 시 모든 권한을 원클릭으로 차단하고 L0 읽기 전용 상태로 잠급니다.</p>
                </div>
                <button
                    onClick={() => alert('🚨 비상 차단 모드가 가동되었습니다.')}
                    className="border border-neutral-500 hover:bg-neutral-900 text-white px-3 py-1.5 rounded text-xs font-bold transition"
                >
                    긴급 차단 실행
                </button>
            </div>
        </div>
    )
}
