"""Petite application de test : page HTML (avec un piège </script>), image, JSON, erreur 500."""
import base64
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAF0lEQVR4nGP8z8DAwMDAxMDAwMDAAAANHQEDasKb6QAAAABJRU5ErkJggg==")

PAGES = {
    "/accueil": (200, "text/html; charset=UTF-8",
                 "<html><head><title>Accueil</title><script>var x = '</script><!--';</script></head>"
                 "<body><h1>Bienvenue é</h1></body></html>".encode()),
    "/logo.png": (200, "image/png", PNG),
    "/api/panier": (200, "application/json", b'{"articles": 3, "total": "42,50 \\u20ac"}'),
    "/erreur": (500, "text/plain; charset=UTF-8", "Erreur interne simulée".encode()),
}


class Gestion(BaseHTTPRequestHandler):
    def do_GET(self):
        code, type_, corps = PAGES.get(self.path.split("?")[0], (404, "text/plain", b"absent"))
        self.send_response(code)
        self.send_header("Content-Type", type_)
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        self.wfile.write(corps)

    def log_message(self, *a):
        pass


ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Gestion).serve_forever()
