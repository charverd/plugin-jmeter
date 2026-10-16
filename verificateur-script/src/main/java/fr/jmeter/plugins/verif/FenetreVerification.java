package fr.jmeter.plugins.verif;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.TreePath;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

/**
 * Rapport du vérificateur. Non modale : on corrige dans l'arbre, puis
 * « Revérifier ». Double-clic sur une ligne : l'élément est sélectionné
 * dans l'arbre.
 */
public class FenetreVerification extends JDialog {

    private static final long serialVersionUID = 1L;

    private static FenetreVerification instance;

    private final ModeleConstats modele = new ModeleConstats();
    private final JTable table = new JTable(modele);
    private final JLabel resume = new JLabel(" ");
    private final JTextArea detail = new JTextArea(3, 60);
    private final Map<Gravite, JCheckBox> filtres = new EnumMap<>(Gravite.class);
    private List<Constat> tous = Collections.emptyList();

    public static void ouvrir() {
        GuiPackage gp = GuiPackage.getInstance();
        if (gp == null) {
            return;
        }
        if (instance == null) {
            instance = new FenetreVerification(gp.getMainFrame());
        }
        instance.verifier();
        instance.setVisible(true);
        instance.toFront();
    }

    private FenetreVerification(Window parent) {
        super(parent, "Vérification du plan", ModalityType.MODELESS);

        JPanel haut = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        haut.add(resume);
        haut.add(new JLabel("   Afficher :"));
        for (Gravite g : Gravite.values()) {
            JCheckBox cb = new JCheckBox(g.getLibelle() + "s", true);
            cb.addItemListener(e -> filtrer());
            filtres.put(g, cb);
            haut.add(cb);
        }

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setMaxWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(330);
        table.getColumnModel().getColumn(3).setPreferredWidth(450);
        table.getColumnModel().getColumn(1).setCellRenderer(new RenduGravite());
        table.getSelectionModel().addListSelectionListener(e -> {
            int i = table.getSelectedRow();
            Constat c = i < 0 ? null : modele.lignes.get(table.convertRowIndexToModel(i));
            detail.setText(c == null ? "" : c.getMessage() + "\n→ " + c.getConseil());
            detail.setCaretPosition(0);
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    montrerDansArbre();
                }
            }
        });
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);

        JButton montrer = new JButton("Montrer dans l'arbre");
        montrer.addActionListener(e -> montrerDansArbre());
        JButton reverifier = new JButton("Revérifier");
        reverifier.addActionListener(e -> verifier());
        JButton copier = new JButton("Copier le rapport");
        copier.addActionListener(e -> {
            StringSelection s = new StringSelection(Verificateur.rapport(tous));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(s, s);
        });
        JButton fermer = new JButton("Fermer");
        fermer.addActionListener(e -> setVisible(false));
        JPanel boutons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        boutons.add(montrer);
        boutons.add(reverifier);
        boutons.add(copier);
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
        setSize(1050, 560);
        setLocationRelativeTo(parent);
    }

    private void verifier() {
        GuiPackage gp = GuiPackage.getInstance();
        // La saisie en cours dans l'écran ouvert doit être dans l'élément.
        gp.updateCurrentNode();
        tous = Verificateur.verifier((JMeterTreeNode) gp.getTreeModel().getRoot());
        resume.setText(Verificateur.resume(tous));
        filtrer();
    }

    private void filtrer() {
        List<Constat> vus = new ArrayList<>();
        for (Constat c : tous) {
            if (filtres.get(c.getGravite()).isSelected()) {
                vus.add(c);
            }
        }
        modele.remplir(vus);
        detail.setText("");
    }

    private void montrerDansArbre() {
        int i = table.getSelectedRow();
        if (i < 0) {
            return;
        }
        JMeterTreeNode n = modele.lignes.get(table.convertRowIndexToModel(i)).getNoeud();
        JTree arbre = GuiPackage.getInstance().getTreeListener().getJTree();
        TreePath chemin = new TreePath(n.getPath());
        arbre.setSelectionPath(chemin);
        arbre.scrollPathToVisible(chemin);
    }

    private static final class RenduGravite extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, focus, r, c);
            if (!sel && v instanceof String) {
                if (Gravite.ERREUR.getLibelle().equals(v)) {
                    setForeground(new Color(0xE0, 0x50, 0x50));
                } else if (Gravite.AVERTISSEMENT.getLibelle().equals(v)) {
                    setForeground(new Color(0xD0, 0x90, 0x20));
                } else {
                    setForeground(t.getForeground());
                }
            }
            return this;
        }
    }

    private static final class ModeleConstats extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final List<Constat> lignes = new ArrayList<>();

        void remplir(List<Constat> l) {
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
            return 4;
        }

        @Override
        public String getColumnName(int c) {
            return new String[] { "Règle", "Gravité", "Élément", "Constat" }[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Constat k = lignes.get(r);
            switch (c) {
            case 0:
                return k.getRegle();
            case 1:
                return k.getGravite().getLibelle();
            case 2:
                return Arbre.chemin(k.getNoeud());
            default:
                return k.getMessage();
            }
        }
    }
}
