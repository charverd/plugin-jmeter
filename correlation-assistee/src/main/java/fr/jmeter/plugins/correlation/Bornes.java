package fr.jmeter.plugins.correlation;

/** Bornes gauche et droite d'un Boundary Extractor. */
public final class Bornes {

    private static final String FINS = "\"'<>&;,) \t\r\n";
    private static final int GAUCHE_MIN = 3;
    private static final int GAUCHE_MAX = 40;
    private static final int DROITE_MAX = 3;

    private final String gauche;
    private final String droite;

    Bornes(String gauche, String droite) {
        this.gauche = gauche;
        this.droite = droite;
    }

    public String getGauche() {
        return gauche;
    }

    public String getDroite() {
        return droite;
    }

    /**
     * Bornes pour extraire la première occurrence de {@code valeur} dans
     * {@code reponse}, ou {@code null} si elle n'y est pas. La borne gauche est
     * le plus court contexte (3 à 40 caractères) dont la première occurrence
     * précède exactement la valeur ; la borne droite, le caractère qui suit,
     * prolongé des caractères de fin usuels (3 au plus).
     */
    public static Bornes calculer(String reponse, String valeur) {
        if (reponse == null || valeur == null || valeur.isEmpty()) {
            return null;
        }
        int pos = reponse.indexOf(valeur);
        if (pos < 0) {
            return null;
        }
        int apres = pos + valeur.length();
        int finDroite = apres;
        if (finDroite < reponse.length()) {
            finDroite++;
            while (finDroite < reponse.length() && finDroite - apres < DROITE_MAX
                    && FINS.indexOf(reponse.charAt(finDroite)) >= 0) {
                finDroite++;
            }
        }
        String droite = reponse.substring(apres, finDroite);

        String gauche = null;
        for (int n = GAUCHE_MIN; n <= Math.min(GAUCHE_MAX, pos); n++) {
            String g = reponse.substring(pos - n, pos);
            String essai = sansBlancsDeTete(g);
            if (essai.isEmpty()) {
                continue;
            }
            int premiere = reponse.indexOf(essai);
            if (premiere + essai.length() == pos) {
                gauche = sansBlancsDeTete(reponse.substring(pos - lisible(reponse, pos, n), pos));
                break;
            }
        }
        if (gauche == null) {
            gauche = sansBlancsDeTete(reponse.substring(Math.max(0, pos - GAUCHE_MAX), pos));
        }
        return new Bornes(gauche, droite);
    }

    /**
     * Allonge une borne gauche unique pour qu'elle se lise : au moins une
     * lettre ou un chiffre, et commencer au début d'un mot (« value=" »
     * plutôt que « ue=" »). Allonger à gauche garde l'unicité.
     */
    private static int lisible(String reponse, int pos, int n) {
        int l = n;
        while (l < GAUCHE_MAX && pos - l > 0 && !contientAlphanum(reponse.substring(pos - l, pos))) {
            l++;
        }
        while (l < GAUCHE_MAX && pos - l > 0 && estMot(reponse.charAt(pos - l - 1))
                && estMot(reponse.charAt(pos - l))) {
            l++;
        }
        return l;
    }

    private static boolean contientAlphanum(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean estMot(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static String sansBlancsDeTete(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }
}
