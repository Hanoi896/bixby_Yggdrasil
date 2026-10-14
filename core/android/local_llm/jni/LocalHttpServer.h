#ifndef LOCAL_HTTP_SERVER_H
#define LOCAL_HTTP_SERVER_H

#include <string>
#include <memory>
#include "ExaoneRuntime.h"

/**
 * LocalHttpServer
 * 기기 내부 127.0.0.1:8080 루프백 경량 HTTP REST 서버
 * Bixby 캡슐 및 앱 내부 클라이언트가 로컬 LLM을 호출할 수 있는 표준 인터페이스 제공
 */
class LocalHttpServer {
public:
    LocalHttpServer(std::shared_ptr<ExaoneRuntime> runtime);
    ~LocalHttpServer();

    /**
     * HTTP 서버 시작 (127.0.0.1 루프백 바인딩)
     * @param port 기본 8080
     */
    bool start(int port = 8080);

    /**
     * HTTP 서버 정지
     */
    void stop();

    bool isRunning() const;
    int getPort() const;

private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

#endif // LOCAL_HTTP_SERVER_H
