# Enregistreur de script sans variables

L'enregistreur HTTP(S) de JMeter (« HTTP(S) Test Script Recorder »), à une
différence près : **il ne remplace jamais une valeur capturée par une
variable**. Les requêtes enregistrées gardent leurs valeurs brutes, sans
aucun `${...}`.

## Le problème qu'il règle

Pendant une capture, l'enregistreur standard cherche les variables définies :
celles du plan de test, les « User Defined Variables » placées sous
l'enregistreur, sous le contrôleur cible ou ses parents. Toute valeur
capturée qui leur est égale (serveur, port, paramètre, en-tête…) est
remplacée par `${nom}`. Aucune option ne permet de le désactiver.

Mesuré sur JMeter 5.4.3, avec les mêmes requêtes capturées et un plan qui
définit `serveur=127.0.0.1`, `port_app=18765`, `utilisateur=alice` et
`jeton=XYZ789` :

| | Serveur | Port | Paramètres et en-têtes |
|---|---|---|---|
| Enregistreur standard | `${serveur}` | `${port_app}` | `${utilisateur}`, `${jeton}` |
| **Enregistreur sans variables** | `127.0.0.1` | `18765` | `alice`, `XYZ789` |

Tout le reste est identique à l'enregistreur standard, dont il reprend
l'écran : port, filtres, regroupement, en-têtes, certificat HTTPS, valeurs
retirées quand elles égalent les « HTTP Request Defaults », gestionnaire
d'autorisation créé à partir de l'en-tête `Authorization`, minuteries
`${T}`.

## Installation

Compatible JMeter 5.4.3 et plus récent, Java 11 (bytecode Java 8).

1. Fermer JMeter et copier `enregistreur-sans-variables-0.1.0.jar` dans
   `lib\ext` (par exemple `C:\apache-jmeter-5.4.3\lib\ext\`).
2. Relancer JMeter.

## Utilisation

Clic droit sur le plan de test : *Ajouter > Éléments hors test >
Enregistreur de script sans variables* (JMeter en anglais : *Add >
Non-Test Elements*). Il s'utilise exactement comme l'enregistreur
standard : contrôleur cible, port, *Démarrer*, navigation, *Arrêter*.

## ⚠️ Avant de lancer le tir sur des injecteurs

**Supprimer l'enregistreur du plan une fois la capture terminée**, puis
enregistrer. Tant qu'il y est, un JMeter qui n'a pas le plugin refuse
d'ouvrir le plan, **même en ligne de commande**
(`CannotResolveClassException: fr.jmeter.plugins.enregistreur.EnregistreurSansVariables`),
contrairement à l'arbre de résultats filtrable. L'enregistreur n'a de toute
façon aucun rôle pendant un tir.

Les requêtes capturées, elles, sont des requêtes HTTP standard : une fois
l'enregistreur supprimé, le plan tourne partout.

## Limites

- Une valeur capturée qui contenait déjà `${` (gabarit JavaScript dans un
  corps de requête, par exemple) est conservée telle quelle : seules les
  valeurs que l'enregistreur a modifiées sont rétablies.
- Il n'y a pas d'interrupteur : pour retrouver le remplacement par les
  variables, utiliser l'enregistreur standard.

## Fonctionnement

Le remplacement est fait dans une méthode privée de `ProxyControl`. Le
plugin hérite de `ProxyControl` et, à chaque requête capturée :

1. prend une copie de la requête et de ses éléments de configuration
   (en-têtes) avant que l'enregistreur standard ne les traite ;
2. laisse l'enregistreur standard faire tout son travail ;
3. remet la valeur d'origine sur chaque valeur texte qui a changé **et**
   contient désormais `${`.

Les autres modifications de l'enregistreur ne produisent pas de `${`, elles
sont donc conservées. Son écran hérite de `ProxyControlGui` et se branche
par la méthode `makeProxyControl()`, prévue pour cela.

## Essais

- `mvn test` : rejoue le remplacement avec le `ValueReplacer` de JMeter,
  mode texte et mode regex, en-tête `Authorization` retiré, puis vérifie la
  restauration (6 tests).
- `essai-gui/lancer.sh` : capture réelle dans l'interface de JMeter (5.4.3 par
  défaut) sous écran virtuel. Les mêmes requêtes `curl` passent par
  l'enregistreur standard (témoin), puis par le plugin. L'essai compare les
  plans enregistrés par la commande *Enregistrer* de JMeter, puis rejoue le
  plan en ligne de commande.
