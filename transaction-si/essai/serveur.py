"""Application de test : /ok répond 200 « Bonjour », /ko répond 500."""
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Gestion(BaseHTTPRequestHandler):
    def do_GET(self):
        ok = self.path.startswith("/ok")
        corps = ("Bonjour" if ok else "Erreur interne").encode()
        self.send_response(200 if ok else 500)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        self.wfile.write(corps)

    def log_message(self, *args):
        pass


ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Gestion).serve_forever()
