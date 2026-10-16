import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.Visualizer;

/**
 * Essai dans la vraie interface de JMeter (thème Darklaf) : la flèche du
 * champ Filtre. Le banc Essai.java tourne hors de JMeter, avec le thème
 * Swing par défaut, et ne voyait pas que Darklaf n'ouvre pas une liste vide.
 * Argument : plan.jmx contenant le récepteur.
 */
public class EssaiJMeter {
  static int echecs;
  static Object gui;
  static void verifier(String quoi, boolean ok) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
  static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
  static void pause(long ms) throws Exception { Thread.sleep(ms); edt(() -> {}); }
  static Object champ(String n) throws Exception { Field f = gui.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(gui); }
  static SampleResult r(String nom) { SampleResult s = new SampleResult(); s.setSampleLabel(nom); s.setSuccessful(true); s.setResponseMessage("OK"); return s; }
  static void clic(Component c, int x, int y) throws Exception {
    edt(() -> { c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
      c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1)); }); }
  static int premierNiveau() throws Exception { JTree t = (JTree) champ("arbre"); final int[] n = new int[1]; edt(() -> n[0] = ((TreeNode) t.getModel().getRoot()).getChildCount()); return n[0]; }

  public static void main(String[] a) throws Exception {
    new Thread(() -> new org.apache.jmeter.JMeter().start(new String[]{"-t", a[0], "-j", "jmeter-gui.log"})).start();
    JMeterTreeNode n = null;
    for (int i = 0; i < 120 && n == null; i++) { Thread.sleep(500); GuiPackage gp = GuiPackage.getInstance();
      if (gp != null && gp.getTreeModel() != null) for (JMeterTreeNode x : gp.getTreeModel().getNodesOfType(ResultCollector.class)) n = x; }
    pause(1500);
    final JMeterTreeNode noeud = n;
    edt(() -> GuiPackage.getInstance().getTreeListener().getJTree().setSelectionPath(new TreePath(noeud.getPath())));
    pause(1000);
    gui = GuiPackage.getInstance().getCurrentGui();
    System.out.println("  thème : " + UIManager.getLookAndFeel().getName());
    JComboBox<?> combo = (JComboBox<?>) champ("champFiltre");
    JTextComponent ed = (JTextComponent) combo.getEditor().getEditorComponent();
    JCheckBox tx = (JCheckBox) champ("caseTransactions");
    // Le plan d'essai enregistre un filtre : on part d'un filtre vide.
    edt(() -> { tx.setSelected(false); combo.getEditor().setItem(""); });
    pause(500);

    SampleResult t = r("TC_01_Connexion"); t.setResponseMessage("Number of samples in transaction : 2, number of failing samples : 0");
    t.addSubResult(r("GET /login"), false); t.addSubResult(r("POST /login"), false);
    ((Visualizer) gui).add(t);
    ((Visualizer) gui).add(r("GET /produits"));
    pause(1800);

    JButton fleche = null;
    for (Component c : combo.getComponents()) if (c instanceof JButton) fleche = (JButton) c;
    final JButton f = fleche;
    clic(f, f.getWidth() / 2, f.getHeight() / 2);
    pause(600);
    ComboPopup popup = (ComboPopup) combo.getUI().getAccessibleChild(combo, 0);
    JList<?> liste = popup.getList();
    verifier("la flèche ouvre la liste", combo.isPopupVisible());
    verifier("la liste contient les 4 noms reçus (" + liste.getModel().getSize() + ")", liste.getModel().getSize() == 4);
    JFrame cadre = GuiPackage.getInstance().getMainFrame();
    edt(() -> { BufferedImage im = new BufferedImage(cadre.getWidth(), cadre.getHeight(), BufferedImage.TYPE_INT_RGB);
      cadre.getRootPane().paint(im.getGraphics()); try { ImageIO.write(im, "png", new File("fleche-ouverte.png")); } catch (Exception e) { } });

    // Choisir « GET /produits » dans la liste, à la souris.
    int idx = -1;
    for (int i = 0; i < liste.getModel().getSize(); i++) if ("GET /produits".equals(liste.getModel().getElementAt(i))) idx = i;
    final int k = idx;
    final Rectangle cell = liste.getCellBounds(k, k);
    clic(liste, cell.x + 5, cell.y + cell.height / 2);
    pause(900);
    verifier("choisir un nom le met dans le champ (« " + ed.getText() + " »)", "GET /produits".equals(ed.getText()));
    verifier("et filtre l'arbre (" + premierNiveau() + " résultat de premier niveau)", premierNiveau() == 1);

    // Saisie en cours + arrivée de nouveaux noms : rien ne doit bouger dans le champ.
    edt(() -> { ed.setText("TC"); ed.setCaretPosition(1); ed.requestFocusInWindow(); });
    ((Visualizer) gui).add(r("GET /panier"));
    pause(2500);
    verifier("la saisie en cours est conservée (« " + ed.getText() + " », curseur " + ed.getCaretPosition() + ")", "TC".equals(ed.getText()) && ed.getCaretPosition() == 1);
    verifier("le nouveau nom est proposé (" + combo.getItemCount() + " noms)", combo.getItemCount() == 5);

    // Transactions seules : la liste ne propose plus que les transactions.
    edt(() -> tx.setSelected(true));
    pause(600);
    verifier("« Transactions seules » réduit la liste à TC_01_Connexion", combo.getItemCount() == 1 && "TC_01_Connexion".equals(combo.getItemAt(0)));
    clic(f, f.getWidth() / 2, f.getHeight() / 2);
    pause(500);
    verifier("la flèche s'ouvre encore", combo.isPopupVisible());
    edt(() -> combo.setPopupVisible(false));

    // Effacer les résultats : la liste se vide.
    edt(() -> ((org.apache.jmeter.samplers.Clearable) gui).clearData());
    pause(1800);
    verifier("après effacement des résultats, la liste est vide", combo.getItemCount() == 0);

    System.out.println(echecs == 0 ? "TOUT EST BON (interface JMeter)" : echecs + " ECHEC(S)");
    System.exit(echecs);
  }
}
