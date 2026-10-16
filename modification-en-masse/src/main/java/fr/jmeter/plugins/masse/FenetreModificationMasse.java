package fr.jmeter.plugins.masse;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;

import fr.jmeter.plugins.masse.MoteurModification.Champ;
import fr.jmeter.plugins.masse.MoteurModification.Cible;
import fr.jmeter.plugins.masse.MoteurModification.StatNom;

/**
 * Fenêtre « Modification en masse » : agit sur les éléments sélectionnés
 * dans l'arbre et tout leur contenu. Non modale, pour pouvoir changer la
 * sélection dans l'arbre sans la fermer.
 */
public class FenetreModificationMasse extends JDialog {

    private static final long serialVersionUID = 1L;

    private static FenetreModificationMasse instance;

    private final JLabel lblPerimetre = new JLabel(" ");
    private final JCheckBox caseContenu = new JCheckBox("Inclure tout le contenu des éléments sélectionnés", true);
    private final JCheckBox caseDesactives = new JCheckBox("Ignorer les éléments désactivés", true);

    private final JRadioButton radioEntete = new JRadioButton(Cible.ENTETE.getLibelle(), true);
    private final JRadioButton radioParametre = new JRadioButton(Cible.PARAMETRE.getLibelle());
    private final ModeleEntetes modeleEntetes = new ModeleEntetes();
    private final JTable tableEntetes = new JTable(modeleEntetes);
    private final JLabel lblNom = new JLabel();
    private final JTextField champNomEntete = new JTextField(20);
    private final JTextField champValeurEntete = new JTextField(30);
    private final JCheckBox caseAjouter = new JCheckBox();
    private final JButton apercuSupprimer = new JButton();

    private final JTextField champRecherche = new JTextField(25);
    private final JTextField champRemplacement = new JTextField(25);
    private final JCheckBox caseRegex = new JCheckBox("Regex");
    private final JCheckBox caseCasse = new JCheckBox("Respecter la casse");
    private final Map<Champ, JCheckBox> casesChamps = new EnumMap<>(Champ.class);

    private final ModeleApercu modeleApercu = new ModeleApercu();
    private final JLabel lblApercu = new JLabel(" ");
    private final JButton boutonAppliquer = new JButton("Appliquer les modifications cochées");
    private final JButton boutonDefaire = new JButton("Annuler la dernière application");

    private List<JMeterTreeNode> perimetre = Collections.emptyList();
    private MoteurModification.Bilan dernierBilan;

    /** Ouvre la fenêtre (une seule à la fois) sur la sélection courante de l'arbre. */
    public static void ouvrir() {
        GuiPackage gp = GuiPackage.getInstance();
        if (gp == null) {
            return;
        }
        if (instance == null) {
            instance = new FenetreModificationMasse(gp.getMainFrame());
        }
        instance.relireSelection();
        instance.setVisible(true);
        instance.toFront();
    }

    private FenetreModificationMasse(Window parent) {
        super(parent, "Modification en masse", ModalityType.MODELESS);

        JButton boutonRelire = new JButton("Reprendre la sélection de l'arbre");
        boutonRelire.addActionListener(e -> relireSelection());
        caseContenu.addItemListener(e -> relireSelection());
        caseDesactives.addItemListener(e -> relireSelection());
        JPanel haut = new JPanel(new GridBagLayout());
        GridBagConstraints g = contraintes();
        haut.add(lblPerimetre, g);
        g.gridy++;
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        options.add(caseContenu);
        options.add(caseDesactives);
        options.add(boutonRelire);
        haut.add(options, g);
        haut.setBorder(BorderFactory.createTitledBorder("Périmètre"));

        JTabbedPane onglets = new JTabbedPane();
        onglets.addTab("En-têtes / paramètres", ongletEntetes());
        onglets.addTab("Rechercher / remplacer", ongletRemplacer());

        JTable tableApercu = new JTable(modeleApercu);
        tableApercu.getColumnModel().getColumn(0).setMaxWidth(30);
        tableApercu.getColumnModel().getColumn(1).setPreferredWidth(320);
        tableApercu.getColumnModel().getColumn(2).setPreferredWidth(150);
        tableApercu.getColumnModel().getColumn(3).setPreferredWidth(200);
        tableApercu.getColumnModel().getColumn(4).setPreferredWidth(200);
        tableApercu.setAutoCreateRowSorter(true);
        JPanel apercu = new JPanel(new BorderLayout(4, 4));
        apercu.setBorder(BorderFactory.createTitledBorder("Aperçu"));
        apercu.add(new JScrollPane(tableApercu), BorderLayout.CENTER);
        JPanel boutons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton toutCocher = new JButton("Tout cocher");
        toutCocher.addActionListener(e -> modeleApercu.cocherTout(true));
        JButton toutDecocher = new JButton("Tout décocher");
        toutDecocher.addActionListener(e -> modeleApercu.cocherTout(false));
        boutonAppliquer.addActionListener(e -> appliquer());
        boutonDefaire.addActionListener(e -> defaire());
        boutonDefaire.setEnabled(false);
        JButton fermer = new JButton("Fermer");
        fermer.addActionListener(e -> setVisible(false));
        boutons.add(lblApercu);
        boutons.add(toutCocher);
        boutons.add(toutDecocher);
        boutons.add(boutonAppliquer);
        boutons.add(boutonDefaire);
        boutons.add(fermer);
        apercu.add(boutons, BorderLayout.SOUTH);

        JPanel milieu = new JPanel(new BorderLayout());
        milieu.add(haut, BorderLayout.NORTH);
        milieu.add(onglets, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, milieu, apercu);
        split.setResizeWeight(0.45);

        JPanel contenu = new JPanel(new BorderLayout());
        contenu.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        contenu.add(split, BorderLayout.CENTER);
        setContentPane(contenu);
        setSize(1000, 700);
        setLocationRelativeTo(parent);
        majCible();
        majApercu(Collections.<Modification>emptyList());
    }

    private Cible cible() {
        return radioParametre.isSelected() ? Cible.PARAMETRE : Cible.ENTETE;
    }

    /** Libellés et liste des noms selon la cible choisie. */
    private void majCible() {
        boolean entete = cible() == Cible.ENTETE;
        lblNom.setText(entete ? "En-tête :" : "Paramètre :");
        caseAjouter.setText(entete ? "Ajouter l'en-tête aux gestionnaires d'en-têtes où il manque"
                : "Ajouter le paramètre aux requêtes où il manque");
        apercuSupprimer.setText(entete ? "Aperçu : supprimer l'en-tête" : "Aperçu : supprimer le paramètre");
        champValeurEntete.setToolTipText(entete ? "Par exemple : Bearer ${token}" : "Par exemple : ${client}");
        modeleEntetes.setTitre(entete ? "En-tête" : "Paramètre");
        tableEntetes.getColumnModel().getColumn(0).setHeaderValue(entete ? "En-tête" : "Paramètre");
        tableEntetes.getTableHeader().repaint();
        champNomEntete.setText("");
        modeleEntetes.remplir(MoteurModification.noms(perimetre, cible()));
        majApercu(Collections.<Modification>emptyList());
    }

    private JComponent ongletEntetes() {
        ButtonGroup groupe = new ButtonGroup();
        groupe.add(radioEntete);
        groupe.add(radioParametre);
        radioEntete.addActionListener(e -> majCible());
        radioParametre.addActionListener(e -> majCible());
        radioParametre.setToolTipText("Paramètres d'URL ou de formulaire ; nom sensible à la casse ;"
                + " les requêtes à corps brut (Body Data) sont ignorées");
        JPanel choixCible = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        choixCible.add(new JLabel("Modifier :"));
        choixCible.add(radioEntete);
        choixCible.add(radioParametre);

        tableEntetes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableEntetes.getColumnModel().getColumn(0).setPreferredWidth(180);
        tableEntetes.getColumnModel().getColumn(1).setMaxWidth(110);
        tableEntetes.getColumnModel().getColumn(2).setPreferredWidth(420);
        tableEntetes.getSelectionModel().addListSelectionListener(e -> {
            int i = tableEntetes.getSelectedRow();
            if (!e.getValueIsAdjusting() && i >= 0) {
                StatNom s = modeleEntetes.get(tableEntetes.convertRowIndexToModel(i));
                champNomEntete.setText(s.getNom());
                if (s.getValeurs().size() == 1) {
                    champValeurEntete.setText(s.getValeurs().iterator().next());
                }
            }
        });
        JButton apercuDefinir = new JButton("Aperçu : donner cette valeur");
        apercuDefinir.addActionListener(e -> {
            if (champNomEntete.getText().trim().isEmpty()) {
                lblApercu.setText("Choisir ou saisir un nom dans la liste.");
                return;
            }
            majApercu(MoteurModification.definir(perimetre, cible(), champNomEntete.getText().trim(),
                    champValeurEntete.getText(), caseAjouter.isSelected()));
        });
        apercuSupprimer.addActionListener(e -> {
            if (!champNomEntete.getText().trim().isEmpty()) {
                majApercu(MoteurModification.supprimer(perimetre, cible(), champNomEntete.getText().trim()));
            }
        });

        JPanel formulaire = new JPanel(new GridBagLayout());
        GridBagConstraints g = contraintes();
        formulaire.add(lblNom, g);
        g.gridx = 1;
        formulaire.add(champNomEntete, g);
        g.gridx = 0;
        g.gridy++;
        formulaire.add(new JLabel("Nouvelle valeur :"), g);
        g.gridx = 1;
        formulaire.add(champValeurEntete, g);
        g.gridx = 1;
        g.gridy++;
        formulaire.add(caseAjouter, g);
        g.gridy++;
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        b.add(apercuDefinir);
        b.add(apercuSupprimer);
        formulaire.add(b, g);

        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(choixCible, BorderLayout.NORTH);
        JScrollPane defil = new JScrollPane(tableEntetes);
        defil.setPreferredSize(new Dimension(600, 120));
        p.add(defil, BorderLayout.CENTER);
        p.add(formulaire, BorderLayout.SOUTH);
        return p;
    }

    private JComponent ongletRemplacer() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = contraintes();
        p.add(new JLabel("Rechercher :"), g);
        g.gridx = 1;
        p.add(champRecherche, g);
        g.gridx = 0;
        g.gridy++;
        p.add(new JLabel("Remplacer par :"), g);
        g.gridx = 1;
        p.add(champRemplacement, g);
        champRemplacement.setToolTipText("${...} est toujours pris à la lettre ; en mode regex, $1, $2… désignent les groupes");
        g.gridy++;
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        opts.add(caseRegex);
        opts.add(caseCasse);
        p.add(opts, g);
        g.gridy++;
        JPanel champs = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        champs.add(new JLabel("Dans :"));
        for (Champ c : Champ.values()) {
            JCheckBox cb = new JCheckBox(c.getLibelle(), c != Champ.NOMS);
            casesChamps.put(c, cb);
            champs.add(cb);
        }
        p.add(champs, g);
        g.gridy++;
        JButton apercu = new JButton("Aperçu");
        apercu.addActionListener(e -> apercuRemplacement());
        champRecherche.addActionListener(e -> apercuRemplacement());
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        b.add(apercu);
        p.add(b, g);
        JPanel conteneur = new JPanel(new BorderLayout());
        conteneur.add(p, BorderLayout.NORTH);
        return conteneur;
    }

    private static GridBagConstraints contraintes() {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(2, 2, 2, 2);
        return g;
    }

    // ------------------------------------------------------------------

    private void relireSelection() {
        GuiPackage gp = GuiPackage.getInstance();
        // Ce qui est en cours de saisie dans l'écran courant doit être dans
        // l'élément avant qu'on le lise ; sinon JMeter le réécrirait plus tard
        // par-dessus nos modifications.
        gp.updateCurrentNode();
        JMeterTreeNode[] selection = gp.getTreeListener().getSelectedNodes();
        perimetre = MoteurModification.perimetre(selection, caseContenu.isSelected(), caseDesactives.isSelected());
        int requetes = MoteurModification.compter(perimetre, HTTPSamplerBase.class);
        int gestionnaires = MoteurModification.compter(perimetre, HeaderManager.class);
        lblPerimetre.setText(String.format("%d élément(s) sélectionné(s) → %d requête(s) HTTP, %d gestionnaire(s) d'en-têtes",
                selection == null ? 0 : selection.length, requetes, gestionnaires));
        modeleEntetes.remplir(MoteurModification.noms(perimetre, cible()));
        majApercu(Collections.<Modification>emptyList());
    }

    private void apercuRemplacement() {
        Set<Champ> champs = EnumSet.noneOf(Champ.class);
        for (Map.Entry<Champ, JCheckBox> e : casesChamps.entrySet()) {
            if (e.getValue().isSelected()) {
                champs.add(e.getKey());
            }
        }
        try {
            majApercu(MoteurModification.remplacer(perimetre, champRecherche.getText(),
                    champRemplacement.getText(), caseRegex.isSelected(), caseCasse.isSelected(), champs));
        } catch (PatternSyntaxException ex) {
            majApercu(Collections.<Modification>emptyList());
            lblApercu.setText("Expression invalide : " + ex.getDescription());
        }
    }

    private void majApercu(List<Modification> modifs) {
        modeleApercu.remplir(modifs);
        lblApercu.setText(modifs.isEmpty() ? "Aucune modification prévue."
                : modifs.size() + " modification(s) prévue(s)");
        boutonAppliquer.setEnabled(!modifs.isEmpty());
    }

    private void appliquer() {
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        MoteurModification.Bilan bilan = MoteurModification.appliquer(modeleApercu.lignes);
        rafraichir(bilan);
        dernierBilan = bilan;
        boutonDefaire.setEnabled(!bilan.getAppliquees().isEmpty());
        relireSelection();
        String msg = bilan.getAppliquees().size() + " modification(s) appliquée(s)";
        if (bilan.getPerimees() > 0) {
            msg += ", " + bilan.getPerimees() + " écartée(s) car l'élément a changé depuis l'aperçu";
        }
        lblApercu.setText(msg);
    }

    private void defaire() {
        if (dernierBilan == null) {
            return;
        }
        GuiPackage.getInstance().updateCurrentNode();
        MoteurModification.defaire(dernierBilan);
        rafraichir(dernierBilan);
        int n = dernierBilan.getAppliquees().size();
        dernierBilan = null;
        boutonDefaire.setEnabled(false);
        relireSelection();
        lblApercu.setText(n + " modification(s) annulée(s)");
    }

    /** Arbre, écran courant et indicateur « plan modifié ». */
    private static void rafraichir(MoteurModification.Bilan bilan) {
        GuiPackage gp = GuiPackage.getInstance();
        for (JMeterTreeNode n : bilan.noeudsTouches()) {
            n.nameChanged();
            gp.getTreeModel().nodeChanged(n);
        }
        if (!bilan.getAppliquees().isEmpty()) {
            gp.setDirty(true);
        }
        gp.refreshCurrentGui();
        SwingUtilities.invokeLater(() -> gp.getMainFrame().repaint());
    }

    // ------------------------------------------------------------------

    private static final class ModeleEntetes extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final List<StatNom> lignes = new ArrayList<>();
        private String titre = "En-tête";

        void setTitre(String t) {
            titre = t;
        }

        void remplir(List<StatNom> l) {
            lignes.clear();
            lignes.addAll(l);
            fireTableDataChanged();
        }

        StatNom get(int i) {
            return lignes.get(i);
        }

        @Override
        public int getRowCount() {
            return lignes.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int c) {
            return new String[] { titre, "Occurrences", "Valeurs" }[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            StatNom s = lignes.get(r);
            switch (c) {
            case 0:
                return s.getNom();
            case 1:
                return s.getOccurrences();
            default:
                return s.getValeurs().size() == 1 ? s.getValeurs().iterator().next()
                        : s.getValeurs().size() + " valeurs différentes";
            }
        }
    }

    private static final class ModeleApercu extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final List<Modification> lignes = new ArrayList<>();

        void remplir(List<Modification> l) {
            lignes.clear();
            lignes.addAll(l);
            fireTableDataChanged();
        }

        void cocherTout(boolean v) {
            for (Modification m : lignes) {
                m.setCochee(v);
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return lignes.size();
        }

        @Override
        public int getColumnCount() {
            return 5;
        }

        @Override
        public String getColumnName(int c) {
            return new String[] { "✓", "Élément", "Champ", "Avant", "Après" }[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c == 0;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Modification m = lignes.get(r);
            switch (c) {
            case 0:
                return m.isCochee();
            case 1:
                return m.getElement();
            case 2:
                return m.getChamp();
            case 3:
                return m.getAvant();
            default:
                return m.getApres();
            }
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            if (c == 0) {
                lignes.get(r).setCochee(Boolean.TRUE.equals(v));
                fireTableCellUpdated(r, c);
            }
        }
    }
}
