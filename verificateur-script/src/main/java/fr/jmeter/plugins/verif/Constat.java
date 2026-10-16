package fr.jmeter.plugins.verif;

import org.apache.jmeter.gui.tree.JMeterTreeNode;

/** Un problème trouvé par une règle, rattaché à un élément du plan. */
public final class Constat {

    private final String regle;
    private final Gravite gravite;
    private final JMeterTreeNode noeud;
    private final String message;
    private final String conseil;

    public Constat(String regle, Gravite gravite, JMeterTreeNode noeud, String message, String conseil) {
        this.regle = regle;
        this.gravite = gravite;
        this.noeud = noeud;
        this.message = message;
        this.conseil = conseil;
    }

    /** Identifiant de la règle (« V04 »). */
    public String getRegle() {
        return regle;
    }

    public Gravite getGravite() {
        return gravite;
    }

    /** Élément concerné (pour le sélectionner dans l'arbre). */
    public JMeterTreeNode getNoeud() {
        return noeud;
    }

    public String getMessage() {
        return message;
    }

    /** Ce qu'il faut faire, en une phrase. */
    public String getConseil() {
        return conseil;
    }

    @Override
    public String toString() {
        return regle + " " + gravite + " " + Arbre.chemin(noeud) + " : " + message;
    }
}
