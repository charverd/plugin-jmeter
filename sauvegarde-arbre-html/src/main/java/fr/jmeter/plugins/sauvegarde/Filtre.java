package fr.jmeter.plugins.sauvegarde;

/**
 * Réglages du filtre de l'arbre de résultats filtrable au moment de la sauvegarde.
 * La page HTML les reprend pour afficher d'abord ce que l'utilisateur voyait.
 */
public final class Filtre {

    final String motif;
    final boolean regex;
    final boolean sensibleCasse;
    final boolean exclure;
    final boolean transactionsSeules;

    public Filtre(String motif, boolean regex, boolean sensibleCasse, boolean exclure, boolean transactionsSeules) {
        this.motif = motif == null ? "" : motif;
        this.regex = regex;
        this.sensibleCasse = sensibleCasse;
        this.exclure = exclure;
        this.transactionsSeules = transactionsSeules;
    }

    public boolean estActif() {
        return !motif.isEmpty();
    }

    /** Description courte pour l'en-tête de la page et le message de fin. */
    public String description() {
        StringBuilder sb = new StringBuilder("« ").append(motif).append(" »");
        if (regex) {
            sb.append(", regex");
        }
        if (sensibleCasse) {
            sb.append(", casse respectée");
        }
        if (exclure) {
            sb.append(", exclusion");
        }
        if (transactionsSeules) {
            sb.append(", transactions seules");
        }
        return sb.toString();
    }
}
