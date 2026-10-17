# Audit de sécurité des plugins — 2026-10-17

Ce dépôt contient huit plugins. Exigence : **aucun appel vers l'extérieur, sauf
vers l'API du modèle** de l'assistant IA.

## Résultat

| Plugin | Version auditée → corrigée | Connexions sortantes | Correctifs |
|---|---|---|---|
| Arbre de résultats filtrable | 0.2.0 → 0.2.1 | aucune | rendu HTML Swing désactivé |
| Enregistreur sans variables | 0.1.0 | aucune (le proxy d'enregistrement écoute, c'est le comportement standard de JMeter) | — |
| Modification en masse | 0.3.0 → 0.3.1 | aucune | rendu HTML Swing désactivé |
| Vérificateur de script | 0.1.0 → 0.1.1 | aucune | rendu HTML Swing désactivé |
| Nettoyage d'enregistrement | 0.1.0 → 0.1.1 | aucune | rendu HTML Swing désactivé |
| Corrélation assistée | 0.1.0 → 0.1.1 | aucune | lecture JTL durcie, rendu HTML Swing désactivé |
| Assistant IA | 0.1.0 → **0.2.0** | **uniquement l'URL du profil choisi** (API du modèle) | **critique** : liste blanche du XML ; redirections refusées ; profils locaux par défaut ; alerte sur le code exécutable ; rendu HTML désactivé |
| Sauvegarde HTML de l'arbre | 0.1.0 → 0.1.1 | aucune (page sans ressource externe) | page créée avec les droits 600 |

## Méthode

1. **Lecture du code** : recherche de tout usage réseau, de processus, de
   désérialisation, d'analyse XML, d'écriture de fichiers et de rendu HTML.
2. **Bytecode des jars** (`javap`), pour couvrir ce qu'une recherche dans les
   sources laisserait passer.
   - Références à `java.net` : `ClientOpenAI` (assistant IA) seulement, plus
     l'URI du fichier local ouvert par « Ouvrir » (sauvegarde HTML).
   - Aucune référence à `java.nio.channels`, `ProcessBuilder`, `Runtime.exec`,
     JNDI, RMI ou `ObjectInputStream`.
   - Les jars ne contiennent que le code des plugins (aucune bibliothèque
     embarquée).
3. **Essai à l'exécution** : les six bancs d'essai de bout en bout ont été
   rejoués dans un espace réseau isolé (`bwrap --unshare-net`), avec la seule
   boucle locale et sans DNS.
   - Un agent Java a noté chaque connexion tentée (HTTP et sockets).
   - Résultat : **les six passent**. Les 49 connexions vont toutes vers
     127.0.0.1 ou 127.0.0.2 : applications d'essai et faux serveur de modèle.
   - Une seule vient d'un plugin : l'assistant IA vers le serveur du profil.
4. **Tests** : `SecuriteTest` (assistant IA) et un test des droits de la page
   (sauvegarde HTML). 108 tests au total.

## Constats et correctifs

### 1. Critique — XML du modèle lu par JMeter sans restriction (assistant IA 0.1.0)

**Le défaut.** L'aperçu des actions fait lire par JMeter (`SaveService.loadTree`)
le XML proposé par le modèle, dès la réception de la réponse. Or JMeter 5.4.3
configure XStream pour accepter **toute classe Java**
(`JMeterUtils.setupXStreamSecurityPolicy` : `AnyTypePermission.ANY`).

**Le risque.** Une réponse malveillante pouvait faire instancier des classes
arbitraires sur le poste, avant tout clic sur « Appliquer ». Elle pouvait venir
d'un serveur compromis ou d'un contenu du plan conçu pour détourner le modèle.

**Correctif (0.2.0).** `ControleXml` applique une liste blanche **avant** toute
lecture par JMeter :
- `hashTree` ;
- éléments dont la balise désigne une classe `TestElement` ;
- `guiclass` désignant un `JMeterGUIComponent`, `testclass` désignant un
  `TestElement` ;
- propriétés `stringProp`, `boolProp`, `intProp`, `longProp`, `floatProp`,
  `doubleProp`, `elementProp` (dont l'`elementType` doit être un
  `TestElement`) et `collectionProp` ;
- attributs limités à une liste fermée ;
- DOCTYPE refusé.

Les classes sont vérifiées par `Class.forName(nom, false, …)`, donc **sans être
initialisées**. Tout le reste est refusé : `objProp`, `mapProp`, attribut
`class`, balises inconnues. Les six fragments du catalogue et les éléments
courants restent acceptés (tests).

### 2. Élevé — Redirection suivie avec la clé d'API (assistant IA 0.1.0)

`HttpURLConnection` suit les redirections par défaut. Une redirection du
serveur aurait envoyé la requête, avec ses en-têtes et donc la clé, vers un
autre hôte.

**Correctif :** `setInstanceFollowRedirects(false)` ; un code 3xx devient une
erreur lisible (« redirection vers … non suivie ») ; seuls `http` et `https`
sont acceptés. Test : un faux serveur qui redirige, et un second serveur qui
ne reçoit **aucune** requête.

### 3. Moyen — Profils de services en ligne proposés d'office (assistant IA 0.1.0)

Le profil actif par défaut était déjà local (Ollama sur localhost). Mais la
configuration créée au premier lancement listait aussi OpenAI, Mistral et
OpenRouter : il suffisait de les choisir dans la liste pour envoyer le contexte
hors de l'entreprise.

**Correctif :** profils locaux seulement (localhost). L'API du modèle
s'ajoute dans *Réglages*.

### 4. Moyen — Rendu HTML de Swing (six plugins)

Un texte qui commence par `<html>` est interprété comme du HTML par les
`JLabel`, donc par les cellules de tableau et les listes, et une balise `<img>`
y charge une URL. Les textes affichés viennent du trafic enregistré, du plan ou
du modèle : une valeur piégée pouvait déclencher un appel réseau à la simple
ouverture d'une fenêtre.

**Correctif :** propriété Swing `html.disable` sur les rendus des tableaux,
listes et listes déroulantes (`SansHtml`). Test : aucune vue HTML n'est créée.

### 5. Moyen — Code exécuté pendant un tir (assistant IA)

Le modèle peut proposer un élément JSR223, BeanShell ou OS Process, ou une
fonction `__groovy`, `__javaScript`, `__BeanShell` ou `__jexl`. Rien ne
s'exécute à l'aperçu, mais un tel ajout exécutera du code au tir.

**Correctif :** l'action est **signalée et décochée**. L'utilisateur la relit
avant de la cocher.

### 6. Faible — Lecture des fichiers JTL (corrélation assistée)

Les entités externes étaient déjà désactivées.

**Durcissement :** DOCTYPE refusé, entités de paramètres désactivées,
`FEATURE_SECURE_PROCESSING`, XInclude désactivé.

### 7. Faible — Droits des fichiers écrits (sauvegarde HTML, assistant IA)

La page HTML contient les en-têtes de requête (cookies, jetons
`Authorization`).

**Correctif :** elle est créée lisible par son seul propriétaire (600, sous
Linux et macOS), tout comme le fichier temporaire de l'assistant. La
configuration de l'assistant l'était déjà.

## Points vérifiés sans constat

- **Démarrage et ouverture des fenêtres** : aucune connexion. L'assistant ne
  contacte le serveur que sur *Envoyer*, *Lister les modèles* ou *Tester*.
- **Données envoyées** : aucune télémétrie, aucune vérification de mise à jour,
  aucun téléchargement.
- **Page HTML de sauvegarde** :
  - aucune ressource externe ;
  - données JSON échappées : `</script>` dans une réponse ne ferme pas le bloc ;
  - réponses affichées en texte (`textContent`), jamais interprétées.

  Vérifié dans Chromium, réseau coupé.
- **Clé d'API** : jamais journalisée. Elle peut rester dans une variable
  d'environnement (`${env:…}`).
- **Réflexion** (sauvegarde HTML → arbre filtrable) : lecture seule de champs
  privés, sans chargement de classe externe.

## Risques résiduels et recommandations

- **Données envoyées au modèle.** La question et le contexte joint
  (arborescence, XML de la sélection, rapport) partent vers l'API du modèle.
  Si cette API est hors de l'entreprise, ne pas joindre de plan contenant des
  secrets (mots de passe dans les paramètres, jetons dans les en-têtes), ou
  décocher *Joindre*. Piste : un masquage automatique des valeurs sensibles
  avant envoi (non fait).
- **Comportements propres à JMeter 5.4.3**, hors de ces plugins :
  - tout `.jmx` ouvert est lu avec la même politique XStream permissive : un
    plan reçu d'une source non fiable reste dangereux à ouvrir ;
  - le proxy d'enregistrement écoute sur toutes les interfaces
    (`new ServerSocket(port)` dans `Daemon`) : arrêter l'enregistreur après
    usage.

- **Plans produits.** Un plan modifié par l'IA contacte, au tir, les serveurs
  qui y sont écrits. C'est le principe d'un test de charge ; l'aperçu les
  montre avant application.
