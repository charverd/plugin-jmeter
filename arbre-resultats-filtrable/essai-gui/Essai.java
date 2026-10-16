import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.*;
import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.SearchByClass;
import org.apache.jorphan.reflect.ClassFinder;
import fr.charverd.jmeter.arbre.ArbreResultatsFiltrableVisualizer;

public class Essai {
  static ArbreResultatsFiltrableVisualizer viz; static JFrame frame;
  static Object champ(String n) throws Exception { Field f = ArbreResultatsFiltrableVisualizer.class.getDeclaredField(n); f.setAccessible(true); return f.get(viz); }
  static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
  static void attendre() throws Exception { Thread.sleep(1500); edt(() -> {}); }
  static java.util.List<String> lignes() throws Exception {
    JTree t = (JTree) champ("arbre"); java.util.List<String> l = new ArrayList<>();
    edt(() -> { for (int i = 0; i < t.getRowCount(); i++) l.add(t.getPathForRow(i).getLastPathComponent().toString()); });
    return l; }
  static void capture(String nom, Component c) throws Exception {
    edt(() -> { BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
      c.paint(im.getGraphics()); try { ImageIO.write(im, "png", new File(nom)); } catch (Exception e) { throw new RuntimeException(e); } }); }
  static void filtre(String motif, boolean regex, boolean excl, boolean tx) throws Exception {
    edt(() -> { try {
      ((JCheckBox) champ("caseRegex")).setSelected(regex); ((JCheckBox) champ("caseExclure")).setSelected(excl);
      ((JCheckBox) champ("caseTransactions")).setSelected(tx);
      ((JComboBox<?>) champ("champFiltre")).getEditor().setItem(motif); } catch (Exception e) { throw new RuntimeException(e); } });
    Thread.sleep(400); attendre(); }
  static void verifier(String quoi, boolean ok) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
  static int echecs;

  public static void main(String[] a) throws Exception {
    String home = System.getenv("JMETER_HOME");
    JMeterUtils.setJMeterHome(home); JMeterUtils.loadJMeterProperties(home + "/bin/jmeter.properties"); JMeterUtils.initLocale();
    java.util.List<String> guis = ClassFinder.findClassesThatExtend(JMeterUtils.getSearchPaths(), new Class[]{JMeterGUIComponent.class});
    verifier("JMeter découvre la classe dans lib/ext", guis.contains(ArbreResultatsFiltrableVisualizer.class.getName()));

    HashTree plan = SaveService.loadTree(new File("plan.jmx"));
    SearchByClass<ResultCollector> s = new SearchByClass<>(ResultCollector.class); plan.traverse(s);
    ResultCollector rc = s.getSearchResults().iterator().next();
    rc.setFilename("resultats.jtl");
    edt(() -> { viz = new ArbreResultatsFiltrableVisualizer(); viz.configure(rc);
      frame = new JFrame("essai"); frame.setContentPane(viz); frame.setSize(1150, 720); frame.setVisible(true); });
    verifier("Libellé du menu", "Arbre de résultats filtrable".equals(viz.getStaticLabel()));
    rc.setListener(viz); rc.loadExistingFile(); attendre();
    edt(() -> { try { JTree t = (JTree) champ("arbre"); for (int i = 0; i < t.getRowCount(); i++) t.expandRow(i); } catch (Exception e) {} });
    java.util.List<String> l = lignes(); System.out.println("  arbre (filtre du plan) : " + l);
    verifier("Filtre lu dans le plan (catalogue, transactions seules)", l.equals(Arrays.asList("TC_02_Catalogue", "TC_02_Catalogue")));
    System.out.println("  état : " + ((JLabel) champ("etat")).getText());

    filtre("", false, false, false); l = lignes();
    verifier("Sans filtre : 8 résultats de premier niveau", l.size() == 8);
    capture("1-sans-filtre.png", frame.getContentPane());

    filtre("produits", false, false, false); l = lignes();
    verifier("'produits' : seules les requêtes GET /produits", l.equals(Arrays.asList("GET /produits", "GET /produits")));
    filtre("post /LOGIN", false, false, false); l = lignes();
    verifier("Enfant trouvé, la transaction parente reste (insensible à la casse)", l.equals(Arrays.asList("TC_01_Connexion", "TC_01_Connexion")));
    filtre("post /login", false, false, true); l = lignes();
    verifier("Transactions seules : le nom d'une requête ne compte plus", l.isEmpty());
    filtre("^TC_0[12]", true, false, true); l = lignes();
    verifier("Regex sur les transactions : 4 résultats", l.size() == 4);
    filtre("debug", false, true, false); l = lignes();
    verifier("Exclure 'debug' : 6 résultats", l.size() == 6 && !l.contains("Debug hors transaction"));
    filtre("([", true, false, false); l = lignes();
    verifier("Regex invalide : tout reste affiché, champ en rouge", l.size() == 8
        && ((JComboBox<?>) champ("champFiltre")).getEditor().getEditorComponent().getBackground().getRed() == 255);

    filtre("connexion", false, false, true);
    JTree t = (JTree) champ("arbre");
    edt(() -> { t.expandRow(0); t.setSelectionRow(2); t.requestFocusInWindow();
      t.getActionMap().get("copierNom").actionPerformed(null); });
    String presse = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
    verifier("Ctrl+C copie le nom du nœud (" + presse + ")", "POST /login".equals(presse));
    capture("2-filtre-transaction.png", frame.getContentPane());

    // Menu contextuel affiché pour la capture
    edt(() -> { Rectangle r = t.getRowBounds(2);
      java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(t, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, r.x + 20, r.y + 5, 1, true, java.awt.event.MouseEvent.BUTTON3);
      for (java.awt.event.MouseListener m : t.getMouseListeners()) m.mousePressed(e); });
    Thread.sleep(500);
    JPopupMenu menu = null;
    for (Window w : Window.getWindows()) {}
    MenuElement[] sel = MenuSelectionManager.defaultManager().getSelectedPath();
    for (Component c : frame.getLayeredPane().getComponents()) if (c instanceof JPopupMenu) menu = (JPopupMenu) c;
    if (menu == null) for (Component c : frame.getLayeredPane().getComponentsInLayer(JLayeredPane.POPUP_LAYER)) { if (c instanceof JPopupMenu) menu = (JPopupMenu) c; else if (c instanceof Container) for (Component d : ((Container) c).getComponents()) if (d instanceof JPopupMenu) menu = (JPopupMenu) d; }
    verifier("Menu contextuel affiché", menu != null && menu.isVisible());
    capture("3-menu-contextuel.png", frame.getRootPane());
    if (menu != null) { final JPopupMenu m = menu;
      edt(() -> { ((JMenuItem) m.getComponent(1)).doClick(); }); }
    presse = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
    verifier("Copier le chemin (" + presse + ")", "TC_01_Connexion > POST /login".equals(presse));

    filtre("", false, false, false);
    edt(() -> { try { ((JButton) trouver(viz, "Noms…")).doClick(); } catch (Exception e) {} });
    Thread.sleep(800);
    JDialog dlg = null; for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible()) dlg = (JDialog) w;
    verifier("Fenêtre Noms… ouverte", dlg != null);
    if (dlg != null) { capture("4-fenetre-noms.png", dlg.getContentPane());
      JList<?> liste = (JList<?>) trouverType(dlg.getContentPane(), JList.class);
      System.out.println("  noms listés : " + liste.getModel().getSize());
      verifier("6 noms distincts", liste.getModel().getSize() == 6);
      final JDialog d = dlg;
      edt(() -> { liste.setSelectedIndices(new int[]{0, 1}); ((JButton) trouver(d.getContentPane(), "Filtrer sur la sélection")).doClick(); });
      attendre(); l = lignes(); System.out.println("  après filtre sur sélection : " + l + " / motif " + ((JComboBox<?>) champ("champFiltre")).getEditor().getItem());
      int n = ((TreeNode) ((JTree) champ("arbre")).getModel().getRoot()).getChildCount();
      verifier("Filtre sur la sélection : 4 résultats de premier niveau", n == 4); }
    capture("5-filtre-selection.png", frame.getContentPane());

    // Réglages enregistrés dans le plan
    ResultCollector copie = (ResultCollector) viz.createTestElement();
    verifier("Réglages sauvegardés", copie.getPropertyAsBoolean("ArbreFiltrable.regex") && copie.getPropertyAsString("ArbreFiltrable.motif").startsWith("^(?:"));
    System.out.println(echecs == 0 ? "TOUT EST BON" : echecs + " ECHEC(S)");
    System.exit(echecs);
  }
  static Component trouver(Container c, String texte) { for (Component x : c.getComponents()) { if (x instanceof AbstractButton && texte.equals(((AbstractButton) x).getText())) return x; if (x instanceof Container) { Component r = trouver((Container) x, texte); if (r != null) return r; } } return null; }
  static Component trouverType(Container c, Class<?> k) { for (Component x : c.getComponents()) { if (k.isInstance(x)) return x; if (x instanceof Container) { Component r = trouverType((Container) x, k); if (r != null) return r; } } return null; }
}
