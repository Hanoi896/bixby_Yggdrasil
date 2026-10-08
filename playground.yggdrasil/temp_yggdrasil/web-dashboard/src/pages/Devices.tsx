import React, { useState, useEffect } from 'react'
import { Smartphone, Tablet, Laptop, Watch, Circle } from 'lucide-react'

interface Device {
    id: number
    user_id: string
    device_id: string
    device_name: string
    last_seen: string
}

const Devices: React.FC = () => {
    const [devices, setDevices] = useState<Device[]>([])
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        // Mock data - in production, fetch from API
        setTimeout(() => {
            setDevices([
                { id: 1, user_id: 'user123', device_id: 'dev1', device_name: 'Pixel 8 Pro', last_seen: new Date(Date.now() - 120000).toISOString() },
                { id: 2, user_id: 'user123', device_id: 'dev2', device_name: 'MacBook Pro', last_seen: new Date(Date.now() - 900000).toISOString() },
                { id: 3, user_id: 'user123', device_id: 'dev3', device_name: 'iPad Air', last_seen: new Date(Date.now() - 3600000).toISOString() },
                { id: 4, user_id: 'user456', device_id: 'dev4', device_name: 'Galaxy Watch', last_seen: new Date(Date.now() - 7200000).toISOString() },
            ])
            setLoading(false)
        }, 500)
    }, [])

    const getDeviceIcon = (name: string) => {
        if (name.toLowerCase().includes('phone') || name.toLowerCase().includes('pixel') || name.toLowerCase().includes('galaxy')) {
            return <Smartphone size={20} />
        }
        if (name.toLowerCase().includes('ipad') || name.toLowerCase().includes('tablet')) {
            return <Tablet size={20} />
        }
        if (name.toLowerCase().includes('macbook') || name.toLowerCase().includes('laptop')) {
            return <Laptop size={20} />
        }
        if (name.toLowerCase().includes('watch')) {
            return <Watch size={20} />
        }
        return <Smartphone size={20} />
    }

    const getStatusColor = (lastSeen: string) => {
        const minutesAgo = (Date.now() - new Date(lastSeen).getTime()) / 60000
        if (minutesAgo < 5) return 'text-green-500'
        if (minutesAgo < 60) return 'text-yellow-500'
        return 'text-red-500'
    }

    const formatLastSeen = (timestamp: string) => {
        const minutesAgo = Math.floor((Date.now() - new Date(timestamp).getTime()) / 60000)
        if (minutesAgo < 1) return 'Just now'
        if (minutesAgo < 60) return `${minutesAgo}m ago`
        const hoursAgo = Math.floor(minutesAgo / 60)
        if (hoursAgo < 24) return `${hoursAgo}h ago`
        const daysAgo = Math.floor(hoursAgo / 24)
        return `${daysAgo}d ago`
    }

    if (loading) {
        return (
            <div className="p-8">
                <div className="animate-pulse">
                    <div className="h-8 bg-gray-700 rounded w-1/4 mb-4"></div>
                    <div className="space-y-4">
                        {[1, 2, 3].map(i => (
                            <div key={i} className="h-20 bg-gray-700 rounded"></div>
                        ))}
                    </div>
                </div>
            </div>
        )
    }

    return (
        <div className="p-8">
            <div className="mb-8">
                <h1 className="text-3xl font-bold mb-2">Devices</h1>
                <p className="text-gray-400">Manage registered devices across all users</p>
            </div>

            <div className="bg-gray-800 rounded-lg border border-gray-700 overflow-hidden">
                <table className="w-full">
                    <thead>
                        <tr className="border-b border-gray-700">
                            <th className="text-left p-4 font-semibold text-gray-300">Device</th>
                            <th className="text-left p-4 font-semibold text-gray-300">User ID</th>
                            <th className="text-left p-4 font-semibold text-gray-300">Device ID</th>
                            <th className="text-left p-4 font-semibold text-gray-300">Status</th>
                            <th className="text-left p-4 font-semibold text-gray-300">Last Seen</th>
                        </tr>
                    </thead>
                    <tbody>
                        {devices.map(device => (
                            <tr key={device.id} className="border-b border-gray-700 hover:bg-gray-750 transition-colors">
                                <td className="p-4">
                                    <div className="flex items-center gap-3">
                                        <div className="p-2 bg-gray-700 rounded">
                                            {getDeviceIcon(device.device_name)}
                                        </div>
                                        <span className="font-medium">{device.device_name}</span>
                                    </div>
                                </td>
                                <td className="p-4 text-gray-400">{device.user_id}</td>
                                <td className="p-4 text-gray-400 font-mono text-sm">{device.device_id}</td>
                                <td className="p-4">
                                    <div className="flex items-center gap-2">
                                        <Circle className={`${getStatusColor(device.last_seen)} fill-current`} size={8} />
                                        <span className={getStatusColor(device.last_seen)}>
                                            {(Date.now() - new Date(device.last_seen).getTime()) / 60000 < 5 ? 'Online' : 'Offline'}
                                        </span>
                                    </div>
                                </td>
                                <td className="p-4 text-gray-400">{formatLastSeen(device.last_seen)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    )
}

export default Devices
