import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.TreePath;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.gui.util.MenuFactory;

/**
 * Pilote la vraie interface de JMeter : l'élément est dans Ajouter > Contrôleur logique,
 * son écran affiche les réglages du plan, une modification faite à l'écran est enregistrée.
 * Arguments : plan.jmx (réécrit en place), dossier des captures.
 */
public class EssaiGui {
  static int echecs;
  static void verifier(String quoi, boolean ok) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
  static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
  static void pause(long ms) throws Exception { Thread.sleep(ms); edt(() -> {}); }
  static <T extends Component> T trouver(Container c, Class<T> type, String texte) {
    for (Component x : c.getComponents()) {
      if (type.isInstance(x) && (texte == null || (x instanceof AbstractButton && texte.equals(((AbstractButton) x).getText())))) return type.cast(x);
      if (x instanceof Container) { T r = trouver((Container) x, type, texte); if (r != null) return r; } }
    return null; }
  static Component parNom(Container c, String nom) {
    for (Component x : c.getComponents()) {
      if (nom.equals(x.getName())) return x;
      if (x instanceof Container) { Component r = parNom((Container) x, nom); if (r != null) return r; } }
    return null; }
  static JMeterTreeNode noeud(String nom) {
    for (JMeterTreeNode n : GuiPackage.getInstance().getTreeModel().getNodesOfType(org.apache.jmeter.testelement.TestElement.class))
      if (nom.equals(n.getName())) return n;
    return null; }
  static void capture(String nom, Component c) throws Exception {
    edt(() -> { BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
      c.paint(im.getGraphics()); try { ImageIO.write(im, "png", new File(nom)); } catch (Exception e) { throw new RuntimeException(e); } }); }

  public static void main(String[] a) throws Exception {
    String plan = a[0], docs = a[1];
    new Thread(() -> new org.apache.jmeter.JMeter().start(new String[]{"-t", plan, "-j", "jmeter-gui.log"})).start();
    for (int i = 0; i < 120 && (GuiPackage.getInstance() == null || noeud("TB_si") == null); i++) Thread.sleep(500);
    pause(2000);
    GuiPackage gp = GuiPackage.getInstance();
    JFrame cadre = gp.getMainFrame();
    JTree arbre = gp.getTreeListener().getJTree();

    final JMenu[] menu = new JMenu[1];
    edt(() -> menu[0] = MenuFactory.makeMenu(MenuFactory.CONTROLLERS, ActionNames.ADD));
    boolean present = false;
    for (Component c : menu[0].getMenuComponents())
      if (c instanceof JMenuItem && "Contrôleur de transaction Si".equals(((JMenuItem) c).getText())) present = true;
    verifier("« Contrôleur de transaction Si » dans Ajouter > " + menu[0].getText(), present);

    edt(() -> { for (int i = 0; i < arbre.getRowCount(); i++) arbre.expandRow(i); });
    edt(() -> arbre.setSelectionPath(new TreePath(noeud("TF_trace").getPath())));
    pause(1000);
    Container ecran = (Container) gp.getCurrentGui();
    verifier("écran ouvert : " + ecran.getClass().getSimpleName(), ecran.getClass().getSimpleName().equals("TransactionSiGui"));
    JTextArea condition = (JTextArea) parNom(ecran, "condition");
    verifier("condition affichée : " + condition.getText(), "${__jexl3(${n} % 2 == 1)}".equals(condition.getText()));
    JCheckBox trace = trouver(ecran, JCheckBox.class, "Condition fausse : écrire une ligne « <nom> (ignorée) »");
    JCheckBox detail = trouver(ecran, JCheckBox.class, "En cas d'échec, écrire le code et le message du premier enfant en échec");
    verifier("cases : traçage coché, détail coché", trace.isSelected() && detail.isSelected());
    edt(() -> { cadre.setSize(1250, 760); cadre.validate(); });
    pause(800);
    capture(docs + "/ecran.png", cadre.getRootPane());

    // Modification à l'écran puis enregistrement par la commande de JMeter.
    edt(() -> { condition.setText("${__jexl3(\"${profil}\" == \"acheteur\")}"); trace.setSelected(false); });
    edt(() -> arbre.setSelectionPath(new TreePath(noeud("TB_si").getPath())));
    pause(800);
    edt(() -> ActionRouter.getInstance().doActionNow(new java.awt.event.ActionEvent(cadre, 1, ActionNames.SAVE)));
    pause(2000);
    String xml = new String(Files.readAllBytes(Paths.get(plan)), "UTF-8");
    int i = xml.indexOf("testname=\"TF_trace\"");
    String bloc = xml.substring(i, xml.indexOf("</fr.jmeter.plugins.transactionsi.TransactionSi>", i));
    verifier("enregistré : nouvelle condition", bloc.contains("<stringProp name=\"TransactionSi.condition\">${__jexl3(&quot;${profil}&quot; == &quot;acheteur&quot;)}</stringProp>"));
    verifier("enregistré : traçage décoché", bloc.contains("<boolProp name=\"TransactionSi.traceIgnored\">false</boolProp>"));
    System.out.println(echecs == 0 ? "TOUT EST BON (interface)" : echecs + " ECHEC(S) (interface)");
    System.exit(echecs == 0 ? 0 : 1);
  }
}
