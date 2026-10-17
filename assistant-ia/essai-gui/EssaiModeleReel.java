import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.tree.TreePath;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;

/**
 * Essai avec un vrai modèle (profil actif du fichier de configuration) : une
 * question simple sur la requête sélectionnée ; on montre la réponse, les
 * actions lues, puis on les applique. Arguments : plan.jmx minutes-max [question]
 */
public class EssaiModeleReel {
  static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
  static GuiPackage gp() { return GuiPackage.getInstance(); }
  static JMeterTreeNode noeud(String nom) {
    for (JMeterTreeNode n : gp().getTreeModel().getNodesOfType(TestElement.class)) if (nom.equals(n.getName())) return n;
    return null; }
  static <T> List<T> tous(Container c, Class<T> k) { List<T> l = new ArrayList<>(); tous(c, k, l); return l; }
  static <T> void tous(Container c, Class<T> k, List<T> l) { for (Component x : c.getComponents()) { if (k.isInstance(x)) l.add(k.cast(x)); if (x instanceof Container) tous((Container) x, k, l); } }
  public static void main(String[] a) throws Exception {
    long debut = System.currentTimeMillis();
    new Thread(() -> new org.apache.jmeter.JMeter().start(new String[]{"-t", a[0], "-j", "jmeter-reel.log"})).start();
    for (int i = 0; i < 120 && (gp() == null || gp().getTreeModel() == null || noeud("Debug") == null); i++) Thread.sleep(500);
    Thread.sleep(2000);
    edt(() -> gp().getTreeListener().getJTree().setSelectionPath(new TreePath(noeud("/api/commande").getPath())));
    Thread.sleep(500);
    edt(() -> { try { Class.forName("fr.jmeter.plugins.ia.FenetreAssistant").getMethod("ouvrir").invoke(null); } catch (Exception e) { throw new RuntimeException(e); } });
    Thread.sleep(800);
    JDialog d = null; for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible() && "Assistant IA".equals(((JDialog) w).getTitle())) d = (JDialog) w;
    final JDialog dlg = d;
    edt(() -> { for (JTextArea t : tous(dlg.getContentPane(), JTextArea.class)) if (t.isEditable()) t.setText(a.length > 2 ? a[2] : "Ajoute une assertion sur le code de réponse 200 à la requête sélectionnée."); });
    edt(() -> { for (JButton b : tous(dlg.getContentPane(), JButton.class)) if (b.getText().startsWith("Envoyer")) b.doClick(); });
    String etat = "";
    long limite = Long.parseLong(a[1]) * 60000L;
    while (System.currentTimeMillis() - debut < limite) {
      Thread.sleep(5000);
      for (JLabel l : tous(dlg.getContentPane(), JLabel.class)) if (l.getText().startsWith("Terminé") || l.getText().startsWith("Erreur")) etat = l.getText();
      if (!etat.isEmpty()) break;
    }
    System.out.println("  durée : " + (System.currentTimeMillis() - debut) / 1000 + " s ; état : " + (etat.isEmpty() ? "pas de réponse dans le délai" : etat));
    String conv = tous(dlg.getContentPane(), JTextPane.class).get(0).getText();
    System.out.println("----- conversation -----\n" + conv.substring(Math.max(0, conv.indexOf("Vous :"))) + "\n------------------------");
    JTable t = null; for (JTable x : tous(dlg.getContentPane(), JTable.class)) t = x;
    final JTable ta = t;
    edt(() -> { TableModel m = ta.getModel(); for (int i = 0; i < m.getRowCount(); i++) System.out.println("  action : " + m.getValueAt(i, 0) + " " + m.getValueAt(i, 1) + " " + m.getValueAt(i, 2) + " | " + m.getValueAt(i, 3) + " | " + m.getValueAt(i, 4)); });
    if (ta.getRowCount() > 0) {
      edt(() -> { for (JButton b : tous(dlg.getContentPane(), JButton.class)) if (b.getText().startsWith("Appliquer")) b.doClick(); });
      Thread.sleep(800);
      JMeterTreeNode cmd = noeud("/api/commande");
      List<String> enfants = new ArrayList<>();
      for (int i = 0; i < cmd.getChildCount(); i++) { JMeterTreeNode c = (JMeterTreeNode) cmd.getChildAt(i); enfants.add(c.getTestElement().getClass().getSimpleName() + " « " + c.getName() + " »"); }
      System.out.println("  après application, sous /api/commande : " + enfants);
      String fin = tous(dlg.getContentPane(), JTextPane.class).get(0).getText();
      System.out.println("  " + fin.substring(fin.lastIndexOf("modification(s) appliquée(s)") - 2).split("\n")[0]);
    }
    System.exit(0);
  }
}
