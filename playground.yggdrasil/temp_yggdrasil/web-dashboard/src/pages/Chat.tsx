import React, { useState, useRef, useEffect } from 'react'
import { ArrowUp, Sparkles, Send } from 'lucide-react'

interface Message {
    id: string
    sender: 'user' | 'assistant'
    text: string
    timestamp: string
}

export default function Chat() {
    const [messages, setMessages] = useState<Message[]>([
        {
            id: '1',
            sender: 'assistant',
            text: '반갑습니다! 무엇을 도와드릴까요? 오늘 일정 확인, 파일 압축, 화면 제어 등 편하게 말씀해 주세요.',
            timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }
    ])
    const [input, setInput] = useState('')
    const [isThinking, setIsThinking] = useState(false)
    const messagesEndRef = useRef<HTMLDivElement>(null)

    const quickChips = [
        '오늘 주요 일정 알려줘',
        '배터리 절전 모드 켜줘',
        '다운로드 폴더 ZIP 압축해줘',
        '화면 정리하고 홈으로 가줘'
    ]

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }

    useEffect(() => {
        scrollToBottom()
    }, [messages])

    const handleSend = async (textToSend?: string) => {
        const query = (textToSend || input).trim()
        if (!query || isThinking) return

        const userMsg: Message = {
            id: Date.now().toString(),
            sender: 'user',
            text: query,
            timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }

        setMessages((prev) => [...prev, userMsg])
        setInput('')
        setIsThinking(true)

        // 로컬 NPU 추론 시뮬레이션
        setTimeout(() => {
            let reply = `온디바이스 EXAONE 3.0에서 '${query}' 요청을 로컬 보안 환경에서 안전하게 처리했습니다.`
            if (query.includes('일정')) {
                reply = '오늘 구글 캘린더에 오후 2:00 팀 미팅, 오후 6:30 저녁 약속이 등록되어 있습니다.'
            } else if (query.includes('배터리')) {
                reply = '배터리 케어가 가동되었습니다. 화면 밝기를 30%로 낮추고 백그라운드 프로세스를 정리했습니다.'
            } else if (query.includes('압축')) {
                reply = '다운로드 폴더의 파일 3개를 /cache/archives/download_backup.zip 으로 고속 압축 완료했습니다.'
            }

            const aiMsg: Message = {
                id: (Date.now() + 1).toString(),
                sender: 'assistant',
                text: reply,
                timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            }
            setMessages((prev) => [...prev, aiMsg])
            setIsThinking(false)
        }, 350)
    }

    return (
        <div className="flex flex-col h-full bg-black text-white">
            {/* 상단 헤더 */}
            <div className="p-4 border-b border-neutral-800 flex items-center justify-between">
                <div>
                    <h1 className="text-base font-bold tracking-tight">이그드라실 대화</h1>
                    <p className="text-[11px] text-neutral-500">LG EXAONE 3.0 • 100% 온디바이스 NPU 연산</p>
                </div>
                <span className="text-[10px] bg-neutral-900 border border-neutral-700 text-neutral-400 px-2 py-0.5 rounded">
                    L4 로컬 보안 가동 중
                </span>
            </div>

            {/* 메시지 리스트 */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4 max-w-3xl mx-auto w-full">
                {messages.map((m) => (
                    <div
                        key={m.id}
                        className={`flex flex-col ${m.sender === 'user' ? 'items-end' : 'items-start'}`}
                    >
                        {m.sender === 'assistant' && (
                            <span className="text-[11px] text-neutral-500 mb-1 ml-1">이그드라실</span>
                        )}
                        <div
                            className={`max-w-[75%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed ${
                                m.sender === 'user'
                                    ? 'bg-white text-black font-normal rounded-br-sm'
                                    : 'bg-neutral-900 text-white border border-neutral-800 rounded-bl-sm'
                            }`}
                        >
                            {m.text}
                        </div>
                        <span className="text-[10px] text-neutral-600 mt-1 px-1">{m.timestamp}</span>
                    </div>
                ))}

                {isThinking && (
                    <div className="flex items-center gap-2 text-xs text-neutral-500 ml-1">
                        <Sparkles size={14} className="animate-pulse text-white" />
                        AI가 응답을 생성하고 있습니다...
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>

            {/* 퀵 칩 */}
            <div className="max-w-3xl mx-auto w-full px-4 py-2 flex items-center gap-2 overflow-x-auto">
                {quickChips.map((chip, idx) => (
                    <button
                        key={idx}
                        onClick={() => handleSend(chip)}
                        className="text-xs bg-neutral-950 border border-neutral-800 hover:border-neutral-700 text-neutral-300 px-3 py-1.5 rounded-full whitespace-nowrap transition"
                    >
                        {chip}
                    </button>
                ))}
            </div>

            {/* 하단 입력 바 */}
            <div className="p-4 border-t border-neutral-900 max-w-3xl mx-auto w-full">
                <form
                    onSubmit={(e) => {
                        e.preventDefault()
                        handleSend()
                    }}
                    className="relative flex items-center"
                >
                    <input
                        type="text"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        placeholder="이그드라실에게 질문이나 작업을 지시하세요..."
                        className="w-full bg-neutral-950 border border-neutral-800 rounded-full pl-5 pr-12 py-3 text-sm text-white placeholder-neutral-600 focus:outline-none focus:border-neutral-500 transition"
                    />
                    <button
                        type="submit"
                        disabled={!input.trim() || isThinking}
                        className={`absolute right-2 p-2 rounded-full transition ${
                            input.trim() && !isThinking
                                ? 'bg-white text-black hover:bg-neutral-200'
                                : 'bg-neutral-800 text-neutral-600 cursor-not-allowed'
                        }`}
                    >
                        <ArrowUp size={16} />
                    </button>
                </form>
            </div>
        </div>
    )
}
