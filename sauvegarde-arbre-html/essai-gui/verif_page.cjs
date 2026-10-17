// Vérifie la page HTML produite dans un vrai navigateur (Chromium sans interface, Playwright).
// Usage : node verif_page.cjs <chemin de playwright> <chromium> page-filtrable.html page-tous.html
const { chromium } = require(process.argv[2]);
let echecs = 0;
const verifier = (quoi, ok) => { console.log((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; };

(async () => {
  const navigateur = await chromium.launch({ executablePath: process.argv[3] });
  const page = await navigateur.newPage({ viewport: { width: 1300, height: 800 } });
  const erreurs = [];
  page.on("pageerror", e => erreurs.push(e.message));
  page.on("console", m => { if (m.type() === "error") erreurs.push(m.text()); });
  const dialogues = [];
  page.on("dialog", d => { dialogues.push(d.message()); d.dismiss(); });

  await page.goto("file://" + require("path").resolve(process.argv[4]));
  const racine = () => page.$$eval("#arbre > ul > li", l => l.map(x => x.querySelector(".nom").textContent));
  verifier("filtre de l'arbre filtrable repris (« panier »)", await page.inputValue("#motif") === "panier");
  verifier("2 résultats affichés sur 6", (await racine()).length === 2 && (await page.textContent("#etatFiltre")).includes("2 affiché(s) sur 6"));
  await page.click("#effacer");
  verifier("filtre effacé : 6 résultats", (await racine()).length === 6);

  // Transaction : déplier, sélectionner une requête, onglets.
  await page.click("#arbre > ul > li:nth-child(1) .bascule");
  const enfants = await page.$$eval("#arbre > ul > li:nth-child(1) > ul > li .nom", l => l.map(x => x.textContent));
  verifier("T01 dépliée : GET /accueil, GET /logo.png", JSON.stringify(enfants) === JSON.stringify(["GET /accueil", "GET /logo.png"]));
  await page.click("text=GET /accueil");
  const stats = await page.textContent("#contenu");
  verifier("onglet « Résultat de l'échantillon » : code et nom d'unité", stats.includes("Code de retour") && stats.includes("200") && stats.includes("Utilisateurs 1-1"));
  await page.click("#onglets button:nth-child(3)");
  const corps = await page.textContent("#contenu pre");
  verifier("« Données de réponse » : HTML affiché en texte, piège compris", corps.includes("var x = '</script><!--';") && corps.includes("Bienvenue é"));
  verifier("aucun script de la réponse exécuté (2 balises script seulement)", (await page.$$eval("script", s => s.length)) === 2 && dialogues.length === 0);
  await page.click("#sousonglets button:nth-child(2)");
  verifier("en-têtes de réponse", (await page.textContent("#contenu")).includes("Content-Type: text/html"));
  await page.click("#onglets button:nth-child(2)");
  await page.click("#sousonglets button:nth-child(1)");
  verifier("onglet « Requête »", (await page.textContent("#contenu")).includes("GET http://127.0.0.1:18790/accueil"));
  await page.click("text=GET /logo.png");
  await page.click("#onglets button:nth-child(3)");
  await page.click("#sousonglets button:nth-child(1)");
  const largeur = await page.$eval("#contenu img", i => i.naturalWidth).catch(() => 0);
  verifier("image de la réponse affichée (16 px)", largeur === 16);

  // Erreur et assertion.
  await page.click("#arbre > ul > li:nth-child(3) .bascule");
  verifier("GET /erreur en rouge", await page.$eval("#arbre > ul > li:nth-child(3)", li => li.classList.contains("ko")));
  await page.click("#arbre > ul > li:nth-child(3) > ul > li .noeud");
  verifier("nœud d'assertion : onglet « Résultats d'assertion »", (await page.textContent("#onglets")).includes("assertion") && (await page.textContent("#contenu")).includes("Test failed"));

  // Mêmes règles de filtre que le plugin.
  const filtrer = async (motif, opts = {}) => {
    for (const id of ["regex", "casse", "exclure", "transactions"]) await page.setChecked("#" + id, !!opts[id]);
    await page.fill("#motif", motif); await page.waitForTimeout(350); return (await racine()).length; };
  verifier("exclure « panier » : 4", await filtrer("panier", { exclure: true }) === 4);
  verifier("« logo » garde la transaction qui contient la requête : 2", await filtrer("logo") === 2);
  verifier("« logo » en transactions seules : 0", await filtrer("logo", { transactions: true }) === 0);
  verifier("regex ^GET /(erreur|api) : 4", await filtrer("^GET /(erreur|api)", { regex: true }) === 4);
  verifier("casse respectée : « get » → 0", await filtrer("get", { casse: true }) === 0);
  await filtrer("(", { regex: true });
  verifier("regex invalide signalée, tout affiché", (await page.textContent("#etatFiltre")).includes("Expression invalide") && (await racine()).length === 6);
  await filtrer("");

  // Clavier.
  await page.click("#arbre > ul > li:nth-child(2) > .noeud");
  await page.keyboard.press("ArrowDown");
  verifier("flèche bas : résultat suivant", (await page.textContent(".noeud.sel .nom")) === "GET /erreur");
  await page.screenshot({ path: "4-page.png" });

  // Page à deux arbres.
  await page.goto("file://" + require("path").resolve(process.argv[5]));
  const options = await page.$$eval("#choixArbre option", o => o.map(x => x.textContent));
  console.log("  arbres : " + options.join(" | "));
  verifier("sélecteur des 2 arbres", options.length === 2 && options[0].includes("Arbre standard (12 résultats)"));
  verifier("premier arbre sans filtre : 12", (await racine()).length === 12 && await page.inputValue("#motif") === "");
  await page.selectOption("#choixArbre", "1");
  verifier("second arbre : filtre « panier », 2 sur 6", await page.inputValue("#motif") === "panier" && (await racine()).length === 2);

  verifier("aucune erreur JavaScript" + (erreurs.length ? " : " + erreurs.join(" ; ") : ""), erreurs.length === 0);
  await navigateur.close();
  console.log(echecs === 0 ? "TOUT EST BON (page)" : echecs + " ECHEC(S) (page)");
  process.exit(echecs ? 1 : 0);
})();
