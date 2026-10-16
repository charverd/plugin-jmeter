package fr.jmeter.plugins.nettoyage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Décisions du nettoyage : ressource statique, domaine tiers, début de page,
 * nom de transaction. Ne connaît pas JMeter.
 */
public final class AnalyseEnregistrement {

    /** Ce qu'on sait d'une requête enregistrée. */
    public static final class RequeteVue {
        private final String domaine;
        private final String chemin;
        private final String methode;
        private final String accept;

        /**
         * @param accept valeur de l'en-tête Accept enregistré, ou {@code null}
         */
        public RequeteVue(String domaine, String chemin, String methode, String accept) {
            this.domaine = domaine == null ? "" : domaine;
            this.chemin = chemin == null ? "" : chemin;
            this.methode = methode == null ? "" : methode;
            this.accept = accept;
        }

        public String getDomaine() {
            return domaine;
        }

        public String getChemin() {
            return chemin;
        }

        public String getMethode() {
            return methode;
        }

        public String getAccept() {
            return accept;
        }
    }

    /** Requêtes consécutives formant une page : indices [debut, fin[. */
    public static final class Groupe {
        private final int debut;
        private final int fin;
        private final String page;

        public Groupe(int debut, int fin, String page) {
            this.debut = debut;
            this.fin = fin;
            this.page = page;
        }

        public int getDebut() {
            return debut;
        }

        public int getFin() {
            return fin;
        }

        public String getPage() {
            return page;
        }

        public int taille() {
            return fin - debut;
        }
    }

    public static final List<String> EXTENSIONS_STATIQUES_PAR_DEFAUT = Collections.unmodifiableList(Arrays.asList(
            "css", "js", "mjs", "map", "png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "bmp", "woff", "woff2",
            "ttf", "otf", "eot", "mp4", "webm", "mp3"));

    private static final List<String> TIERS = Arrays.asList("google-analytics.com", "googletagmanager.com",
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "facebook.com", "facebook.net",
            "connect.facebook.net", "hotjar.com", "hotjar.io", "clarity.ms", "bing.com", "linkedin.com", "licdn.com",
            "twitter.com", "ads-twitter.com", "newrelic.com", "nr-data.net", "dynatrace.com", "segment.io",
            "segment.com", "mixpanel.com", "optimizely.com", "cookielaw.org", "onetrust.com", "gstatic.com",
            "fonts.googleapis.com", "sentry.io", "mouseflow.com", "matomo.cloud");

    private AnalyseEnregistrement() {
    }

    /** « CSS, .js;png » → [css, js, png]. */
    public static Set<String> lireExtensions(String texte) {
        Set<String> s = new LinkedHashSet<>();
        if (texte == null) {
            return s;
        }
        for (String morceau : texte.split("[,;\\s]+")) {
            String e = morceau.trim();
            while (e.startsWith(".")) {
                e = e.substring(1);
            }
            if (!e.isEmpty()) {
                s.add(e.toLowerCase(Locale.ROOT));
            }
        }
        return s;
    }

    /** Chemin sans query string ni fragment. */
    static String sansRequete(String chemin) {
        if (chemin == null) {
            return "";
        }
        String c = chemin;
        int i = c.indexOf('#');
        if (i >= 0) {
            c = c.substring(0, i);
        }
        i = c.indexOf('?');
        if (i >= 0) {
            c = c.substring(0, i);
        }
        return c;
    }

    private static String dernierSegment(String chemin) {
        String c = sansRequete(chemin);
        while (c.endsWith("/")) {
            c = c.substring(0, c.length() - 1);
        }
        return c.substring(c.lastIndexOf('/') + 1);
    }

    /** Extension en minuscules du dernier segment, ou "". */
    public static String extension(String chemin) {
        String c = sansRequete(chemin);
        String seg = c.substring(c.lastIndexOf('/') + 1);
        int point = seg.lastIndexOf('.');
        return point < 0 || point == seg.length() - 1 ? "" : seg.substring(point + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean estRessourceStatique(RequeteVue r, Set<String> extensions) {
        String e = extension(r.getChemin());
        return !e.isEmpty() && extensions.contains(e);
    }

    /** Nombre de requêtes par domaine (minuscules), du plus fréquent au moins fréquent. */
    public static Map<String, Integer> compterDomaines(List<RequeteVue> requetes) {
        Map<String, Integer> compte = new HashMap<>();
        for (RequeteVue r : requetes) {
            compte.merge(r.getDomaine().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> l = new ArrayList<>(compte.entrySet());
        l.sort((a, b) -> a.getValue().equals(b.getValue()) ? a.getKey().compareTo(b.getKey())
                : b.getValue() - a.getValue());
        Map<String, Integer> trie = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : l) {
            trie.put(e.getKey(), e.getValue());
        }
        return trie;
    }

    public static boolean estTiersConnu(String domaine) {
        if (domaine == null) {
            return false;
        }
        String d = domaine.toLowerCase(Locale.ROOT);
        for (String t : TIERS) {
            if (d.equals(t) || d.endsWith("." + t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Une page principale ouvre une transaction : Accept contenant text/html,
     * ou GET sans Accept, et pas une ressource statique.
     */
    public static boolean estPagePrincipale(RequeteVue r, Set<String> extensions) {
        if (estRessourceStatique(r, extensions)) {
            return false;
        }
        String a = r.getAccept();
        if (a != null && a.toLowerCase(Locale.ROOT).contains("text/html")) {
            return true;
        }
        if (a != null && !a.trim().isEmpty()) {
            return false;
        }
        return "GET".equalsIgnoreCase(r.getMethode());
    }

    /** « /fr/compte/commandes.aspx?id=3 » → « commandes » ; « / » → « accueil ». */
    public static String nomDePage(RequeteVue r) {
        String seg = dernierSegment(r.getChemin());
        if (seg.isEmpty()) {
            return "accueil";
        }
        int point = seg.lastIndexOf('.');
        if (point > 0) {
            seg = seg.substring(0, point);
        }
        String n = seg.replaceAll("[^A-Za-z0-9_-]", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return n.isEmpty() ? "page" : n;
    }

    /**
     * Découpe la liste en pages : chaque page principale ouvre un groupe ; ce
     * qui précède la première forme un groupe nommé d'après la première requête.
     */
    public static List<Groupe> regrouperParPage(List<RequeteVue> requetes, Set<String> extensions) {
        List<Groupe> groupes = new ArrayList<>();
        int debut = 0;
        String page = null;
        for (int i = 0; i < requetes.size(); i++) {
            RequeteVue r = requetes.get(i);
            if (i == 0) {
                page = nomDePage(r);
            } else if (estPagePrincipale(r, extensions)) {
                groupes.add(new Groupe(debut, i, page));
                debut = i;
                page = nomDePage(r);
            }
        }
        if (!requetes.isEmpty()) {
            groupes.add(new Groupe(debut, requetes.size(), page));
        }
        return groupes;
    }

    /** « T{n}_{page} », 3, 2, « connexion » → « T03_connexion ». */
    public static String nomTransaction(String modele, int numero, int chiffres, String page) {
        String m = modele == null || modele.isEmpty() ? "{n}_{page}" : modele;
        StringBuilder n = new StringBuilder(Integer.toString(numero));
        while (n.length() < Math.max(1, chiffres)) {
            n.insert(0, '0');
        }
        return m.replace("{n}", n).replace("{page}", page == null ? "" : page);
    }
}
