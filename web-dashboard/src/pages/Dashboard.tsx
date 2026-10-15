import React from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import { Activity, Users, Database, Shield } from 'lucide-react'

const Dashboard: React.FC = () => {
    // Mock data
    const stats = [
        { label: 'Active Devices', value: '12', icon: Activity, color: 'bg-blue-500' },
        { label: 'Total Users', value: '45', icon: Users, color: 'bg-green-500' },
        { label: 'Synced Data', value: '2.4 GB', icon: Database, color: 'bg-purple-500' },
        { label: 'Security Events', value: '3', icon: Shield, color: 'bg-yellow-500' },
    ]

    const permissionData = [
        { level: 'L0-L2', count: 35 },
        { level: 'L3-L4', count: 18 },
        { level: 'L5-L6', count: 7 },
        { level: 'L7-L10', count: 2 },
    ]

    return (
        <div className="p-8">
            <div className="mb-8">
                <h1 className="text-3xl font-bold mb-2">Dashboard</h1>
                <p className="text-gray-400">Monitor your Hugin-Munin system</p>
            </div>

            {/* Stats Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                {stats.map((stat, index) => {
                    const Icon = stat.icon
                    return (
                        <div key={index} className="bg-gray-800 rounded-lg p-6 border border-gray-700">
                            <div className="flex items-center justify-between mb-4">
                                <div className={`${stat.color} p-3 rounded-lg`}>
                                    <Icon size={24} />
                                </div>
                            </div>
                            <div className="text-3xl font-bold mb-1">{stat.value}</div>
                            <div className="text-sm text-gray-400">{stat.label}</div>
                        </div>
                    )
                })}
            </div>

            {/* Charts */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Permission Distribution */}
                <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
                    <h2 className="text-xl font-semibold mb-4">Permission Distribution</h2>
                    <ResponsiveContainer width="100%" height={300}>
                        <BarChart data={permissionData}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                            <XAxis dataKey="level" stroke="#9CA3AF" />
                            <YAxis stroke="#9CA3AF" />
                            <Tooltip
                                contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151' }}
                                labelStyle={{ color: '#F3F4F6' }}
                            />
                            <Legend />
                            <Bar dataKey="count" fill="#3B82F6" />
                        </BarChart>
                    </ResponsiveContainer>
                </div>

                {/* Recent Activity */}
                <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
                    <h2 className="text-xl font-semibold mb-4">Recent Activity</h2>
                    <div className="space-y-4">
                        {[
                            { action: 'Device registered', device: 'Pixel 8 Pro', time: '2 min ago' },
                            { action: 'Data synced', device: 'MacBook Pro', time: '15 min ago' },
                            { action: 'Permission granted', device: 'iPad Air', time: '1 hour ago' },
                            { action: 'Break-Glass activated', device: 'Galaxy S24', time: '2 hours ago' },
                        ].map((activity, index) => (
                            <div key={index} className="flex items-center justify-between p-3 bg-gray-700 rounded">
                                <div>
                                    <div className="font-medium">{activity.action}</div>
                                    <div className="text-sm text-gray-400">{activity.device}</div>
                                </div>
                                <div className="text-sm text-gray-400">{activity.time}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    )
}

export default Dashboard
