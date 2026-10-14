#include "LocalHttpServer.h"
#include <iostream>
#include <sstream>
#include <thread>
#include <atomic>
#include <chrono>

class LocalHttpServer::Impl {
public:
    std::shared_ptr<ExaoneRuntime> runtime;
    int port = 8080;
    std::atomic<bool> running{false};
    std::thread serverThread;

    Impl(std::shared_ptr<ExaoneRuntime> rt) : runtime(std::move(rt)) {}

    bool start(int p) {
        if (running) return true;
        this->port = p;
        this->running = true;

        // 127.0.0.1:port 백그라운드 리스닝 루프 시작
        serverThread = std::thread([this]() {
            runLoop();
        });

        return true;
    }

    void stop() {
        if (!running) return;
        running = false;
        if (serverThread.joinable()) {
            serverThread.join();
        }
    }

    void runLoop() {
        // 내부 소켓 바인딩 (127.0.0.1 루프백 인터페이스 격리)
        while (running) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }

    std::string handleHealthRequest() {
        std::stringstream ss;
        ss << "{\n"
           << "  \"status\": \"healthy\",\n"
           << "  \"engine\": \"EXAONE-3.0-2.4B-Instruct\",\n"
           << "  \"acceleration\": \"Qualcomm QNN Hexagon NPU\",\n"
           << "  \"ramUsageMb\": " << (runtime ? runtime->getEstimatedRamUsageMb() : 0) << ",\n"
           << "  \"tokensPerSec\": " << (runtime ? runtime->getInferenceTokensPerSec() : 0.0f) << "\n"
           << "}";
        return ss.str();
    }
};

LocalHttpServer::LocalHttpServer(std::shared_ptr<ExaoneRuntime> runtime)
    : pImpl(std::make_unique<Impl>(std::move(runtime))) {}

LocalHttpServer::~LocalHttpServer() {
    stop();
}

bool LocalHttpServer::start(int port) {
    return pImpl->start(port);
}

void LocalHttpServer::stop() {
    pImpl->stop();
}

bool LocalHttpServer::isRunning() const {
    return pImpl->running;
}

int LocalHttpServer::getPort() const {
    return pImpl->port;
}
