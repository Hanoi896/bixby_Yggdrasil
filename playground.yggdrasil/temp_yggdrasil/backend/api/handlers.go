package api

import (
	"encoding/base64"
	"encoding/json"
	"log"
	"net/http"
	"time"

	"github.com/huginmunin/backend/database"
	"github.com/huginmunin/backend/models"
)

type Handler struct {
	db *database.DB
}

func NewHandler(db *database.DB) *Handler {
	return &Handler{db: db}
}

// RegisterDevice handles device registration
func (h *Handler) RegisterDevice(w http.ResponseWriter, r *http.Request) {
	var req models.RegisterDeviceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	// Validate required fields
	if req.UserID == "" || req.DeviceID == "" || req.PublicKey == "" {
		respondError(w, http.StatusBadRequest, "Missing required fields")
		return
	}

	// Check if user exists, create if not
	var userExists bool
	err := h.db.QueryRow("SELECT EXISTS(SELECT 1 FROM users WHERE user_id = ?)", req.UserID).Scan(&userExists)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "Database error")
		return
	}

	if !userExists {
		_, err = h.db.Exec("INSERT INTO users (user_id, public_key) VALUES (?, ?)", req.UserID, req.PublicKey)
		if err != nil {
			respondError(w, http.StatusInternalServerError, "Failed to create user")
			return
		}
	}

	// Register device
	_, err = h.db.Exec(
		"INSERT OR REPLACE INTO devices (user_id, device_id, device_name, public_key, last_seen) VALUES (?, ?, ?, ?, ?)",
		req.UserID, req.DeviceID, req.DeviceName, req.PublicKey, time.Now(),
	)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "Failed to register device")
		return
	}

	log.Printf("✅ Device registered: %s for user %s", req.DeviceID, req.UserID)
	respondJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"message": "Device registered successfully",
	})
}

// UploadData handles encrypted data upload
func (h *Handler) UploadData(w http.ResponseWriter, r *http.Request) {
	var req models.UploadRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	// Decode base64 encrypted blob
	encryptedBlob, err := base64.StdEncoding.DecodeString(req.EncryptedBlob)
	if err != nil {
		respondError(w, http.StatusBadRequest, "Invalid encrypted blob encoding")
		return
	}

	// TODO: Verify signature with device public key

	// Store encrypted data
	_, err = h.db.Exec(
		`INSERT INTO sync_data (user_id, device_id, data_type, encrypted_blob, signature)
		 VALUES (?, ?, ?, ?, ?)
		 ON CONFLICT(user_id, data_type) DO UPDATE SET
		 encrypted_blob = excluded.encrypted_blob,
		 signature = excluded.signature,
		 version = version + 1,
		 updated_at = CURRENT_TIMESTAMP`,
		req.UserID, req.DeviceID, req.DataType, encryptedBlob, req.Signature,
	)
	if err != nil {
		log.Printf("❌ Upload error: %v", err)
		respondError(w, http.StatusInternalServerError, "Failed to store data")
		return
	}

	log.Printf("📤 Data uploaded: type=%s, user=%s, size=%d bytes", req.DataType, req.UserID, len(encryptedBlob))
	respondJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"message": "Data uploaded successfully",
	})
}

// DownloadData handles encrypted data download
func (h *Handler) DownloadData(w http.ResponseWriter, r *http.Request) {
	var req models.DownloadRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	query := `SELECT id, user_id, device_id, data_type, encrypted_blob, signature, version, created_at, updated_at
			  FROM sync_data WHERE user_id = ?`
	args := []interface{}{req.UserID}

	if req.DataType != "" {
		query += " AND data_type = ?"
		args = append(args, req.DataType)
	}

	if req.Since > 0 {
		query += " AND updated_at > datetime(?, 'unixepoch')"
		args = append(args, req.Since)
	}

	rows, err := h.db.Query(query, args...)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "Database error")
		return
	}
	defer rows.Close()

	var dataList []models.SyncData
	for rows.Next() {
		var data models.SyncData
		err := rows.Scan(&data.ID, &data.UserID, &data.DeviceID, &data.DataType,
			&data.EncryptedBlob, &data.Signature, &data.Version, &data.CreatedAt, &data.UpdatedAt)
		if err != nil {
			continue
		}
		dataList = append(dataList, data)
	}

	log.Printf("📥 Data downloaded: user=%s, items=%d", req.UserID, len(dataList))
	respondJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"data":    dataList,
		"count":   len(dataList),
	})
}

// ListDevices lists all devices for a user
func (h *Handler) ListDevices(w http.ResponseWriter, r *http.Request) {
	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		respondError(w, http.StatusBadRequest, "user_id required")
		return
	}

	rows, err := h.db.Query(
		"SELECT id, user_id, device_id, device_name, public_key, last_seen FROM devices WHERE user_id = ?",
		userID,
	)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "Database error")
		return
	}
	defer rows.Close()

	var devices []models.Device
	for rows.Next() {
		var device models.Device
		err := rows.Scan(&device.ID, &device.UserID, &device.DeviceID,
			&device.DeviceName, &device.PublicKey, &device.LastSeen)
		if err != nil {
			continue
		}
		devices = append(devices, device)
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"devices": devices,
		"count":   len(devices),
	})
}

// Health check
func (h *Handler) Health(w http.ResponseWriter, r *http.Request) {
	respondJSON(w, http.StatusOK, map[string]interface{}{
		"status":  "healthy",
		"service": "hugin-munin-sync",
		"version": "1.0.0",
	})
}

// Helper functions
func respondJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func respondError(w http.ResponseWriter, status int, message string) {
	respondJSON(w, status, map[string]interface{}{
		"success": false,
		"error":   message,
	})
}
