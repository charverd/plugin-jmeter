package fr.jmeter.plugins.correlation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Une requête enregistrée, vue comme une liste de champs porteurs de valeurs. */
public final class Requete {

    /** Où se trouve une valeur dans la requête. */
    public enum TypeChamp {
        PARAMETRE, ENTETE, CHEMIN, CORPS
    }

    /** Un champ porteur de valeur. CHEMIN : nom « segment i » ; CORPS : nom « corps ». */
    public static final class Champ {
        private final TypeChamp type;
        private final String nom;
        private final String valeur;

        public Champ(TypeChamp type, String nom, String valeur) {
            this.type = type;
            this.nom = nom == null ? "" : nom;
            this.valeur = valeur == null ? "" : valeur;
        }

        public TypeChamp getType() {
            return type;
        }

        public String getNom() {
            return nom;
        }

        public String getValeur() {
            return valeur;
        }
    }

    private static final Pattern NUMERIQUE = Pattern.compile("\\d+");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEXA = Pattern.compile("[0-9a-fA-F]{16,}");

    private final String nom;
    private final String methode;
    private final String chemin;
    private final List<Champ> champs;

    public Requete(String nom, String methode, String chemin, List<Champ> champs) {
        this.nom = nom;
        this.methode = methode == null ? "" : methode;
        this.chemin = chemin == null ? "" : chemin;
        this.champs = Collections.unmodifiableList(new ArrayList<>(champs));
    }

    public String getNom() {
        return nom;
    }

    public String getMethode() {
        return methode;
    }

    public String getChemin() {
        return chemin;
    }

    public List<Champ> getChamps() {
        return champs;
    }

    private static String sansRequete(String chemin) {
        int q = chemin.indexOf('?');
        return q < 0 ? chemin : chemin.substring(0, q);
    }

    /** « GET /cmd/*&#47;detail » : segments numériques, UUID ou hexadécimaux longs neutralisés. */
    public String cle() {
        String[] segs = sansRequete(chemin).split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            String s = segs[i];
            boolean variable = !s.isEmpty()
                    && (NUMERIQUE.matcher(s).matches() || UUID.matcher(s).matches() || HEXA.matcher(s).matches());
            sb.append(variable ? "*" : s);
        }
        return methode.toUpperCase(Locale.ROOT) + " " + sb;
    }

    /** Un champ CHEMIN par segment non vide, query string retirée. */
    public static List<Champ> segmentsDuChemin(String chemin) {
        List<Champ> l = new ArrayList<>();
        if (chemin == null) {
            return l;
        }
        int i = 0;
        for (String s : sansRequete(chemin).split("/")) {
            if (!s.isEmpty()) {
                l.add(new Champ(TypeChamp.CHEMIN, "segment " + i++, s));
            }
        }
        return l;
    }
}
