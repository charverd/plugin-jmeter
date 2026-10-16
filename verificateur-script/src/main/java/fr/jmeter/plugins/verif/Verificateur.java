package fr.jmeter.plugins.verif;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jmeter.gui.tree.JMeterTreeNode;

/** Exécute toutes les règles sur un plan et trie les constats. */
public final class Verificateur {

    private Verificateur() {
    }

    /**
     * Constats de toutes les règles, triés par gravité puis dans l'ordre de
     * l'arbre. Une règle qui plante n'arrête pas les autres : elle laisse un
     * constat qui le dit.
     */
    public static List<Constat> verifier(JMeterTreeNode racine) {
        List<Constat> tous = new ArrayList<>();
        for (Regle r : Regles.toutes()) {
            List<Constat> siens = new ArrayList<>();
            try {
                r.verifier(racine, siens);
                tous.addAll(siens);
            } catch (RuntimeException e) {
                tous.add(new Constat(r.id(), Gravite.INFO, racine,
                        "la règle « " + r.libelle() + " » n'a pas pu s'exécuter : " + e,
                        "signaler ce plan au développeur du plugin"));
            }
        }
        Map<JMeterTreeNode, Integer> ordre = new HashMap<>();
        int i = 0;
        for (JMeterTreeNode n : Arbre.actifs(racine)) {
            ordre.put(n, i++);
        }
        tous.sort((a, b) -> {
            int g = a.getGravite().compareTo(b.getGravite());
            if (g != 0) {
                return g;
            }
            int o = Integer.compare(ordre.getOrDefault(a.getNoeud(), -1), ordre.getOrDefault(b.getNoeud(), -1));
            return o != 0 ? o : a.getRegle().compareTo(b.getRegle());
        });
        return tous;
    }

    /** Rapport texte, pour copier dans un ticket ou un courriel. */
    public static String rapport(List<Constat> constats) {
        int[] n = new int[Gravite.values().length];
        for (Constat c : constats) {
            n[c.getGravite().ordinal()]++;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(resume(constats)).append("\n\n");
        for (Constat c : constats) {
            sb.append('[').append(c.getRegle()).append("] ").append(c.getGravite().getLibelle()).append(" — ")
                    .append(Arbre.chemin(c.getNoeud())).append('\n')
                    .append("    ").append(c.getMessage()).append('\n');
            if (c.getConseil() != null && !c.getConseil().isEmpty()) {
                sb.append("    → ").append(c.getConseil()).append('\n');
            }
        }
        return sb.toString();
    }

    public static String resume(List<Constat> constats) {
        int[] n = new int[Gravite.values().length];
        for (Constat c : constats) {
            n[c.getGravite().ordinal()]++;
        }
        if (constats.isEmpty()) {
            return "Aucun problème trouvé.";
        }
        return n[0] + " erreur(s), " + n[1] + " avertissement(s), " + n[2] + " info(s)";
    }
}
