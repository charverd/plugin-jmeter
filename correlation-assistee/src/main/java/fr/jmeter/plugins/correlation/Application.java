package fr.jmeter.plugins.correlation;

import java.util.ArrayList;
import java.util.List;

import org.apache.jmeter.config.Argument;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.extractor.BoundaryExtractor;
import org.apache.jmeter.extractor.gui.BoundaryExtractorGui;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;

/** Écrit la corrélation dans l'arbre JMeter, de façon réversible. */
final class Application {

    /** Ce que l'utilisateur a décidé pour un candidat. */
    enum Action {
        EXTRAIRE("Extraire"),
        HORODATAGE("Remplacer par ${__time()}"),
        IGNORER("Ignorer");

        private final String libelle;

        Action(String libelle) {
            this.libelle = libelle;
        }

        @Override
        public String toString() {
            return libelle;
        }
    }

    /** D'où extraire une valeur : la requête dont la réponse la contient, et les bornes. */
    static final class Source {
        final int requete;
        final Bornes bornes;
        final boolean dansEntetes;

        Source(int requete, Bornes bornes, boolean dansEntetes) {
            this.requete = requete;
            this.bornes = bornes;
            this.dansEntetes = dansEntetes;
        }
    }

    private final List<Runnable> annulations = new ArrayList<>();
    private int extracteurs;
    private int remplacements;

    /**
     * Première réponse, parmi celles des requêtes qui précèdent la première
     * utilisation, qui contient la valeur (corps d'abord, puis en-têtes).
     */
    static Source chercherSource(Correlation.Candidat c, List<LectureJtl.Reponse> reponsesDeA) {
        for (int i = 0; i < c.premierUsage() && i < reponsesDeA.size(); i++) {
            LectureJtl.Reponse r = reponsesDeA.get(i);
            if (r == null) {
                continue;
            }
            Bornes b = Bornes.calculer(r.getCorps(), c.getValeurA());
            if (b != null) {
                return new Source(i, b, false);
            }
            b = Bornes.calculer(r.getEntetes(), c.getValeurA());
            if (b != null) {
                return new Source(i, b, true);
            }
        }
        return null;
    }

    int getExtracteurs() {
        return extracteurs;
    }

    int getRemplacements() {
        return remplacements;
    }

    /** Ajoute un Boundary Extractor sous la requête source. */
    void extraire(JMeterTreeNode requeteSource, String variable, Source s) {
        GuiPackage gp = GuiPackage.getInstance();
        BoundaryExtractor ex = (BoundaryExtractor) gp.createTestElement(BoundaryExtractorGui.class,
                BoundaryExtractor.class);
        ex.setName("Corrélation " + variable);
        ex.setRefName(variable);
        ex.setLeftBoundary(s.bornes.getGauche());
        ex.setRightBoundary(s.bornes.getDroite());
        ex.setMatchNumber(1);
        ex.setDefaultValue("NON_TROUVE_" + variable);
        ex.setUseField(s.dansEntetes ? "true" : "false");
        JMeterTreeModel modele = gp.getTreeModel();
        JMeterTreeNode n = new JMeterTreeNode(ex, modele);
        modele.insertNodeInto(n, requeteSource, requeteSource.getChildCount());
        annulations.add(() -> modele.removeNodeFromParent(n));
        extracteurs++;
    }

    /**
     * Remplace {@code valeur} par {@code par} dans les requêtes données :
     * chemin, valeurs des paramètres (et corps brut), valeurs des en-têtes.
     */
    void remplacer(List<JMeterTreeNode> requetes, String valeur, String par) {
        for (JMeterTreeNode n : requetes) {
            HTTPSamplerBase s = (HTTPSamplerBase) n.getTestElement();
            String chemin = s.getPropertyAsString(HTTPSamplerBase.PATH);
            if (chemin.contains(valeur)) {
                s.setProperty(HTTPSamplerBase.PATH, chemin.replace(valeur, par));
                annulations.add(() -> s.setProperty(HTTPSamplerBase.PATH, chemin));
                remplacements++;
            }
            Arguments args = s.getArguments();
            for (int i = 0; i < args.getArgumentCount(); i++) {
                Argument a = args.getArgument(i);
                String v = a.getValue();
                if (v != null && v.contains(valeur)) {
                    a.setValue(v.replace(valeur, par));
                    annulations.add(() -> a.setValue(v));
                    remplacements++;
                }
            }
            for (HeaderManager hm : LiaisonArbre.entetes(n)) {
                for (int i = 0; i < hm.size(); i++) {
                    Header h = hm.get(i);
                    String v = h.getValue();
                    if (v != null && v.contains(valeur) && !"Cookie".equalsIgnoreCase(h.getName())) {
                        h.setValue(v.replace(valeur, par));
                        annulations.add(() -> h.setValue(v));
                        remplacements++;
                    }
                }
            }
        }
    }

    boolean estVide() {
        return annulations.isEmpty();
    }

    void defaire() {
        for (int i = annulations.size() - 1; i >= 0; i--) {
            annulations.get(i).run();
        }
        annulations.clear();
    }
}
