package api

import (
	"github.com/gorilla/mux"
	"github.com/huginmunin/backend/database"
)

func RegisterRoutes(router *mux.Router, db *database.DB) {
	handler := NewHandler(db)

	// Health check
	router.HandleFunc("/health", handler.Health).Methods("GET")

	// Device management
	router.HandleFunc("/devices/register", handler.RegisterDevice).Methods("POST")
	router.HandleFunc("/devices", handler.ListDevices).Methods("GET")

	// Data sync
	router.HandleFunc("/sync/upload", handler.UploadData).Methods("POST")
	router.HandleFunc("/sync/download", handler.DownloadData).Methods("POST")
}
