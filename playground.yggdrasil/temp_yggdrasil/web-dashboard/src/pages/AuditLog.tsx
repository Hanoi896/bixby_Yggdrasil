import React from 'react'
import { AlertCircle, CheckCircle, Info, XCircle } from 'lucide-react'

interface AuditEntry {
    id: number
    timestamp: string
    user_id: string
    action: string
    level: string
    details: string
    status: 'success' | 'error' | 'warning' | 'info'
}

const AuditLog: React.FC = () => {
    // Mock audit log data
    const auditEntries: AuditEntry[] = [
        {
            id: 1,
            timestamp: new Date(Date.now() - 120000).toISOString(),
            user_id: 'user123',
            action: 'Permission Granted',
            level: 'L5',
            details: 'Payment permission granted for device Pixel 8 Pro',
            status: 'success',
        },
        {
            id: 2,
            timestamp: new Date(Date.now() - 900000).toISOString(),
            user_id: 'user456',
            action: 'Break-Glass Activated',
            level: 'L10',
            details: 'Emergency access initiated, 3 trustees notified',
            status: 'warning',
        },
        {
            id: 3,
            timestamp: new Date(Date.now() - 1800000).toISOString(),
            user_id: 'user123',
            action: 'Data Sync',
            level: 'L0',
            details: 'Encrypted blob synced: permissions (245 KB)',
            status: 'info',
        },
        {
            id: 4,
            timestamp: new Date(Date.now() - 3600000).toISOString(),
            user_id: 'user789',
            action: 'Authentication Failed',
            level: 'L0',
            details: 'Invalid signature from device MacBook Pro',
            status: 'error',
        },
    ]

    const getStatusIcon = (status: string) => {
        switch (status) {
            case 'success':
                return <CheckCircle className="text-green-500" size={20} />
            case 'error':
                return <XCircle className="text-red-500" size={20} />
            case 'warning':
                return <AlertCircle className="text-yellow-500" size={20} />
            case 'info':
            default:
                return <Info className="text-blue-500" size={20} />
        }
    }

    const getStatusBadge = (level: string) => {
        const levelNum = parseInt(level.replace('L', ''))
        if (levelNum >= 7) return 'bg-red-500/20 text-red-400 border-red-500/50'
        if (levelNum >= 5) return 'bg-yellow-500/20 text-yellow-400 border-yellow-500/50'
        if (levelNum >= 3) return 'bg-blue-500/20 text-blue-400 border-blue-500/50'
        return 'bg-gray-500/20 text-gray-400 border-gray-500/50'
    }

    return (
        <div className="p-8">
            <div className="mb-8">
                <h1 className="text-3xl font-bold mb-2">Audit Log</h1>
                <p className="text-gray-400">Complete audit trail of all system actions</p>
            </div>

            <div className="space-y-4">
                {auditEntries.map(entry => (
                    <div
                        key={entry.id}
                        className="bg-gray-800 border border-gray-700 rounded-lg p-4 hover:border-gray-600 transition-colors"
                    >
                        <div className="flex items-start gap-4">
                            <div className="mt-1">{getStatusIcon(entry.status)}</div>

                            <div className="flex-1">
                                <div className="flex items-center gap-3 mb-2">
                                    <h3 className="font-semibold text-lg">{entry.action}</h3>
                                    <span className={`px-2 py-1 text-xs font-medium rounded border ${getStatusBadge(entry.level)}`}>
                                        {entry.level}
                                    </span>
                                </div>

                                <p className="text-gray-300 mb-2">{entry.details}</p>

                                <div className="flex items-center gap-4 text-sm text-gray-400">
                                    <span>User: {entry.user_id}</span>
                                    <span>•</span>
                                    <span>{new Date(entry.timestamp).toLocaleString()}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* Pagination */}
            <div className="mt-8 flex justify-center gap-2">
                <button className="px-4 py-2 bg-gray-800 border border-gray-700 rounded hover:bg-gray-700 transition-colors">
                    Previous
                </button>
                <button className="px-4 py-2 bg-primary-600 border border-primary-600 rounded">
                    1
                </button>
                <button className="px-4 py-2 bg-gray-800 border border-gray-700 rounded hover:bg-gray-700 transition-colors">
                    2
                </button>
                <button className="px-4 py-2 bg-gray-800 border border-gray-700 rounded hover:bg-gray-700 transition-colors">
                    3
                </button>
                <button className="px-4 py-2 bg-gray-800 border border-gray-700 rounded hover:bg-gray-700 transition-colors">
                    Next
                </button>
            </div>
        </div>
    )
}

export default AuditLog
