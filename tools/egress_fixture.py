#!/usr/bin/env python3
"""Minimal controlled egress fixture for authorized local testing.

Run on a host reachable by the Android device:
  python3 tools/egress_fixture.py --port 8080

This endpoint reports the TCP peer address seen by the fixture. It is not an
anonymity service and must not be exposed publicly without access controls.
"""
import argparse
import json
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Handler(BaseHTTPRequestHandler):
    server_version = "CloakDroidFixture/1"

    def do_GET(self):
        peer_ip, peer_port = self.client_address[:2]
        body = {
            "ok": True,
            "observed_ip": peer_ip,
            "observed_port": peer_port,
            "server_family": "IPv6" if ":" in self.server.server_address[0] else "IPv4",
            "request_path": self.path,
        }
        raw = json.dumps(body).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, fmt, *args):
        # Deliberately do not log headers or credentials.
        print("fixture", self.client_address[0], self.command, self.path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"CloakDroid fixture listening on {args.host}:{args.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    main()
