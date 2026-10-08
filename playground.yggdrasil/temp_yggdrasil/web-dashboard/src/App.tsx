import React, { useState } from 'react'
import Dashboard from './pages/Dashboard'
import Devices from './pages/Devices'
import AuditLog from './pages/AuditLog'
import { Menu } from 'lucide-react'
import './index.css'

function App() {
    const [currentPage, setCurrentPage] = useState('dashboard')
    const [sidebarOpen, setSidebarOpen] = useState(true)

    const navigation = [
        { id: 'dashboard', name: 'Dashboard', icon: '📊' },
        { id: 'devices', name: 'Devices', icon: '📱' },
        { id: 'audit', name: 'Audit Log', icon: '📋' },
    ]

    const renderPage = () => {
        switch (currentPage) {
            case 'dashboard':
                return <Dashboard />
            case 'devices':
                return <Devices />
            case 'audit':
                return <AuditLog />
            default:
                return <Dashboard />
        }
    }

    return (
        <div className="flex h-screen bg-gray-900 text-white">
            {/* Sidebar */}
            <div className={`${sidebarOpen ? 'w-64' : 'w-20'} bg-gray-800 transition-all duration-300`}>
                <div className="p-4">
                    <div className="flex items-center justify-between mb-8">
                        <h1 className={`text-xl font-bold ${!sidebarOpen && 'hidden'}`}>
                            Hugin-Munin
                        </h1>
                        <button
                            onClick={() => setSidebarOpen(!sidebarOpen)}
                            className="p-2 rounded hover:bg-gray-700"
                        >
                            <Menu size={20} />
                        </button>
                    </div>

                    <nav>
                        {navigation.map((item) => (
                            <button
                                key={item.id}
                                onClick={() => setCurrentPage(item.id)}
                                className={`w-full flex items-center gap-3 p-3 rounded mb-2 transition-colors ${currentPage === item.id
                                        ? 'bg-primary-600 text-white'
                                        : 'hover:bg-gray-700'
                                    }`}
                            >
                                <span className="text-xl">{item.icon}</span>
                                <span className={!sidebarOpen ? 'hidden' : ''}>{item.name}</span>
                            </button>
                        ))}
                    </nav>
                </div>
            </div>

            {/* Main Content */}
            <div className="flex-1 overflow-auto">
                {renderPage()}
            </div>
        </div>
    )
}

export default App
