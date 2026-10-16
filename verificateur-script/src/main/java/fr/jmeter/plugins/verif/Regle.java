package fr.jmeter.plugins.verif;

import java.util.List;

import org.apache.jmeter.gui.tree.JMeterTreeNode;

/** Une vérification. Ne modifie jamais le plan. */
public interface Regle {

    /** Identifiant court et stable (« V01 »). */
    String id();

    /** Ce que la règle vérifie, en quelques mots. */
    String libelle();

    /**
     * Parcourt le plan et ajoute ses constats.
     *
     * @param racine   nœud racine de l'arbre (celui du plan de test ou au-dessus)
     * @param constats liste à compléter
     */
    void verifier(JMeterTreeNode racine, List<Constat> constats);
}
