#!/usr/bin/env python3
"""데모용 정적 서버 + API 프록시.

미리보기 패널의 오리진이 localhost:3000이 아닐 수 있어(또는 https라 http 요청이 mixed-content로 차단)
클라이언트가 백엔드(18090)를 직접 호출하면 CORS/mixed-content로 막힌다.
이 서버는 정적 파일을 서빙하면서 /api·/actuator·/v3 요청을 백엔드로 프록시해,
클라이언트가 '같은 오리진'(이 서버)으로만 호출하게 만든다 → CORS·mixed-content 무관하게 동작.
"""
import os
import urllib.error
import urllib.request
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

BACKEND = os.environ.get("BACKEND_URL", "http://localhost:18090")
PROXY_PREFIXES = ("/api/", "/actuator", "/v3/", "/swagger")
HOP_BY_HOP = {"connection", "keep-alive", "transfer-encoding", "content-encoding",
              "content-length", "te", "trailers", "upgrade", "host"}


class Handler(SimpleHTTPRequestHandler):
    def _is_proxy(self):
        return self.path.startswith(PROXY_PREFIXES)

    def _proxy(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        body = self.rfile.read(length) if length else None
        req = urllib.request.Request(BACKEND + self.path, data=body, method=self.command)
        for k, v in self.headers.items():
            if k.lower() not in HOP_BY_HOP:
                req.add_header(k, v)
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                self._relay(r.status, r.getheaders(), r.read())
        except urllib.error.HTTPError as e:
            self._relay(e.code, e.headers.items(), e.read())
        except Exception as e:  # noqa: BLE001
            payload = ('{"meta":{"result":"FAIL","errorCode":"PROXY_ERROR","message":"%s"}}'
                       % str(e)).encode()
            self._relay(502, [("Content-Type", "application/json")], payload)

    def _relay(self, status, headers, data):
        self.send_response(status)
        for k, v in headers:
            if k.lower() not in HOP_BY_HOP:
                self.send_header(k, v)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self._proxy() if self._is_proxy() else super().do_GET()

    def do_POST(self):
        self._proxy()

    def do_PUT(self):
        self._proxy()

    def do_DELETE(self):
        self._proxy()

    def log_message(self, fmt, *args):
        pass  # 조용히


if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    port = int(os.environ.get("PORT", "3000"))
    print(f"demo proxy on http://localhost:{port}  → backend {BACKEND}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
