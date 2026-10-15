# Hugin-Munin Web Dashboard

## Setup

```bash
# Install dependencies
npm install

# Run development server
npm run dev

# Build for production
npm run build
```

## Features

- 📊 **Dashboard**: Overview of system stats, permission distribution, recent activity
- 📱 **Devices**: Manage registered devices across all users
- 📋 **Audit Log**: Complete audit trail with color-coded severity levels

## Technology Stack

- React 18 + TypeScript
- Vite (build tool)
- TailwindCSS (styling)
- Recharts (analytics)
- Lucide React (icons)

## Development

The dashboard connects to the backend API at `http://localhost:8080`.

Make sure the backend server is running before starting the dashboard.

## Production

```bash
npm run build
# Serve the `dist` folder with any static file server
```
