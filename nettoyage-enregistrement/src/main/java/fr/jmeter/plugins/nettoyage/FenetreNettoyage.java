package fr.jmeter.plugins.nettoyage;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.TreePath;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

import fr.jmeter.plugins.nettoyage.AnalyseEnregistrement.Groupe;

/**
 * Fenêtre « Nettoyer un enregistrement » : agit sur les éléments
 * sélectionnés dans l'arbre (en général le Recording Controller).
 */
public class FenetreNettoyage extends JDialog {

    private static final long serialVersionUID = 1L;

    private static FenetreNettoyage instance;

    private final JLabel lblSelection = new JLabel(" ");
    private final JCheckBox caseStatiques = new JCheckBox("Supprimer les ressources statiques, extensions :", true);
    private final JTextField champExtensions = new JTextField(
            String.join(", ", AnalyseEnregistrement.EXTENSIONS_STATIQUES_PAR_DEFAUT), 45);
    private final ModeleDomaines modeleDomaines = new ModeleDomaines();
    private final JCheckBox caseRegrouper = new JCheckBox("Regrouper en transactions, une par page", true);
    private final JTextField champModele = new JTextField("T{n}_{page}", 14);
    private final JSpinner spinChiffres = new JSpinner(new SpinnerNumberModel(2, 1, 6, 1));
    private final JCheckBox caseParent = new JCheckBox("« Generate parent sample »", true);
    private final JTextArea apercu = new JTextArea(12, 80);
    private final JButton boutonAppliquer = new JButton("Appliquer");
    private final JButton boutonDefaire = new JButton("Annuler le nettoyage");

    private List<JMeterTreeNode> conteneurs = new ArrayList<>();
    private Nettoyage.Plan plan;
    private OperationsArbre dernier;

    public static void ouvrir() {
        GuiPackage gp = GuiPackage.getInstance();
        if (gp == null) {
            return;
        }
        if (instance == null) {
            instance = new FenetreNettoyage(gp.getMainFrame());
        }
        instance.relireSelection();
        instance.setVisible(true);
        instance.toFront();
    }

    private FenetreNettoyage(Window parent) {
        super(parent, "Nettoyer un enregistrement", ModalityType.MODELESS);

        JPanel reglages = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.insets = new Insets(2, 2, 2, 2);
        JButton relire = new JButton("Reprendre la sélection de l'arbre");
        relire.addActionListener(e -> relireSelection());
        JPanel ligneSel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ligneSel.add(lblSelection);
        ligneSel.add(relire);
        reglages.add(ligneSel, g);

        g.gridy++;
        JPanel ligneStat = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ligneStat.add(caseStatiques);
        ligneStat.add(champExtensions);
        reglages.add(ligneStat, g);

        g.gridy++;
        reglages.add(new JLabel("Domaines trouvés (cocher ceux à supprimer ; les tiers connus sont précochés) :"), g);
        g.gridy++;
        JTable tableDomaines = SansHtml.table(new JTable(modeleDomaines));
        tableDomaines.getColumnModel().getColumn(0).setMaxWidth(70);
        tableDomaines.getColumnModel().getColumn(2).setMaxWidth(90);
        tableDomaines.getColumnModel().getColumn(3).setMaxWidth(90);
        JScrollPane defil = new JScrollPane(tableDomaines);
        defil.setPreferredSize(new java.awt.Dimension(700, 120));
        reglages.add(defil, g);

        g.gridy++;
        JPanel ligneGroupe = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ligneGroupe.add(caseRegrouper);
        ligneGroupe.add(new JLabel("nom :"));
        ligneGroupe.add(champModele);
        ligneGroupe.add(new JLabel("{n} sur"));
        ligneGroupe.add(spinChiffres);
        ligneGroupe.add(new JLabel("chiffres"));
        ligneGroupe.add(caseParent);
        champModele.setToolTipText("{n} = numéro, {page} = nom tiré de l'URL de la page");
        reglages.add(ligneGroupe, g);
        reglages.setBorder(BorderFactory.createTitledBorder("Réglages"));

        apercu.setEditable(false);
        JPanel panApercu = new JPanel(new BorderLayout(4, 4));
        panApercu.setBorder(BorderFactory.createTitledBorder("Aperçu"));
        panApercu.add(new JScrollPane(apercu), BorderLayout.CENTER);
        JButton boutonApercu = new JButton("Aperçu");
        boutonApercu.addActionListener(e -> preparer());
        boutonAppliquer.addActionListener(e -> appliquer());
        boutonAppliquer.setEnabled(false);
        boutonDefaire.addActionListener(e -> defaire());
        boutonDefaire.setEnabled(false);
        JButton fermer = new JButton("Fermer");
        fermer.addActionListener(e -> setVisible(false));
        JPanel boutons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        boutons.add(boutonApercu);
        boutons.add(boutonAppliquer);
        boutons.add(boutonDefaire);
        boutons.add(fermer);
        panApercu.add(boutons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, reglages, panApercu);
        split.setResizeWeight(0.4);
        JPanel contenu = new JPanel(new BorderLayout());
        contenu.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        contenu.add(split, BorderLayout.CENTER);
        setContentPane(contenu);
        setSize(1000, 720);
        setLocationRelativeTo(parent);
    }

    private void relireSelection() {
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        JMeterTreeNode[] sel = gp.getTreeListener().getSelectedNodes();
        conteneurs = sel == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(sel));
        List<Nettoyage.Requete> requetes = Nettoyage.lire(conteneurs);
        lblSelection.setText(conteneurs.size() + " élément(s) sélectionné(s), " + requetes.size() + " requête(s) HTTP");
        List<AnalyseEnregistrement.RequeteVue> vues = new ArrayList<>();
        for (Nettoyage.Requete q : requetes) {
            vues.add(q.vue);
        }
        modeleDomaines.remplir(AnalyseEnregistrement.compterDomaines(vues));
        plan = null;
        apercu.setText(requetes.isEmpty()
                ? "Sélectionner dans l'arbre l'enregistrement à nettoyer (Recording Controller, groupe d'unités…),"
                        + " puis « Reprendre la sélection de l'arbre »."
                : "Régler, puis « Aperçu ».");
        boutonAppliquer.setEnabled(false);
    }

    private Nettoyage.Reglages reglages() {
        Nettoyage.Reglages r = new Nettoyage.Reglages();
        r.supprimerStatiques = caseStatiques.isSelected();
        r.extensions = AnalyseEnregistrement.lireExtensions(champExtensions.getText());
        r.domainesASupprimer = modeleDomaines.coches();
        r.regrouper = caseRegrouper.isSelected();
        r.modele = champModele.getText();
        r.chiffres = (Integer) spinChiffres.getValue();
        r.parentSample = caseParent.isSelected();
        return r;
    }

    private void preparer() {
        GuiPackage.getInstance().updateCurrentNode();
        Nettoyage.Reglages r = reglages();
        plan = Nettoyage.preparer(conteneurs, r);
        StringBuilder sb = new StringBuilder();
        sb.append(plan.aSupprimer.size()).append(" requête(s) supprimée(s), ")
                .append(Nettoyage.nombreDeTransactions(plan)).append(" transaction(s) créée(s)\n\n");
        for (int i = 0; i < plan.aSupprimer.size(); i++) {
            Nettoyage.Requete q = plan.aSupprimer.get(i);
            sb.append("Supprimer  ").append(q.noeud.getName()).append("   — ").append(plan.raisons.get(i)).append('\n');
        }
        int numero = 0;
        for (int c = 0; c < plan.conteneurs.size(); c++) {
            for (Groupe gr : plan.groupes.get(c)) {
                numero++;
                sb.append("Transaction  ")
                        .append(AnalyseEnregistrement.nomTransaction(r.modele, numero, r.chiffres, gr.getPage()))
                        .append("   ← ").append(gr.taille()).append(" requête(s) : ");
                List<String> noms = new ArrayList<>();
                for (int k = gr.getDebut(); k < gr.getFin(); k++) {
                    noms.add(plan.requetesDirectes.get(c).get(k).noeud.getName());
                }
                sb.append(String.join(", ", noms)).append('\n');
            }
        }
        if (plan.dejaEnTransaction > 0) {
            sb.append('\n').append(plan.dejaEnTransaction)
                    .append(" requête(s) déjà dans une transaction : laissées telles quelles.\n");
        }
        apercu.setText(sb.toString());
        apercu.setCaretPosition(0);
        boutonAppliquer.setEnabled(!plan.aSupprimer.isEmpty() || Nettoyage.nombreDeTransactions(plan) > 0);
    }

    private void appliquer() {
        if (plan == null) {
            return;
        }
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        // L'écran ouvert pourrait être celui d'une requête supprimée : on se replace sur le conteneur.
        if (!conteneurs.isEmpty()) {
            gp.getTreeListener().getJTree().setSelectionPath(new TreePath(conteneurs.get(0).getPath()));
        }
        int supprimees = plan.aSupprimer.size();
        int transactions = Nettoyage.nombreDeTransactions(plan);
        dernier = Nettoyage.appliquer(plan, reglages());
        gp.setDirty(true);
        for (JMeterTreeNode c : conteneurs) {
            gp.getTreeListener().getJTree().expandPath(new TreePath(c.getPath()));
        }
        plan = null;
        boutonAppliquer.setEnabled(false);
        boutonDefaire.setEnabled(!dernier.estVide());
        apercu.setText("Fait : " + supprimees + " requête(s) supprimée(s), " + transactions
                + " transaction(s) créée(s).\n« Annuler le nettoyage » remet tout en place tant que la fenêtre est ouverte.");
    }

    private void defaire() {
        if (dernier == null) {
            return;
        }
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        if (!conteneurs.isEmpty()) {
            gp.getTreeListener().getJTree().setSelectionPath(new TreePath(conteneurs.get(0).getPath()));
        }
        dernier.defaire();
        dernier = null;
        gp.setDirty(true);
        boutonDefaire.setEnabled(false);
        relireSelection();
        apercu.setText("Nettoyage annulé.");
    }

    private static final class ModeleDomaines extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final List<String> domaines = new ArrayList<>();
        private final List<Integer> nombres = new ArrayList<>();
        private final List<Boolean> coches = new ArrayList<>();

        void remplir(Map<String, Integer> compte) {
            domaines.clear();
            nombres.clear();
            coches.clear();
            for (Map.Entry<String, Integer> e : compte.entrySet()) {
                domaines.add(e.getKey());
                nombres.add(e.getValue());
                coches.add(AnalyseEnregistrement.estTiersConnu(e.getKey()));
            }
            fireTableDataChanged();
        }

        Set<String> coches() {
            Set<String> s = new LinkedHashSet<>();
            for (int i = 0; i < domaines.size(); i++) {
                if (coches.get(i)) {
                    s.add(domaines.get(i));
                }
            }
            return s;
        }

        @Override
        public int getRowCount() {
            return domaines.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int c) {
            return new String[] { "Supprimer", "Domaine", "Requêtes", "Tiers connu" }[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : c == 2 ? Integer.class : String.class;
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c == 0;
        }

        @Override
        public Object getValueAt(int r, int c) {
            switch (c) {
            case 0:
                return coches.get(r);
            case 1:
                return domaines.get(r).isEmpty() ? "(sans domaine)" : domaines.get(r);
            case 2:
                return nombres.get(r);
            default:
                return AnalyseEnregistrement.estTiersConnu(domaines.get(r)) ? "oui" : "";
            }
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            if (c == 0) {
                coches.set(r, Boolean.TRUE.equals(v));
                fireTableCellUpdated(r, c);
            }
        }
    }
}
