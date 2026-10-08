package models

import "time"

type User struct {
	ID        int64     `json:"id"`
	UserID    string    `json:"user_id"`
	PublicKey string    `json:"public_key"`
	CreatedAt time.Time `json:"created_at"`
}

type Device struct {
	ID         int64     `json:"id"`
	UserID     string    `json:"user_id"`
	DeviceID   string    `json:"device_id"`
	DeviceName string    `json:"device_name"`
	PublicKey  string    `json:"public_key"`
	LastSeen   time.Time `json:"last_seen"`
}

type SyncData struct {
	ID            int64     `json:"id"`
	UserID        string    `json:"user_id"`
	DeviceID      string    `json:"device_id"`
	DataType      string    `json:"data_type"`
	EncryptedBlob []byte    `json:"encrypted_blob"`
	Signature     string    `json:"signature"`
	Version       int       `json:"version"`
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`
}

type UploadRequest struct {
	UserID        string `json:"user_id"`
	DeviceID      string `json:"device_id"`
	DataType      string `json:"data_type"`
	EncryptedBlob string `json:"encrypted_blob"` // Base64 encoded
	Signature     string `json:"signature"`
}

type DownloadRequest struct {
	UserID   string `json:"user_id"`
	DataType string `json:"data_type"`
	Since    int64  `json:"since,omitempty"` // Unix timestamp
}

type RegisterDeviceRequest struct {
	UserID     string `json:"user_id"`
	DeviceID   string `json:"device_id"`
	DeviceName string `json:"device_name"`
	PublicKey  string `json:"public_key"`
}
