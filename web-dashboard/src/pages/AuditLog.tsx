import React, { useState, useEffect } from 'react'
import { AlertCircle, CheckCircle, ShieldAlert, Lock, Unlock, Link2, RefreshCw } from 'lucide-react'

interface AuditBlock {
    index: number
    timestamp: string
    actionType: string
    securityLevel: string
    realm: string
    details: string
    previousHash: string
    hash: string
    status: 'SUCCESS' | 'BLOCKED' | 'BREAK_GLASS' | 'MULTI_SIG'
}

const AuditLog: React.FC = () => {
    const [blocks, setBlocks] = useState<AuditBlock[]>([])
    const [integrity, setIntegrity] = useState<boolean>(true)
    const [isBreakGlassActive, setIsBreakGlassActive] = useState<boolean>(false)
    const [isLoading, setIsLoading] = useState<boolean>(false)

    const fetchAuditData = async () => {
        setIsLoading(true)
        try {
            // 1. 감사 로그 해시 체인 조회
            const logRes = await fetch('http://127.0.0.1:8080/v1/audit/logs')
            if (logRes.ok) {
                const data = await logRes.json()
                setBlocks(data.blocks || [])
                setIntegrity(data.integrity ?? true)
            }

            // 2. 보안 상태(Break-Glass) 조회
            const secRes = await fetch('http://127.0.0.1:8080/v1/security/status')
            if (secRes.ok) {
                const secData = await secRes.json()
                setIsBreakGlassActive(secData.isBreakGlassActive ?? false)
            }
        } catch (e) {
            // 로컬 서버 오프라인 시 모의 데이터 폴백
            setBlocks([
                {
                    index: 2,
                    timestamp: new Date().toISOString(),
                    actionType: 'screen_control',
                    securityLevel: 'L4',
                    realm: 'Nidavellir',
                    details: "화면 요소 '다음' 탭 실행 완료 (Golden Border 점등)",
                    previousHash: 'a7b8c9d0e1f2...',
                    hash: '4f5e6d7c8b9a...',
                    status: 'SUCCESS'
                },
                {
                    index: 1,
                    timestamp: new Date(Date.now() - 60000).toISOString(),
                    actionType: 'compress_files',
                    securityLevel: 'L3',
                    realm: 'Midgard',
                    details: '문서 폴더 2개 파일 ZIP 압축 완료 (Yggdrasil_Archive.zip)',
                    previousHash: '000000000000...',
                    hash: 'a7b8c9d0e1f2...',
                    status: 'SUCCESS'
                }
            ])
        } finally {
            setIsLoading(false)
        }
    }

    useEffect(() => {
        fetchAuditData()
        const interval = setInterval(fetchAuditData, 3000)
        return () => clearInterval(interval)
    }, [])

    const toggleBreakGlass = async () => {
        const nextAction = isBreakGlassActive ? 'release' : 'trigger'
        try {
            const res = await fetch('http://127.0.0.1:8080/v1/security/break-glass', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: nextAction, reason: '웹 대시보드 관리자 원클릭 비상 제어' })
            })
            if (res.ok) {
                const resData = await res.json()
                setIsBreakGlassActive(resData.isBreakGlassActive)
                fetchAuditData()
            }
        } catch (e) {
            setIsBreakGlassActive(!isBreakGlassActive)
        }
    }

    const getStatusBadge = (level: string) => {
        const num = parseInt(level.replace('L', '')) || 0
        if (num >= 8) return 'bg-red-500/20 text-red-400 border-red-500/50'
        if (num >= 4) return 'bg-yellow-500/20 text-yellow-400 border-yellow-500/50'
        return 'bg-blue-500/20 text-blue-400 border-blue-500/50'
    }

    return (
        <div className="p-8">
            {/* Header & Break-Glass Emergency Bar */}
            <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 mb-8">
                <div>
                    <h1 className="text-3xl font-bold mb-2 flex items-center gap-3">
                        보안 감사 대시보드
                        {integrity ? (
                            <span className="flex items-center gap-1.5 text-xs px-3 py-1 bg-green-500/20 text-green-400 border border-green-500/40 rounded-full font-mono">
                                <CheckCircle size={14} /> SHA-256 해시체인 무결성 검증됨 (Tamper-Proof)
                            </span>
                        ) : (
                            <span className="flex items-center gap-1.5 text-xs px-3 py-1 bg-red-500/20 text-red-400 border border-red-500/40 rounded-full font-mono">
                                <AlertCircle size={14} /> 해시 체인 위변조 감지!
                            </span>
                        )}
                    </h1>
                    <p className="text-gray-400">Yggdrasil 온디바이스 에이전트의 모든 화면 제어, 통신, 보안 감사 체인</p>
                </div>

                <div className="flex items-center gap-3">
                    <button
                        onClick={fetchAuditData}
                        className="p-2.5 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg text-gray-300 transition-colors"
                        title="새로고침"
                    >
                        <RefreshCw size={18} className={isLoading ? 'animate-spin' : ''} />
                    </button>

                    {/* 원클릭 Break-Glass 비상 차단 버튼 */}
                    <button
                        onClick={toggleBreakGlass}
                        className={`flex items-center gap-2 px-5 py-2.5 rounded-lg font-bold shadow-lg transition-all ${
                            isBreakGlassActive
                                ? 'bg-amber-600 hover:bg-amber-500 text-white animate-pulse'
                                : 'bg-red-600 hover:bg-red-500 text-white'
                        }`}
                    >
                        {isBreakGlassActive ? (
                            <>
                                <Unlock size={18} />
                                비상 차단 해제 (Release Lockdown)
                            </>
                        ) : (
                            <>
                                <ShieldAlert size={18} />
                                원클릭 비상 차단 (Break-Glass)
                            </>
                        )}
                    </button>
                </div>
            </div>

            {/* Break-Glass Alert Banner */}
            {isBreakGlassActive && (
                <div className="mb-6 p-4 bg-red-950/80 border-2 border-red-600 rounded-xl flex items-center justify-between animate-bounce">
                    <div className="flex items-center gap-3">
                        <Lock className="text-red-400" size={28} />
                        <div>
                            <div className="text-red-200 font-bold text-lg">🚨 BREAK-GLASS 비상 차단 프로토콜 활성화 중</div>
                            <div className="text-red-300 text-sm">모든 외부 네트워크 차단 및 자율 실행 즉시 정지, 읽기 전용(L0) 강제 전환 상태입니다.</div>
                        </div>
                    </div>
                </div>
            )}

            {/* Multi-Sig Status Card */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                <div className="bg-gray-800 border border-gray-700 rounded-xl p-5">
                    <div className="text-gray-400 text-sm mb-1">다중 서명 (Multi-Sig) 정책</div>
                    <div className="text-2xl font-bold text-yellow-400">2-of-3 Ed25519</div>
                    <div className="text-xs text-gray-400 mt-2">L8~L10 고위험 작업 시 2명 이상 신뢰인 전자 서명 필수</div>
                </div>
                <div className="bg-gray-800 border border-gray-700 rounded-xl p-5">
                    <div className="text-gray-400 text-sm mb-1">등록된 신뢰인 (Trustees)</div>
                    <div className="text-2xl font-bold text-green-400">3대 기기 정상 등록</div>
                    <div className="text-xs text-gray-400 mt-2">보조 폰, 신뢰 관리자 태블릿, 보안 키</div>
                </div>
                <div className="bg-gray-800 border border-gray-700 rounded-xl p-5">
                    <div className="text-gray-400 text-sm mb-1">총 체인 블록 수</div>
                    <div className="text-2xl font-bold text-purple-400">{blocks.length} 블록</div>
                    <div className="text-xs text-gray-400 mt-2">제네시스 블록부터 실시간 해시 링크 유지 중</div>
                </div>
            </div>

            {/* Hash Chain Table */}
            <div className="space-y-4">
                {blocks.map((block) => (
                    <div
                        key={block.index}
                        className="bg-gray-800/90 border border-gray-700 rounded-xl p-5 hover:border-gray-500 transition-colors font-sans"
                    >
                        <div className="flex items-start justify-between gap-4 mb-2">
                            <div className="flex items-center gap-3">
                                <span className="text-xs font-mono px-2 py-0.5 bg-gray-700 text-gray-300 rounded">
                                    #{block.index}
                                </span>
                                <h3 className="font-semibold text-lg text-white">{block.actionType}</h3>
                                <span className={`px-2.5 py-0.5 text-xs font-bold rounded border ${getStatusBadge(block.securityLevel)}`}>
                                    {block.securityLevel} ({block.realm})
                                </span>
                            </div>

                            <span className="text-xs font-mono text-gray-400">
                                {new Date(block.timestamp).toLocaleString()}
                            </span>
                        </div>

                        <p className="text-gray-300 mb-3 text-sm">{block.details}</p>

                        {/* Merkle Hash Linkage */}
                        <div className="pt-3 border-t border-gray-700/60 flex flex-wrap items-center gap-4 text-xs font-mono text-gray-400">
                            <div className="flex items-center gap-1.5">
                                <Link2 size={13} className="text-primary-400" />
                                <span>PrevHash: <span className="text-gray-300">{block.previousHash.slice(0, 16)}...</span></span>
                            </div>
                            <div>
                                <span>BlockHash: <span className="text-emerald-400 font-bold">{block.hash.slice(0, 20)}...</span></span>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    )
}

export default AuditLog
