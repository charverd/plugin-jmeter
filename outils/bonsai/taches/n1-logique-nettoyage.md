Tu écris du code Java 8 (pas de `var`, pas de `List.of`, pas de `String.isBlank`, pas de `Map.of`). Les noms de classes, méthodes, variables et les commentaires sont en français, sans accents dans les identifiants. Aucune dépendance autre que le JDK et JUnit 5. Livre exactement deux fichiers, chacun précédé d'une ligne `// FICHIER: <chemin>` puis d'un bloc ```java. Rien d'autre après les deux blocs.

Contexte : un plugin JMeter qui nettoie un script enregistré. L'interface (déjà prévue, ne pas l'écrire) lit chaque requête enregistrée dans une `RequeteVue`, demande à ta classe ce qu'il faut supprimer ou regrouper, puis modifie l'arbre JMeter elle-même. Ta classe ne connaît pas JMeter.

## Fichier 1 : `nettoyage-enregistrement/src/main/java/fr/jmeter/plugins/nettoyage/AnalyseEnregistrement.java`

Paquet `fr.jmeter.plugins.nettoyage`. Une classe `public final class AnalyseEnregistrement` (constructeur privé, méthodes statiques) contenant deux classes imbriquées publiques et statiques :

```java
/** Ce qu'on sait d'une requête enregistrée. Champs finaux, constructeur, getters. */
public static final class RequeteVue {
    public RequeteVue(String domaine, String chemin, String methode, String accept);
    // domaine : "www.exemple.fr" (peut être vide) ; chemin : "/a/b.css?v=2" (peut contenir une query string, peut être vide)
    // methode : "GET", "POST"… ; accept : valeur de l'en-tête Accept enregistré, ou null
    public String getDomaine(); public String getChemin(); public String getMethode(); public String getAccept();
}

/** Un groupe de requêtes consécutives formant une page : indices [debut, fin[ dans la liste d'entrée. */
public static final class Groupe {
    public Groupe(int debut, int fin, String page);
    public int getDebut(); public int getFin(); public String getPage(); public int taille();
}
```

Constantes et méthodes publiques statiques :

1. `EXTENSIONS_STATIQUES_PAR_DEFAUT` : `List<String>` non modifiable, dans cet ordre : css, js, mjs, map, png, jpg, jpeg, gif, svg, ico, webp, bmp, woff, woff2, ttf, otf, eot, mp4, webm, mp3.

2. `Set<String> lireExtensions(String texte)` : découpe sur virgules, points-virgules et espaces ; retire les points initiaux ; met en minuscules ; ignore les vides ; ordre conservé (`LinkedHashSet`). `"CSS, .js;png"` → `[css, js, png]`.

3. `String extension(String chemin)` : extension en minuscules du dernier segment du chemin, après avoir retiré la query string (`?…`) et le fragment (`#…`). Rend `""` s'il n'y en a pas. `"/a/b.CSS?v=2"` → `"css"` ; `"/a.b/c"` → `""` ; `"/"` → `""` ; `null` → `""`.

4. `boolean estRessourceStatique(RequeteVue r, Set<String> extensions)` : vrai si `extension(r.getChemin())` est dans `extensions`.

5. `Map<String, Integer> compterDomaines(List<RequeteVue> requetes)` : nombre de requêtes par domaine, en minuscules, domaine vide compté sous `""`. Ordre : nombre décroissant, puis domaine croissant (`LinkedHashMap`).

6. `boolean estTiersConnu(String domaine)` : vrai si le domaine (minuscules) est égal à l'un des suffixes suivants ou se termine par `"." + suffixe` : google-analytics.com, googletagmanager.com, doubleclick.net, googlesyndication.com, googleadservices.com, facebook.com, facebook.net, connect.facebook.net, hotjar.com, hotjar.io, clarity.ms, bing.com, linkedin.com, licdn.com, twitter.com, ads-twitter.com, newrelic.com, nr-data.net, dynatrace.com, segment.io, segment.com, mixpanel.com, optimizely.com, cookielaw.org, onetrust.com, gstatic.com, fonts.googleapis.com, sentry.io, mouseflow.com, matomo.cloud.

7. `boolean estPagePrincipale(RequeteVue r, Set<String> extensions)` :
   - faux si `estRessourceStatique(r, extensions)` ;
   - vrai si `r.getAccept()` non null contient `text/html` (sans tenir compte de la casse) ;
   - sinon, si `r.getAccept()` est non null et non vide : faux (c'est un appel XHR, JSON, image…) ;
   - sinon (pas d'en-tête Accept) : vrai si la méthode est GET (sans casse), faux sinon.

8. `String nomDePage(RequeteVue r)` : dernier segment non vide du chemin (query string et fragment retirés), sans son extension ; caractères hors `[A-Za-z0-9_-]` remplacés par `_` ; `_` répétés réduits à un seul ; `_` en début et fin retirés. Chemin vide ou `/` : `"accueil"`. Résultat vide après nettoyage : `"page"`. `"/fr/mon-compte/commandes.aspx?id=3"` → `"commandes"` ; `"/api/v1/"` → `"v1"` ; `"/connexion%20client"` → `"connexion_20client"`.

9. `List<Groupe> regrouperParPage(List<RequeteVue> requetes, Set<String> extensions)` : parcourt dans l'ordre ; une requête qui `estPagePrincipale` ouvre un nouveau groupe, nommé `nomDePage` de cette requête ; les suivantes s'y ajoutent jusqu'à la prochaine page principale. Les requêtes situées avant la première page principale forment un premier groupe nommé `nomDePage` de la première requête. Liste vide → liste vide. Les groupes couvrent toute la liste, sans trou ni recouvrement.

10. `String nomTransaction(String modele, int numero, int chiffres, String page)` : remplace dans `modele` toutes les occurrences de `{n}` par `numero` complété à gauche par des zéros jusqu'à `chiffres` chiffres (au moins 1), et `{page}` par `page`. Modèle null ou vide : `"{n}_{page}"`. `nomTransaction("T{n}_{page}", 3, 2, "connexion")` → `"T03_connexion"`.

## Fichier 2 : `nettoyage-enregistrement/src/test/java/fr/jmeter/plugins/nettoyage/AnalyseEnregistrementTest.java`

JUnit 5. Couvrir chaque méthode, avec au moins les exemples donnés ci-dessus, plus : `estPagePrincipale` (Accept `text/html,application/xhtml+xml`, Accept `application/json`, sans Accept en GET, sans Accept en POST, ressource `.js` avec Accept `*/*`) ; `estTiersConnu` (`www.google-analytics.com` vrai, `analytics.google.com.exemple.fr` faux, `nr-data.net` vrai, `exemple.fr` faux) ; `regrouperParPage` sur un parcours réaliste de 9 requêtes : une ressource `.js` avant la première page, la page `/connexion` (Accept text/html), deux `.css`, un POST `/api/login` avec Accept `application/json`, la page `/accueil` (Accept text/html), une image `.png`, un GET `/api/panier` avec Accept `application/json`, la page `/commandes` sans Accept en GET → 4 groupes [0,1[ « app » si la ressource est `/static/app.js`, [1,5[ « connexion », [5,8[ « accueil », [8,9[ « commandes » ; vérifier que les groupes couvrent toute la liste ; `compterDomaines` (ordre et casse).
