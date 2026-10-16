#!/usr/bin/env python3
"""Application de test pour les plugins de scripting.

Elle se comporte comme une vraie application à corréler : chaque session
(cookie SID) reçoit un jeton CSRF et un numéro de commande aléatoires, et les
requêtes qui ne renvoient pas les bonnes valeurs sont refusées.

  GET  /login                 page HTML avec <input name="csrf" value="...">
  POST /login                 csrf=<jeton> ; rend {"commande": "<8 chiffres>"} (403 si mauvais jeton)
  GET  /commande/<n>/detail   200 si <n> est la commande de la session, 404 sinon
  GET  /static/app.js, /static/style.css, /logo.png   ressources statiques
  GET  /collect               « mesure d'audience » (servie sur 127.0.0.2 par une 2e instance)

Usage : serveur.py <port> [adresse]
"""
import json, random, string, sys, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sessions = {}

def alea(n, alphabet=string.ascii_letters + string.digits):
    return "".join(random.choice(alphabet) for _ in range(n))

class Gestion(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def sid(self):
        for c in (self.headers.get("Cookie") or "").split(";"):
            k, _, v = c.strip().partition("=")
            if k == "SID":
                return v
        return None

    def repondre(self, code, corps, type_="text/html", entetes=None):
        b = corps.encode()
        self.send_response(code)
        self.send_header("Content-Type", type_)
        self.send_header("Content-Length", str(len(b)))
        for k, v in (entetes or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        chemin = urllib.parse.urlparse(self.path).path
        if chemin == "/login":
            sid = alea(16)
            sessions[sid] = {"csrf": alea(20)}
            page = ('<html><body><form method="post" action="/login">'
                    '<input type="hidden" name="csrf" value="%s"/>'
                    '<input name="user"/></form></body></html>' % sessions[sid]["csrf"])
            self.repondre(200, page, entetes={"Set-Cookie": "SID=%s; Path=/" % sid})
        elif chemin.startswith("/commande/") and chemin.endswith("/detail"):
            n = chemin.split("/")[2]
            s = sessions.get(self.sid() or "", {})
            if s.get("commande") == n:
                self.repondre(200, "<html>commande %s</html>" % n)
            else:
                self.repondre(404, "commande inconnue")
        elif chemin in ("/static/app.js", "/static/style.css"):
            self.repondre(200, "/* statique */", "text/plain")
        elif chemin == "/logo.png":
            self.repondre(200, "PNG", "image/png")
        elif chemin == "/collect":
            self.repondre(200, "ok", "text/plain")
        elif chemin in ("/", "/accueil"):
            self.repondre(200, "<html>accueil</html>")
        else:
            self.repondre(404, "?")

    def do_POST(self):
        chemin = urllib.parse.urlparse(self.path).path
        n = int(self.headers.get("Content-Length") or 0)
        donnees = urllib.parse.parse_qs(self.rfile.read(n).decode())
        s = sessions.get(self.sid() or "")
        if chemin == "/login":
            if s is None or donnees.get("csrf", [""])[0] != s["csrf"]:
                self.repondre(403, "jeton CSRF invalide")
                return
            s["commande"] = alea(8, string.digits)
            self.repondre(200, json.dumps({"commande": s["commande"], "statut": "ok"}), "application/json")
        else:
            self.repondre(404, "?")

port = int(sys.argv[1])
adresse = sys.argv[2] if len(sys.argv) > 2 else "127.0.0.1"
ThreadingHTTPServer((adresse, port), Gestion).serve_forever()
