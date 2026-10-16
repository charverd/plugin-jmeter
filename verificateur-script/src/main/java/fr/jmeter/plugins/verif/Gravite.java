package fr.jmeter.plugins.verif;

/** Gravité d'un constat, de la plus forte à la plus faible. */
public enum Gravite {
    /** Le tir échouera ou donnera des résultats faux. */
    ERREUR("Erreur"),
    /** Le tir passera, mais probablement pas comme prévu. */
    AVERTISSEMENT("Avertissement"),
    /** Bonne pratique non suivie. */
    INFO("Info");

    private final String libelle;

    Gravite(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
