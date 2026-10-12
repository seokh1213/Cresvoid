# 네트워크

서비스 사이 호출에서 생기는 연결 수명, 타임아웃, 이름 해석 문제를 익힌다. 모든 실험은 JDK 의 HTTP 서버와 클라이언트, Kotlin 으로 만든 작은 TCP 프록시로 돌아간다.

## 1. 개념 지도

- keep-alive 연결은 양쪽이 각자 놀고 있는 연결의 수명을 정한다. 서버가 먼저 닫은 연결을 클라이언트 풀이 다시 쓰면 첫 요청이 실패한다. 클라이언트 쪽 수명을 서버보다 짧게 둔다.
- 타임아웃은 연결, 풀에서 연결을 빌리는 대기, 응답 대기, 요청 전체로 나뉜다. 하나라도 없으면 느린 하위 서비스가 스레드를 점유한다.
- 이름 해석 캐시는 [../jvm/README.md](../jvm/README.md) 에 있다.
- HTTP 클라이언트 풀의 경로당 한도는 [../spring/README.md](../spring/README.md) 의 'HTTP 클라이언트' 절에 있다.
- HTTP/2 는 연결 하나에 요청 여러 개를 섞어 보낸다. TCP 수준에서 잃은 패킷 하나가 그 연결의 모든 흐름을 기다리게 한다.

## 2. 학습 경로

L-network-10~L-network-20 은 lab 종류다. lab 엔진 작업([../lab.md](../lab.md))이 먼저다. 추가 도구는 필요 없다.

## 3. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| TIME_WAIT 와 임시 포트 | lab | 짧은 연결을 많이 맺을 때 |
| 긴 연결과 L4 부하 분산 | reading | gRPC 처럼 연결을 오래 쓰는 프로토콜의 쏠림 |
| TLS 핸드셰이크 비용과 세션 재개 | lab | 연결 재사용 여부에 따른 지연 |
| 리버스 프록시의 upstream 연결 재사용 | lab | nginx 가 upstream 연결을 다시 쓰는 조건, 요청마다 새 연결을 맺을 때의 TIME_WAIT, 설정을 읽을 때만 해석한 upstream 주소. 1.29.7 전에는 `keepalive` 에 더해 `proxy_http_version 1.1` 과 빈 `Connection` 헤더가 있어야 연결을 다시 쓴다. 주소를 다시 해석하는 `resolve` 는 1.27.3 부터 오픈 소스판에도 있다. nginx 가 필요하다 |

## 4. 출처

- RFC 9110(HTTP Semantics), RFC 9112(HTTP/1.1), RFC 9113(HTTP/2), RFC 9293(TCP).
- java.net.http.HttpClient 와 com.sun.net.httpserver.HttpServer API 문서(JDK 21).
- nginx 문서: ngx_http_upstream_module 의 `keepalive` 와 `server`, ngx_http_proxy_module 의 `proxy_http_version`. https://nginx.org/en/docs/http/ngx_http_upstream_module.html
