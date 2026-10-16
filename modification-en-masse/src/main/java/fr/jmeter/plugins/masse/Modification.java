package fr.jmeter.plugins.masse;

import java.util.function.Supplier;

import org.apache.jmeter.gui.tree.JMeterTreeNode;

/**
 * Une modification prévue sur un élément du plan : ce qu'il contient,
 * ce qu'il contiendra, et comment l'écrire puis le défaire.
 */
public final class Modification {

    /** Texte affiché dans l'aperçu pour un en-tête qui n'existe pas encore ou plus. */
    public static final String ABSENT = "(absent)";
    public static final String SUPPRIME = "(supprimé)";

    private final JMeterTreeNode noeud;
    private final String element;
    private final String champ;
    private final String avant;
    private final String apres;
    private final Supplier<String> lire;
    private final Runnable ecrire;
    private final Runnable defaire;
    private boolean cochee = true;

    Modification(JMeterTreeNode noeud, String element, String champ, String avant, String apres,
            Supplier<String> lire, Runnable ecrire, Runnable defaire) {
        this.noeud = noeud;
        this.element = element;
        this.champ = champ;
        this.avant = avant;
        this.apres = apres;
        this.lire = lire;
        this.ecrire = ecrire;
        this.defaire = defaire;
    }

    public JMeterTreeNode getNoeud() {
        return noeud;
    }

    /** Chemin lisible de l'élément dans l'arbre (« TC_01 > /login-3 > HTTP Header Manager »). */
    public String getElement() {
        return element;
    }

    public String getChamp() {
        return champ;
    }

    public String getAvant() {
        return avant;
    }

    public String getApres() {
        return apres;
    }

    public boolean isCochee() {
        return cochee;
    }

    public void setCochee(boolean cochee) {
        this.cochee = cochee;
    }

    /** L'élément contient-il toujours la valeur vue lors de l'aperçu ? */
    boolean estAJour() {
        String actuel = lire.get();
        return actuel == null ? ABSENT.equals(avant) : actuel.equals(avant);
    }

    void ecrire() {
        ecrire.run();
    }

    void defaire() {
        defaire.run();
    }
}
