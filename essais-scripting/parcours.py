#!/usr/bin/env python3
"""Joue le parcours utilisateur à travers le proxy de l'enregistreur JMeter,
comme le ferait un navigateur. Usage : parcours.py <port proxy> <port appli>"""
import http.cookiejar, re, sys, urllib.request, urllib.parse

proxy, appli = sys.argv[1], sys.argv[2]
base = "http://127.0.0.1:%s" % appli
cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.ProxyHandler({"http": "http://127.0.0.1:%s" % proxy}),
                                 urllib.request.HTTPCookieProcessor(cj))
HTML = {"Accept": "text/html,application/xhtml+xml"}
def get(url, entetes=None):
    r = op.open(urllib.request.Request(url, headers=entetes or {"Accept": "*/*"}))
    return r.status, r.read().decode()
st, page = get(base + "/login", HTML)
csrf = re.search(r'name="csrf" value="([^"]+)"', page).group(1)
get(base + "/static/app.js"); get(base + "/static/style.css"); get(base + "/logo.png", {"Accept": "image/png"})
get("http://127.0.0.2:%s/collect?page=login" % appli)
r = op.open(urllib.request.Request(base + "/login", data=urllib.parse.urlencode({"user": "alice", "csrf": csrf}).encode(),
                                   headers={"Accept": "application/json"}))
import json
commande = json.loads(r.read().decode())["commande"]
st, _ = get(base + "/commande/%s/detail" % commande, HTML)
get(base + "/static/style.css")
print("parcours joué : csrf=%s commande=%s detail=%s" % (csrf, commande, st))
