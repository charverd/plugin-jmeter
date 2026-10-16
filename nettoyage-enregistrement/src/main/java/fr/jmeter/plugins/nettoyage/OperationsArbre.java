package fr.jmeter.plugins.nettoyage;

import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.MutableTreeNode;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

/**
 * Modifications de l'arbre JMeter (suppression, insertion, déplacement de
 * nœuds), enregistrées pour pouvoir être défaites dans l'ordre inverse.
 */
final class OperationsArbre {

    private interface Op {
        void defaire(JMeterTreeModel modele);
    }

    private final JMeterTreeModel modele;
    private final List<Op> faites = new ArrayList<>();

    OperationsArbre(JMeterTreeModel modele) {
        this.modele = modele;
    }

    boolean estVide() {
        return faites.isEmpty();
    }

    void supprimer(JMeterTreeNode n) {
        JMeterTreeNode parent = (JMeterTreeNode) n.getParent();
        int index = parent.getIndex(n);
        modele.removeNodeFromParent(n);
        faites.add(m -> m.insertNodeInto(n, parent, Math.min(index, parent.getChildCount())));
    }

    void inserer(JMeterTreeNode n, JMeterTreeNode parent, int index) {
        modele.insertNodeInto(n, parent, index);
        faites.add(m -> m.removeNodeFromParent(n));
    }

    void deplacer(JMeterTreeNode n, JMeterTreeNode nouveauParent) {
        JMeterTreeNode ancien = (JMeterTreeNode) n.getParent();
        int index = ancien.getIndex(n);
        modele.removeNodeFromParent(n);
        modele.insertNodeInto(n, nouveauParent, nouveauParent.getChildCount());
        faites.add(m -> {
            m.removeNodeFromParent(n);
            m.insertNodeInto((MutableTreeNode) n, ancien, Math.min(index, ancien.getChildCount()));
        });
    }

    void defaire() {
        for (int i = faites.size() - 1; i >= 0; i--) {
            faites.get(i).defaire(modele);
        }
        faites.clear();
    }
}
