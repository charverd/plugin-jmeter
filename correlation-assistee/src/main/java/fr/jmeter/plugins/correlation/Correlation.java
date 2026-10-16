package fr.jmeter.plugins.correlation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.jmeter.plugins.correlation.Requete.Champ;
import fr.jmeter.plugins.correlation.Requete.TypeChamp;

/** Alignement de deux enregistrements et détection des valeurs dynamiques. */
public final class Correlation {

    /** Nature d'une valeur, pour proposer l'action adaptée. */
    public enum Nature {
        JETON, UUID, NOMBRE, HORODATAGE, TEXTE
    }

    /** Un endroit de A où une valeur est envoyée. */
    public static final class Usage {
        private final int requete;
        private final TypeChamp type;
        private final String nom;

        public Usage(int requete, TypeChamp type, String nom) {
            this.requete = requete;
            this.type = type;
            this.nom = nom;
        }

        public int getRequete() {
            return requete;
        }

        public TypeChamp getType() {
            return type;
        }

        public String getNom() {
            return nom;
        }
    }

    /** Une valeur dynamique et tous les endroits de A qui l'envoient. */
    public static final class Candidat {
        private final String valeurA;
        private final String valeurB;
        private String nomPropose;
        private final List<Usage> usages = new ArrayList<>();

        Candidat(String valeurA, String valeurB) {
            this.valeurA = valeurA;
            this.valeurB = valeurB;
        }

        public String getValeurA() {
            return valeurA;
        }

        public String getValeurB() {
            return valeurB;
        }

        public String getNomPropose() {
            return nomPropose;
        }

        public Nature getNature() {
            return nature(valeurA);
        }

        public List<Usage> getUsages() {
            return Collections.unmodifiableList(usages);
        }

        /** Indice dans A de la première requête qui envoie la valeur. */
        public int premierUsage() {
            return usages.isEmpty() ? Integer.MAX_VALUE : usages.get(0).getRequete();
        }
    }

    private static final Set<String> ENTETES_IGNORES = new HashSet<>(Arrays.asList("cookie", "content-length",
            "host", "referer", "user-agent", "accept-language", "if-modified-since", "if-none-match"));
    private static final Pattern JETON_CORPS = Pattern.compile("[A-Za-z0-9_\\-+/=.%]{4,}");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HORODATAGE = Pattern.compile("1(\\d{9}|\\d{12})");
    private static final Pattern NOMBRE = Pattern.compile("\\d+");
    private static final Pattern JETON = Pattern.compile("[A-Za-z0-9_\\-+/=%]{16,}");

    private Correlation() {
    }

    static Nature nature(String v) {
        if (UUID.matcher(v).matches()) {
            return Nature.UUID;
        }
        if (HORODATAGE.matcher(v).matches()) {
            return Nature.HORODATAGE;
        }
        if (NOMBRE.matcher(v).matches()) {
            return Nature.NOMBRE;
        }
        if (JETON.matcher(v).matches()) {
            return Nature.JETON;
        }
        return Nature.TEXTE;
    }

    /** Plus longue sous-suite commune sur {@link Requete#cle()} : paires (indice A, indice B). */
    public static List<int[]> aligner(List<Requete> a, List<Requete> b) {
        int n = a.size();
        int m = b.size();
        int[][] t = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                t[i][j] = a.get(i).cle().equals(b.get(j).cle()) ? t[i + 1][j + 1] + 1
                        : Math.max(t[i + 1][j], t[i][j + 1]);
            }
        }
        List<int[]> paires = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).cle().equals(b.get(j).cle())) {
                paires.add(new int[] { i, j });
                i++;
                j++;
            } else if (t[i + 1][j] >= t[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return paires;
    }

    /** Au moins 4 caractères : en dessous, trop de valeurs coïncideraient par hasard. */
    private static boolean estSignificative(String v) {
        return v.length() >= 4;
    }

    private static boolean ignore(Champ c) {
        return c.getType() == TypeChamp.ENTETE && ENTETES_IGNORES.contains(c.getNom().toLowerCase(Locale.ROOT));
    }

    private static List<String> jetonsDuCorps(String corps) {
        List<String> l = new ArrayList<>();
        Matcher m = JETON_CORPS.matcher(corps);
        while (m.find()) {
            l.add(m.group());
        }
        return l;
    }

    /** Champs de même type et même nom, appariés dans l'ordre d'apparition. */
    private static Map<String, List<Champ>> parCle(Requete r) {
        Map<String, List<Champ>> m = new LinkedHashMap<>();
        for (Champ c : r.getChamps()) {
            m.computeIfAbsent(c.getType() + "\u0000" + c.getNom(), k -> new ArrayList<>()).add(c);
        }
        return m;
    }

    public static List<Candidat> detecter(List<Requete> a, List<Requete> b) {
        Map<String, Candidat> parValeur = new LinkedHashMap<>();
        for (int[] p : aligner(a, b)) {
            Map<String, List<Champ>> ca = parCle(a.get(p[0]));
            Map<String, List<Champ>> cb = parCle(b.get(p[1]));
            for (Map.Entry<String, List<Champ>> e : ca.entrySet()) {
                List<Champ> lb = cb.get(e.getKey());
                if (lb == null) {
                    continue;
                }
                for (int k = 0; k < Math.min(e.getValue().size(), lb.size()); k++) {
                    Champ x = e.getValue().get(k);
                    Champ y = lb.get(k);
                    if (ignore(x)) {
                        continue;
                    }
                    if (x.getType() == TypeChamp.CORPS) {
                        List<String> ja = jetonsDuCorps(x.getValeur());
                        List<String> jb = jetonsDuCorps(y.getValeur());
                        for (int t = 0; t < Math.min(ja.size(), jb.size()); t++) {
                            noter(parValeur, ja.get(t), jb.get(t));
                        }
                    } else {
                        noter(parValeur, x.getValeur(), y.getValeur());
                    }
                }
            }
        }
        List<Candidat> candidats = new ArrayList<>(parValeur.values());
        for (Candidat c : candidats) {
            completerUsages(c, a);
        }
        candidats.removeIf(c -> c.usages.isEmpty());
        candidats.sort((x, y) -> x.premierUsage() != y.premierUsage()
                ? Integer.compare(x.premierUsage(), y.premierUsage()) : x.valeurA.compareTo(y.valeurA));
        Map<String, Integer> pris = new HashMap<>();
        for (Candidat c : candidats) {
            String base = nomDeBase(c);
            int n = pris.merge(base, 1, Integer::sum);
            c.nomPropose = n == 1 ? base : base + "_" + n;
        }
        return candidats;
    }

    private static void noter(Map<String, Candidat> parValeur, String va, String vb) {
        if (va.equals(vb) || !estSignificative(va)) {
            return;
        }
        if (!parValeur.containsKey(va)) {
            parValeur.put(va, new Candidat(va, vb));
        }
    }

    /** Tous les endroits de A qui envoient la valeur (égalité, ou contenue dans le corps). */
    private static void completerUsages(Candidat c, List<Requete> a) {
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < a.size(); i++) {
            for (Champ ch : a.get(i).getChamps()) {
                if (ignore(ch)) {
                    continue;
                }
                boolean envoie = ch.getType() == TypeChamp.CORPS ? ch.getValeur().contains(c.valeurA)
                        : ch.getValeur().equals(c.valeurA);
                if (envoie && vus.add(i + "\u0000" + ch.getType() + "\u0000" + ch.getNom())) {
                    c.usages.add(new Usage(i, ch.getType(), ch.getNom()));
                }
            }
        }
    }

    private static String nomDeBase(Candidat c) {
        Usage u = c.usages.get(0);
        String n;
        if (u.getType() == TypeChamp.CHEMIN) {
            n = "id_chemin";
        } else if (u.getType() == TypeChamp.CORPS) {
            n = "valeur_corps";
        } else {
            n = u.getNom().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_")
                    .replaceAll("^_+|_+$", "");
        }
        if (n.isEmpty() || Character.isDigit(n.charAt(0))) {
            n = "c_" + (n.isEmpty() ? "valeur" : n);
        }
        return n;
    }
}
