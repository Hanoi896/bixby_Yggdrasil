package main

import (
	"log"
	"net/http"
	"os"

	"github.com/gorilla/mux"
	"github.com/huginmunin/backend/api"
	"github.com/huginmunin/backend/database"
	"github.com/rs/cors"
)

func main() {
	// Initialize database
	db, err := database.InitDB()
	if err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}
	defer db.Close()

	// Create router
	router := mux.NewRouter()
	
	// API routes
	apiRouter := router.PathPrefix("/api/v1").Subrouter()
	api.RegisterRoutes(apiRouter, db)

	// CORS middleware
	corsHandler := cors.New(cors.Options{
		AllowedOrigins:   []string{"http://localhost:5173", "http://localhost:3000"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Authorization", "Content-Type"},
		AllowCredentials: true,
	})

	// Start server
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("🚀 Hugin-Munin Sync Server starting on port %s", port)
	log.Printf("📊 Database: SQLite")
	log.Printf("🔒 Encryption: E2E enabled")
	
	handler := corsHandler.Handler(router)
	if err := http.ListenAndServe(":"+port, handler); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
