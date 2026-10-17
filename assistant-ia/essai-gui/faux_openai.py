#!/usr/bin/env python3
"""Faux serveur au format OpenAI pour l'essai de l'assistant IA.

POST /v1/chat/completions : répond en flux SSE (raisonnement puis texte). Il lit
l'arborescence jointe par le plugin pour retrouver les ids, et propose des
actions jmeter-actions réalistes (dont une sur un id inexistant). Chaque
requête reçue est écrite dans requetes.jsonl.
GET /v1/models : deux modèles.
Usage : faux_openai.py <port>
"""
import json, re, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ASSERTION = ('<ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="Code 200" enabled="true">'
             '<collectionProp name="Asserion.test_strings"><stringProp name="49586">200</stringProp></collectionProp>'
             '<stringProp name="Assertion.custom_message"></stringProp>'
             '<stringProp name="Assertion.test_field">Assertion.response_code</stringProp>'
             '<boolProp name="Assertion.assume_success">false</boolProp>'
             '<intProp name="Assertion.test_type">8</intProp></ResponseAssertion>')

def ids(texte):
    return {m.group(2): m.group(1) for m in re.finditer(r"\[([0-9.]+)\] \S+ « (.*?) »", texte)}

class Gestion(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_GET(self):
        if self.path.endswith("/models"):
            b = json.dumps({"object": "list", "data": [{"id": "faux-1"}, {"id": "faux-2"}]}).encode()
            self.send_response(200); self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
        else:
            self.send_response(404); self.end_headers()

    def do_POST(self):
        corps = json.loads(self.rfile.read(int(self.headers.get("Content-Length") or 0)))
        with open("requetes.jsonl", "a") as f:
            f.write(json.dumps({"entetes": dict(self.headers), "corps": corps}) + "\n")
        if not corps.get("stream"):
            b = json.dumps({"choices": [{"message": {"content": "OK"}, "finish_reason": "stop"}]}).encode()
            self.send_response(200); self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
            return
        dernier = corps["messages"][-1]["content"]
        i = ids(dernier)
        actions = [
            {"action": "ajouter", "parent": i.get("/api/commande"), "xml": ASSERTION},
            {"action": "modifier", "cible": i.get("/api/commande"), "proprietes": {"HTTPSampler.path": "/api/v2/commande"}},
            {"action": "renommer", "cible": i.get("T01"), "nom": "Connexion"},
            {"action": "desactiver", "cible": i.get("Debug")},
            {"action": "supprimer", "cible": i.get("Pause inutile")},
            {"action": "renommer", "cible": "9.9.9", "nom": "fantôme"},
        ]
        texte = ("J'ajoute une assertion sur le code 200, je passe la requête en v2, je renomme la transaction, "
                 "je désactive le Debug Sampler et je supprime la minuterie inutile.\n\n```jmeter-actions\n"
                 + json.dumps(actions, ensure_ascii=False, indent=1) + "\n```\n")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.end_headers()
        def envoyer(d):
            self.wfile.write(("data: " + json.dumps(d, ensure_ascii=False) + "\n\n").encode()); self.wfile.flush()
        for mot in ["Je ", "regarde ", "le ", "plan."]:
            envoyer({"choices": [{"delta": {"reasoning_content": mot}}]}); time.sleep(0.05)
        for k in range(0, len(texte), 40):
            envoyer({"choices": [{"delta": {"content": texte[k:k + 40]}}]}); time.sleep(0.01)
        envoyer({"choices": [{"delta": {}, "finish_reason": "stop"}]})
        self.wfile.write(b"data: [DONE]\n\n"); self.wfile.flush()

ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Gestion).serve_forever()
