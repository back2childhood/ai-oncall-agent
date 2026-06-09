from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading


def now_iso():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class MockOpsHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/v1/alerts":
            self._json({
                "status": "success",
                "data": {
                    "alerts": [
                        {
                            "labels": {
                                "alertname": "HighMemoryUsage",
                                "severity": "warning",
                                "service": "checkout-api",
                                "instance": "checkout-api-1"
                            },
                            "annotations": {
                                "summary": "Memory usage is above 85%",
                                "description": "checkout-api memory stayed high for the last 10 minutes"
                            },
                            "state": "firing",
                            "activeAt": now_iso()
                        }
                    ]
                }
            })
            return
        self.send_error(404, "Not found")

    def do_POST(self):
        if self.path == "/mcp/logs/query":
            length = int(self.headers.get("content-length", "0"))
            if length:
                self.rfile.read(length)
            self._json({
                "logs": [
                    {
                        "timestamp": now_iso(),
                        "service": "checkout-api",
                        "level": "ERROR",
                        "message": "OutOfMemoryError while processing checkout request",
                        "attributes": {
                            "traceId": "demo-trace-001",
                            "pod": "checkout-api-1"
                        }
                    },
                    {
                        "timestamp": now_iso(),
                        "service": "checkout-api",
                        "level": "WARN",
                        "message": "Full GC pause exceeded 2 seconds",
                        "attributes": {
                            "heapUsedPercent": "92",
                            "pod": "checkout-api-1"
                        }
                    }
                ]
            })
            return
        self.send_error(404, "Not found")

    def log_message(self, format, *args):
        return

    def _json(self, body):
        data = json.dumps(body).encode("utf-8")
        self.send_response(200)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def serve(port):
    ThreadingHTTPServer(("0.0.0.0", port), MockOpsHandler).serve_forever()


if __name__ == "__main__":
    threading.Thread(target=serve, args=(9090,), daemon=True).start()
    serve(8081)
