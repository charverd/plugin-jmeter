import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.tree.TreePath;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.control.TransactionController;

/**
 * Pilote la vraie interface de JMeter : sélection multiple dans l'arbre,
 * menu clic droit, fenêtre « Modification en masse », enregistrement du plan
 * par la commande « Enregistrer ». Argument : plan.jmx (réécrit en place).
 */
public class Essai {
  static int echecs;
  static void verifier(String quoi, boolean ok) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
  static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
  static void pause(long ms) throws Exception { Thread.sleep(ms); edt(() -> {}); }
  static <T extends Component> T trouver(Container c, Class<T> type, String texte) {
    for (Component x : c.getComponents()) {
      if (type.isInstance(x) && (texte == null || (x instanceof AbstractButton && texte.equals(((AbstractButton) x).getText()))
          || (x instanceof JLabel && texte.equals(((JLabel) x).getText())))) return type.cast(x);
      if (x instanceof JMenu) { for (Component y : ((JMenu) x).getMenuComponents()) if (type.isInstance(y) && y instanceof AbstractButton && texte != null && texte.equals(((AbstractButton) y).getText())) return type.cast(y); }
      if (x instanceof Container) { T r = trouver((Container) x, type, texte); if (r != null) return r; } }
    return null; }
  static void capture(String nom, Component c) throws Exception {
    edt(() -> { BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
      c.paint(im.getGraphics()); try { ImageIO.write(im, "png", new File(nom)); } catch (Exception e) { throw new RuntimeException(e); } }); }
  static JMeterTreeNode noeud(String nom) {
    for (JMeterTreeNode n : GuiPackage.getInstance().getTreeModel().getNodesOfType(org.apache.jmeter.testelement.TestElement.class))
      if (nom.equals(n.getName())) return n;
    return null; }
  static JMeterTreeNode enTetes(String tc, int req) { return (JMeterTreeNode) noeud(tc).getChildAt(req - 1).getChildAt(0); }
  static String param(String tc, int req) { return ((org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase) ((JMeterTreeNode) noeud(tc).getChildAt(req - 1)).getTestElement()).getArguments().getArgumentsAsMap().get("client"); }
  static String auth(String tc, int req) { return ((HeaderManager) enTetes(tc, req).getTestElement()).getFirstHeaderNamed("Authorization").getValue(); }

  public static void main(String[] a) throws Exception {
    String plan = a[0];
    new Thread(() -> new org.apache.jmeter.JMeter().start(new String[]{"-t", plan, "-j", "jmeter.log"})).start();
    for (int i = 0; i < 120 && (GuiPackage.getInstance() == null || noeud("TC_03_Deconnexion") == null); i++) Thread.sleep(500);
    pause(2000);
    GuiPackage gp = GuiPackage.getInstance();
    JFrame cadre = gp.getMainFrame();
    JTree arbre = gp.getTreeListener().getJTree();

    // 1. Menu Outils
    JMenuBar barre = cadre.getJMenuBar();
    JMenuItem item = null;
    for (int i = 0; i < barre.getMenuCount(); i++) { JMenu m = barre.getMenu(i);
      for (Component c : m.getMenuComponents()) if (c instanceof JMenuItem && "Modification en masse…".equals(((JMenuItem) c).getText())) { item = (JMenuItem) c;
        System.out.println("  menu : " + m.getText()); } }
    verifier("« Modification en masse… » présent dans le menu Outils", item != null);

    // 2. L'écran ouvert est celui des en-têtes de TC_01/req-1 (piège de l'écran périmé),
    //    puis sélection multiple : TC_01 et TC_02, en-têtes de TC_01/req-1 en dernier.
    edt(() -> { arbre.expandRow(0); arbre.expandRow(1); });
    edt(() -> arbre.setSelectionPaths(new TreePath[]{ new TreePath(noeud("TC_01_Connexion").getPath()),
        new TreePath(noeud("TC_02_Commande").getPath()), new TreePath(enTetes("TC_01_Connexion", 1).getPath()) }));
    pause(800);
    System.out.println("  écran courant : " + gp.getCurrentGui().getClass().getSimpleName() + ", sélection : " + arbre.getSelectionCount());

    // 3. Clic droit sur TC_02 : la sélection multiple reste et le menu contient l'entrée.
    edt(() -> { Rectangle r = arbre.getPathBounds(new TreePath(noeud("TC_02_Commande").getPath()));
      MouseEvent ev = new MouseEvent(arbre, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), MouseEvent.BUTTON3_DOWN_MASK, r.x + 10, r.y + 5, 1, true, MouseEvent.BUTTON3);
      arbre.dispatchEvent(ev); });
    pause(1200);
    JPopupMenu pop = null;
    for (MenuElement m : MenuSelectionManager.defaultManager().getSelectedPath()) if (m instanceof JPopupMenu) pop = (JPopupMenu) m;
    if (pop == null) for (Window w : Window.getWindows()) { JPopupMenu p = trouver(w, JPopupMenu.class, null); if (p != null && p.isShowing()) pop = p; }
    boolean dansPopup = pop != null && trouver(pop, JMenuItem.class, "Modification en masse…") != null;
    verifier("entrée présente dans le menu clic droit de l'arbre", dansPopup);
    verifier("la sélection multiple est conservée au clic droit (" + arbre.getSelectionCount() + ")", arbre.getSelectionCount() == 3);
    capture("1-clic-droit.png", cadre.getRootPane());
    final JPopupMenu popup = pop;
    if (popup != null) edt(() -> trouver(popup, JMenuItem.class, "Modification en masse…").doClick());
    else { final JMenuItem it = item; edt(() -> it.doClick()); }
    pause(1000);

    JDialog dlg = null; for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible() && "Modification en masse".equals(((JDialog) w).getTitle())) dlg = (JDialog) w;
    verifier("fenêtre ouverte", dlg != null);
    if (dlg == null) { System.exit(1); }
    final JDialog d = dlg;
    String perimetre = null;
    for (JLabel l : labels(d.getContentPane())) if (l.getText().contains("sélectionné")) perimetre = l.getText();
    System.out.println("  périmètre : " + perimetre);
    verifier("périmètre : 4 requêtes et 4 gestionnaires d'en-têtes", perimetre != null && perimetre.contains("4 requête(s)") && perimetre.contains("4 gestionnaire(s)"));

    // 4. Onglet En-têtes : Authorization -> Bearer ${token}
    List<JTable> tables = tables(d.getContentPane());
    JTable tEntetes = tables.get(0), tApercu = tables.get(1);
    edt(() -> { TableModel m = tEntetes.getModel(); for (int i = 0; i < m.getRowCount(); i++) if ("Authorization".equals(m.getValueAt(i, 0))) tEntetes.setRowSelectionInterval(i, i); });
    List<JTextField> champs = champs(d.getContentPane());
    edt(() -> { champs.get(1).setText("Bearer ${token}"); trouver(d.getContentPane(), JButton.class, "Aperçu : donner cette valeur").doClick(); });
    pause(300);
    verifier("aperçu : 4 modifications d'en-tête", tApercu.getRowCount() == 4);
    capture("2-apercu-entetes.png", d.getContentPane());
    edt(() -> trouver(d.getContentPane(), JButton.class, "Appliquer les modifications cochées").doClick());
    pause(500);
    verifier("TC_01 et TC_02 : Authorization = Bearer ${token}", auth("TC_01_Connexion", 1).equals("Bearer ${token}") && auth("TC_02_Commande", 2).equals("Bearer ${token}"));
    verifier("TC_03 (non sélectionnée) inchangée", auth("TC_03_Deconnexion", 1).equals("Bearer jeton3"));
    verifier("le plan est marqué modifié", gp.isDirty());

    // 4 bis. V2 : même onglet, cible « Paramètre de requête » : client -> ${client}
    edt(() -> trouver(d.getContentPane(), JRadioButton.class, "Paramètre de requête").doClick());
    pause(300);
    String titreColonne = String.valueOf(tEntetes.getColumnModel().getColumn(0).getHeaderValue());
    verifier("la liste montre les paramètres (colonne « " + titreColonne + " »)",
        "Paramètre".equals(titreColonne) && tEntetes.getRowCount() == 1 && "client".equals(tEntetes.getModel().getValueAt(0, 0)));
    edt(() -> { tEntetes.setRowSelectionInterval(0, 0); champs.get(1).setText("${client}");
      trouver(d.getContentPane(), JButton.class, "Aperçu : donner cette valeur").doClick(); });
    pause(300);
    verifier("aperçu : 4 paramètres client", tApercu.getRowCount() == 4);
    capture("2b-apercu-parametres.png", d.getContentPane());
    edt(() -> trouver(d.getContentPane(), JButton.class, "Appliquer les modifications cochées").doClick());
    pause(500);
    verifier("TC_01 et TC_02 : client = ${client}", "${client}".equals(param("TC_01_Connexion", 1)) && "${client}".equals(param("TC_02_Commande", 2)));
    verifier("TC_03 : client inchangé", "C123".equals(param("TC_03_Deconnexion", 1)));

    // 5. Onglet Rechercher / remplacer : serveur -> ${serveur}
    JTabbedPane onglets = trouver(d.getContentPane(), JTabbedPane.class, null);
    edt(() -> onglets.setSelectedIndex(1));
    edt(() -> { champs.get(2).setText("app.exemple.fr"); champs.get(3).setText("${serveur}");
      trouver(d.getContentPane(), JButton.class, "Aperçu").doClick(); });
    pause(300);
    verifier("aperçu remplacement : 4 serveurs", tApercu.getRowCount() == 4);
    capture("3-apercu-remplacement.png", d.getContentPane());
    edt(() -> trouver(d.getContentPane(), JButton.class, "Appliquer les modifications cochées").doClick());
    pause(300);
    edt(() -> d.setVisible(false));

    // 6. Piège de l'écran périmé : on navigue ailleurs, JMeter réécrit l'élément depuis l'écran.
    edt(() -> arbre.setSelectionPath(new TreePath(noeud("TC_03_Deconnexion").getPath())));
    pause(800);
    verifier("après navigation, l'écran des en-têtes n'a pas écrasé la modification", auth("TC_01_Connexion", 1).equals("Bearer ${token}"));

    // 7. Enregistrer
    edt(() -> ActionRouter.getInstance().doActionNow(new java.awt.event.ActionEvent(arbre, 1, ActionNames.SAVE)));
    pause(800);
    System.out.println(echecs == 0 ? "TOUT EST BON (interface)" : echecs + " ECHEC(S)");
    System.exit(echecs);
  }
  static List<JLabel> labels(Container c) { List<JLabel> l = new ArrayList<>(); coll(c, JLabel.class, l); return l; }
  static List<JTable> tables(Container c) { List<JTable> l = new ArrayList<>(); coll(c, JTable.class, l); return l; }
  static List<JTextField> champs(Container c) { List<JTextField> l = new ArrayList<>(); coll(c, JTextField.class, l); return l; }
  static <T> void coll(Container c, Class<T> k, List<T> l) { for (Component x : c.getComponents()) { if (k.isInstance(x)) l.add(k.cast(x)); if (x instanceof Container) coll((Container) x, k, l); } }
}
