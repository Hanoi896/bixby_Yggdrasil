# Hugin-Munin Sync Server

## Setup

```bash
# Install dependencies
go mod download

# Run server
go run main.go

# Or build
go build -o hugin-munin-server
./hugin-munin-server
```

## Configuration

Environment variables:
- `PORT`: Server port (default: 8080)
- `DB_PATH`: SQLite database path (default: ./hugin-munin.db)

## API Endpoints

### Health Check
```
GET /api/v1/health
```

### Register Device
```
POST /api/v1/devices/register
{
  "user_id": "user123",
  "device_id": "device456",
  "device_name": "My Phone",
  "public_key": "base64_encoded_key"
}
```

### Upload Data
```
POST /api/v1/sync/upload
{
  "user_id": "user123",
  "device_id": "device456",
  "data_type": "permissions",
  "encrypted_blob": "base64_encrypted_data",
  "signature": "ed25519_signature"
}
```

### Download Data
```
POST /api/v1/sync/download
{
  "user_id": "user123",
  "data_type": "permissions",
  "since": 1234567890
}
```

### List Devices
```
GET /api/v1/devices?user_id=user123
```

## Security

- All data is stored encrypted (E2E)
- Ed25519 signatures verify data integrity
- No plaintext data on server
- CORS enabled for web dashboard
