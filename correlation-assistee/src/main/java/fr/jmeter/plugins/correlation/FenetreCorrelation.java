package fr.jmeter.plugins.correlation;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.util.JMeterUtils;

/**
 * Fenêtre « Corrélation assistée ». On sélectionne dans l'arbre les deux
 * enregistrements du même parcours (A puis B, dans l'ordre de l'arbre),
 * on donne le fichier des réponses de A, on analyse, on choisit, on applique.
 */
public class FenetreCorrelation extends JDialog {

    private static final long serialVersionUID = 1L;

    private static FenetreCorrelation instance;

    private final JLabel lblA = new JLabel(" ");
    private final JLabel lblB = new JLabel(" ");
    private final JTextField champJtl = new JTextField(45);
    private final Modele modele = new Modele();
    private final JTable table = new JTable(modele);
    private final JTextArea detail = new JTextArea(4, 80);
    private final JLabel bilan = new JLabel(" ");
    private final JButton boutonAppliquer = new JButton("Appliquer les lignes cochées");
    private final JButton boutonDefaire = new JButton("Annuler la dernière application");

    private JMeterTreeNode noeudA;
    private JMeterTreeNode noeudB;
    private List<JMeterTreeNode> requetesA = Collections.emptyList();
    private Application derniere;

    public static void ouvrir() {
        GuiPackage gp = GuiPackage.getInstance();
        if (gp == null) {
            return;
        }
        if (instance == null) {
            instance = new FenetreCorrelation(gp.getMainFrame());
        }
        instance.relireSelection();
        instance.setVisible(true);
        instance.toFront();
    }

    private FenetreCorrelation(Window parent) {
        super(parent, "Corrélation assistée", ModalityType.MODELESS);

        JPanel haut = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(2, 2, 2, 2);
        haut.add(new JLabel("Enregistrement A (à corriger) :"), g);
        g.gridx = 1;
        haut.add(lblA, g);
        g.gridx = 0;
        g.gridy++;
        haut.add(new JLabel("Enregistrement B (même parcours) :"), g);
        g.gridx = 1;
        haut.add(lblB, g);
        g.gridx = 0;
        g.gridy++;
        haut.add(new JLabel("Réponses de A (fichier JTL XML) :"), g);
        g.gridx = 1;
        JPanel ligneJtl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ligneJtl.add(champJtl);
        JButton parcourir = new JButton("Parcourir…");
        parcourir.addActionListener(e -> choisirJtl());
        ligneJtl.add(parcourir);
        haut.add(ligneJtl, g);
        g.gridx = 1;
        g.gridy++;
        JPanel boutonsHaut = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton relire = new JButton("Reprendre la sélection de l'arbre");
        relire.addActionListener(e -> relireSelection());
        JButton preparer = new JButton("Préparer l'enregistreur…");
        preparer.setToolTipText("Ajoute sous l'enregistreur un récepteur qui sauve les réponses au bon format");
        preparer.addActionListener(e -> preparerEnregistreur());
        JButton analyser = new JButton("Analyser");
        analyser.addActionListener(e -> analyser());
        boutonsHaut.add(relire);
        boutonsHaut.add(preparer);
        boutonsHaut.add(analyser);
        haut.add(boutonsHaut, g);
        haut.setBorder(BorderFactory.createTitledBorder("Enregistrements"));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setMaxWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(220);
        table.getColumnModel().getColumn(6).setPreferredWidth(170);
        table.getColumnModel().getColumn(6)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(Application.Action.values())));
        table.getSelectionModel().addListSelectionListener(e -> montrerDetail());
        detail.setEditable(false);
        detail.setLineWrap(true);

        boutonAppliquer.addActionListener(e -> appliquer());
        boutonAppliquer.setEnabled(false);
        boutonDefaire.addActionListener(e -> defaire());
        boutonDefaire.setEnabled(false);
        JButton fermer = new JButton("Fermer");
        fermer.addActionListener(e -> setVisible(false));
        JPanel boutons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        boutons.add(bilan);
        boutons.add(boutonAppliquer);
        boutons.add(boutonDefaire);
        boutons.add(fermer);
        JPanel bas = new JPanel(new BorderLayout(4, 4));
        bas.add(new JScrollPane(detail), BorderLayout.CENTER);
        bas.add(boutons, BorderLayout.SOUTH);

        JPanel contenu = new JPanel(new BorderLayout(4, 4));
        contenu.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        contenu.add(haut, BorderLayout.NORTH);
        contenu.add(new JScrollPane(table), BorderLayout.CENTER);
        contenu.add(bas, BorderLayout.SOUTH);
        setContentPane(contenu);
        setSize(1100, 650);
        setLocationRelativeTo(parent);
    }

    private void relireSelection() {
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        JMeterTreeNode[] sel = gp.getTreeListener().getSelectedNodes();
        noeudA = sel != null && sel.length >= 1 ? sel[0] : null;
        noeudB = sel != null && sel.length >= 2 ? sel[1] : null;
        if (sel != null && sel.length == 2) {
            // Ordre de l'arbre : A est celui qui vient en premier.
            int ia = gp.getTreeListener().getJTree().getRowForPath(new javax.swing.tree.TreePath(sel[0].getPath()));
            int ib = gp.getTreeListener().getJTree().getRowForPath(new javax.swing.tree.TreePath(sel[1].getPath()));
            if (ib >= 0 && ia > ib) {
                JMeterTreeNode t = noeudA;
                noeudA = noeudB;
                noeudB = t;
            }
        }
        lblA.setText(noeudA == null ? "(sélectionner deux enregistrements dans l'arbre, Ctrl+clic)" : decrire(noeudA));
        lblB.setText(noeudB == null ? "—" : decrire(noeudB));
        modele.remplir(Collections.<Ligne>emptyList());
        boutonAppliquer.setEnabled(false);
    }

    private static String decrire(JMeterTreeNode n) {
        return n.getName() + " (" + LiaisonArbre.requetes(n).size() + " requêtes)";
    }

    private void choisirJtl() {
        JFileChooser c = new JFileChooser(JMeterUtils.getPropDefault("user.dir", "."));
        if (c.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            champJtl.setText(c.getSelectedFile().getAbsolutePath());
        }
    }

    private void preparerEnregistreur() {
        String f = JOptionPane.showInputDialog(this,
                "Fichier où écrire les réponses de l'enregistrement A :", "reponses-enregistrement-A.jtl");
        if (f == null || f.trim().isEmpty()) {
            return;
        }
        int n = PreparationEnregistreur.preparer(f.trim());
        if (n == 0) {
            JOptionPane.showMessageDialog(this, "Aucun enregistreur HTTP(S) dans le plan : en ajouter un d'abord.");
            return;
        }
        champJtl.setText(new File(f.trim()).getAbsolutePath());
        JOptionPane.showMessageDialog(this, "Récepteur « " + PreparationEnregistreur.NOM + " » ajouté sous "
                + n + " enregistreur(s).\nEnregistrer le parcours A, puis désactiver ce récepteur avant"
                + " d'enregistrer le parcours B.");
    }

    private void analyser() {
        if (noeudA == null || noeudB == null) {
            bilan.setText("Sélectionner les deux enregistrements dans l'arbre (Ctrl+clic), puis « Reprendre ».");
            return;
        }
        GuiPackage.getInstance().updateCurrentNode();
        requetesA = LiaisonArbre.requetes(noeudA);
        List<Requete> a = LiaisonArbre.versRequetes(requetesA);
        List<Requete> b = LiaisonArbre.versRequetes(LiaisonArbre.requetes(noeudB));
        List<LectureJtl.Reponse> reponses = Collections.emptyList();
        String erreurJtl = null;
        if (!champJtl.getText().trim().isEmpty()) {
            try {
                reponses = LiaisonArbre.apparier(requetesA, LectureJtl.lire(new File(champJtl.getText().trim())));
            } catch (Exception ex) {
                erreurJtl = ex.getMessage();
            }
        }
        List<Ligne> lignes = new ArrayList<>();
        for (Correlation.Candidat c : Correlation.detecter(a, b)) {
            Application.Source s = reponses.isEmpty() ? null : Application.chercherSource(c, reponses);
            Application.Action action = c.getNature() == Correlation.Nature.HORODATAGE ? Application.Action.HORODATAGE
                    : s != null ? Application.Action.EXTRAIRE : Application.Action.IGNORER;
            lignes.add(new Ligne(c, s, action));
        }
        modele.remplir(lignes);
        boutonAppliquer.setEnabled(!lignes.isEmpty());
        String msg = lignes.size() + " valeur(s) dynamique(s) trouvée(s) sur " + Correlation.aligner(a, b).size()
                + " requêtes alignées";
        if (erreurJtl != null) {
            msg += " — fichier de réponses illisible : " + erreurJtl;
        } else if (reponses.isEmpty()) {
            msg += " — sans fichier de réponses, aucun extracteur ne peut être proposé";
        }
        bilan.setText(msg);
    }

    private void montrerDetail() {
        int i = table.getSelectedRow();
        if (i < 0) {
            detail.setText("");
            return;
        }
        Ligne l = modele.lignes.get(table.convertRowIndexToModel(i));
        StringBuilder sb = new StringBuilder();
        sb.append("Valeur A : ").append(l.candidat.getValeurA()).append("\nValeur B : ")
                .append(l.candidat.getValeurB()).append("\nUtilisée par : ");
        List<String> u = new ArrayList<>();
        for (Correlation.Usage us : l.candidat.getUsages()) {
            u.add(requetesA.get(us.getRequete()).getName() + " (" + us.getType() + " " + us.getNom() + ")");
        }
        sb.append(String.join(", ", u));
        if (l.source != null) {
            sb.append("\nExtraite de la réponse de : ").append(requetesA.get(l.source.requete).getName())
                    .append(l.source.dansEntetes ? " (en-têtes)" : " (corps)")
                    .append("\nBornes : gauche « ").append(l.source.bornes.getGauche()).append(" », droite « ")
                    .append(l.source.bornes.getDroite()).append(" »");
        }
        detail.setText(sb.toString());
        detail.setCaretPosition(0);
    }

    private void appliquer() {
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        Application app = new Application();
        int ignorees = 0;
        for (Ligne l : modele.lignes) {
            if (!l.cochee) {
                continue;
            }
            Correlation.Candidat c = l.candidat;
            List<JMeterTreeNode> cibles = new ArrayList<>();
            if (l.action == Application.Action.EXTRAIRE) {
                if (l.source == null) {
                    ignorees++;
                    continue;
                }
                app.extraire(requetesA.get(l.source.requete), l.variable, l.source);
                cibles.addAll(requetesA.subList(l.source.requete + 1, requetesA.size()));
                app.remplacer(cibles, c.getValeurA(), "${" + l.variable + "}");
            } else if (l.action == Application.Action.HORODATAGE) {
                String f = c.getValeurA().length() == 10 ? "${__time(/1000,)}" : "${__time()}";
                app.remplacer(requetesA, c.getValeurA(), f);
            }
        }
        derniere = app;
        gp.setDirty(true);
        gp.refreshCurrentGui();
        gp.getMainFrame().repaint();
        boutonDefaire.setEnabled(!app.estVide());
        boutonAppliquer.setEnabled(false);
        bilan.setText(app.getExtracteurs() + " extracteur(s) créé(s), " + app.getRemplacements()
                + " valeur(s) remplacée(s)" + (ignorees > 0 ? ", " + ignorees + " ligne(s) sans source ignorée(s)" : ""));
    }

    private void defaire() {
        if (derniere == null) {
            return;
        }
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        derniere.defaire();
        derniere = null;
        gp.setDirty(true);
        gp.refreshCurrentGui();
        boutonDefaire.setEnabled(false);
        bilan.setText("Corrélation annulée.");
    }

    /** Une ligne du tableau : un candidat et ce que l'utilisateur en fait. */
    private static final class Ligne {
        final Correlation.Candidat candidat;
        final Application.Source source;
        boolean cochee;
        String variable;
        Application.Action action;

        Ligne(Correlation.Candidat c, Application.Source s, Application.Action a) {
            candidat = c;
            source = s;
            action = a;
            variable = c.getNomPropose();
            cochee = a != Application.Action.IGNORER;
        }
    }

    private final class Modele extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final List<Ligne> lignes = new ArrayList<>();

        void remplir(List<Ligne> l) {
            lignes.clear();
            lignes.addAll(l);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return lignes.size();
        }

        @Override
        public int getColumnCount() {
            return 7;
        }

        @Override
        public String getColumnName(int c) {
            return new String[] { "✓", "Variable", "Nature", "Valeur A", "Usages", "Source", "Action" }[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : c == 4 ? Integer.class : c == 6 ? Application.Action.class : String.class;
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c == 0 || c == 1 || c == 6;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Ligne l = lignes.get(r);
            switch (c) {
            case 0:
                return l.cochee;
            case 1:
                return l.variable;
            case 2:
                return l.candidat.getNature().toString().toLowerCase(java.util.Locale.ROOT);
            case 3:
                return l.candidat.getValeurA();
            case 4:
                return l.candidat.getUsages().size();
            case 5:
                return l.source == null ? "introuvable" : requetesA.get(l.source.requete).getName();
            default:
                return l.action;
            }
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            Ligne l = lignes.get(r);
            if (c == 0) {
                l.cochee = Boolean.TRUE.equals(v);
            } else if (c == 1 && v != null && !v.toString().trim().isEmpty()) {
                l.variable = v.toString().trim();
            } else if (c == 6 && v instanceof Application.Action) {
                l.action = (Application.Action) v;
            }
            fireTableRowsUpdated(r, r);
        }
    }
}
