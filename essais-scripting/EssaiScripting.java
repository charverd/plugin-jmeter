import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
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
import org.apache.jmeter.protocol.http.proxy.ProxyControl;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.testelement.TestElement;

/**
 * Essai de bout en bout des trois plugins de scripting dans la vraie interface
 * de JMeter : deux enregistrements réels, nettoyage, corrélation, vérification,
 * enregistrement du plan. lancer.sh rejoue ensuite le plan contre l'application.
 * Arguments : plan.jmx port-appli
 */
public class EssaiScripting {
  static int echecs;
  static void verifier(String quoi, boolean ok) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
  static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
  static void pause(long ms) throws Exception { Thread.sleep(ms); edt(() -> {}); }
  static GuiPackage gp() { return GuiPackage.getInstance(); }
  static JMeterTreeNode noeud(String nom) {
    for (JMeterTreeNode n : gp().getTreeModel().getNodesOfType(TestElement.class)) if (nom.equals(n.getName())) return n;
    return null; }
  static List<JMeterTreeNode> enfants(JMeterTreeNode n) { List<JMeterTreeNode> l = new ArrayList<>(); for (int i = 0; i < n.getChildCount(); i++) l.add((JMeterTreeNode) n.getChildAt(i)); return l; }
  static List<JMeterTreeNode> requetes(JMeterTreeNode n) { List<JMeterTreeNode> l = new ArrayList<>(); coll(n, l); return l; }
  static void coll(JMeterTreeNode n, List<JMeterTreeNode> l) { if (n.getTestElement() instanceof HTTPSamplerBase) l.add(n); for (JMeterTreeNode c : enfants(n)) coll(c, l); }
  static void selectionner(JMeterTreeNode... ns) throws Exception {
    edt(() -> { TreePath[] p = new TreePath[ns.length]; for (int i = 0; i < ns.length; i++) p[i] = new TreePath(ns[i].getPath());
      gp().getTreeListener().getJTree().setSelectionPaths(p); }); pause(500); }
  static JDialog dialogue(String titre) { for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible() && titre.equals(((JDialog) w).getTitle())) return (JDialog) w; return null; }
  static <T extends Component> T trouver(Container c, Class<T> type, String texte) {
    for (Component x : c.getComponents()) {
      if (type.isInstance(x) && (texte == null || (x instanceof AbstractButton && texte.equals(((AbstractButton) x).getText())))) return type.cast(x);
      if (x instanceof Container) { T r = trouver((Container) x, type, texte); if (r != null) return r; } }
    return null; }
  static <T> List<T> tous(Container c, Class<T> k) { List<T> l = new ArrayList<>(); tous(c, k, l); return l; }
  static <T> void tous(Container c, Class<T> k, List<T> l) { for (Component x : c.getComponents()) { if (k.isInstance(x)) l.add(k.cast(x)); if (x instanceof Container) tous((Container) x, k, l); } }
  static void cliquer(JDialog d, String bouton) throws Exception { edt(() -> trouver(d.getContentPane(), JButton.class, bouton).doClick()); pause(600); }
  static void capture(String nom, Component c) throws Exception {
    edt(() -> { BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
      c.paint(im.getGraphics()); try { ImageIO.write(im, "png", new File(nom)); } catch (Exception e) { throw new RuntimeException(e); } }); }
  static void parcours(String portAppli) throws Exception {
    Process p = new ProcessBuilder("python3", System.getProperty("ici") + "/parcours.py", "18901", portAppli).redirectErrorStream(true).start();
    String sortie = new String(p.getInputStream().readAllBytes()).trim(); p.waitFor();
    System.out.println("  " + sortie.replace("\n", "\n  ")); }
  static void enregistrer(JMeterTreeNode cible, String portAppli) throws Exception {
    JMeterTreeNode enr = gp().getTreeModel().getNodesOfType(ProxyControl.class).get(0);
    ProxyControl modele = (ProxyControl) enr.getTestElement();
    edt(() -> gp().getTreeListener().getJTree().setSelectionPath(new TreePath(enr.getPath())));
    pause(800);
    SwingUtilities.invokeLater(() -> { for (Component c : tous((Container) gp().getCurrentGui(), AbstractButton.class)) if ("start".equals(((AbstractButton) c).getActionCommand())) ((AbstractButton) c).doClick(); });
    for (int i = 0; i < 60; i++) { Thread.sleep(500); try (java.net.Socket s = new java.net.Socket("127.0.0.1", 18901)) { break; } catch (Exception e) { } }
    modele.setTarget(cible);
    parcours(portAppli);
    pause(3000);
    // Par le bouton Arrêter, pour que l'écran de l'enregistreur sache qu'il est arrêté.
    edt(() -> gp().getTreeListener().getJTree().setSelectionPath(new TreePath(enr.getPath())));
    pause(500);
    edt(() -> { for (Component c : tous((Container) gp().getCurrentGui(), AbstractButton.class)) if ("stop".equals(((AbstractButton) c).getActionCommand())) ((AbstractButton) c).doClick(); });
    pause(1000);
    for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible() && !((JDialog) w).isModal()
        && ((JDialog) w).getTitle() != null && ((JDialog) w).getTitle().contains("Recorder")) edt(() -> w.setVisible(false)); }

  public static void main(String[] a) throws Exception {
    String plan = a[0], portAppli = a[1];
    Thread fermeur = new Thread(() -> { while (true) { try { Thread.sleep(400); } catch (InterruptedException e) { return; }
      SwingUtilities.invokeLater(() -> { for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible() && ((JDialog) w).isModal()) w.dispose(); }); } });
    fermeur.setDaemon(true); fermeur.start();
    new Thread(() -> new org.apache.jmeter.JMeter().start(new String[]{"-t", plan, "-j", "jmeter-gui.log"})).start();
    for (int i = 0; i < 120 && (gp() == null || gp().getTreeModel() == null || noeud("Enregistrement B") == null); i++) Thread.sleep(500);
    pause(2000);
    JMeterTreeNode A = noeud("Enregistrement A"), B = noeud("Enregistrement B");
    String jtl = new File("reponses-A.jtl").getAbsolutePath();

    // --- Préparation de l'enregistreur (corrélation), puis deux enregistrements réels.
    Class<?> prep = Class.forName("fr.jmeter.plugins.correlation.PreparationEnregistreur");
    Method m = prep.getDeclaredMethod("preparer", String.class); m.setAccessible(true);
    final int[] equipes = new int[1];
    edt(() -> { try { equipes[0] = (Integer) m.invoke(null, jtl); } catch (Exception e) { throw new RuntimeException(e); } });
    verifier("« Préparer l'enregistreur » ajoute le récepteur des réponses", equipes[0] == 1 && noeud("Réponses pour la corrélation") != null);
    enregistrer(A, portAppli);
    JMeterTreeNode ecrivain = noeud("Réponses pour la corrélation");
    edt(() -> { ecrivain.setEnabled(false); gp().getTreeModel().nodeChanged(ecrivain); });
    enregistrer(B, portAppli);
    verifier("A et B enregistrés (" + requetes(A).size() + " / " + requetes(B).size() + " requêtes)", requetes(A).size() == 8 && requetes(B).size() == 8);
    String contenuJtl = Files.exists(Paths.get(jtl)) ? new String(Files.readAllBytes(Paths.get(jtl))) : "";
    verifier("le fichier des réponses de A contient la page de connexion", contenuJtl.contains("name=&quot;csrf&quot;") || contenuJtl.contains("name=\"csrf\""));
    verifier("et seulement A (une seule page /login)", contenuJtl.split("lb=\"/login").length - 1 == 2);

    // --- Nettoyage d'enregistrement sur A et B.
    selectionner(A, B);
    edt(() -> { try { Class.forName("fr.jmeter.plugins.nettoyage.FenetreNettoyage").getMethod("ouvrir").invoke(null); } catch (Exception e) { throw new RuntimeException(e); } });
    pause(800);
    JDialog dn = dialogue("Nettoyer un enregistrement");
    verifier("fenêtre de nettoyage ouverte", dn != null);
    JTable domaines = trouver(dn.getContentPane(), JTable.class, null);
    edt(() -> { TableModel t = domaines.getModel(); for (int i = 0; i < t.getRowCount(); i++) if ("127.0.0.2".equals(t.getValueAt(i, 1))) t.setValueAt(Boolean.TRUE, i, 0); });
    cliquer(dn, "Aperçu");
    String apercu = trouver(dn.getContentPane(), JTextArea.class, null).getText();
    System.out.println("  " + apercu.split("\n")[0]);
    verifier("aperçu : 10 requêtes supprimées, 4 transactions", apercu.startsWith("10 requête(s) supprimée(s), 4 transaction(s)"));
    capture("1-nettoyage.png", dn.getContentPane());
    cliquer(dn, "Appliquer");
    List<String> sousA = new ArrayList<>(); for (JMeterTreeNode c : enfants(A)) sousA.add(c.getName() + "(" + c.getChildCount() + ")");
    System.out.println("  A après nettoyage : " + sousA);
    verifier("A : 2 transactions (login, detail) et 3 requêtes", enfants(A).size() == 2 && requetes(A).size() == 3
        && enfants(A).get(0).getName().endsWith("_login") && enfants(A).get(1).getName().endsWith("_detail"));
    edt(() -> dn.setVisible(false));

    // --- Corrélation assistée.
    selectionner(A, B);
    edt(() -> { try { Class.forName("fr.jmeter.plugins.correlation.FenetreCorrelation").getMethod("ouvrir").invoke(null); } catch (Exception e) { throw new RuntimeException(e); } });
    pause(800);
    JDialog dc = dialogue("Corrélation assistée");
    verifier("fenêtre de corrélation ouverte", dc != null);
    edt(() -> tous(dc.getContentPane(), JTextField.class).get(0).setText(jtl));
    cliquer(dc, "Analyser");
    JTable cand = trouver(dc.getContentPane(), JTable.class, null);
    List<String> lignes = new ArrayList<>();
    edt(() -> { TableModel t = cand.getModel(); for (int i = 0; i < t.getRowCount(); i++) lignes.add(t.getValueAt(i, 0) + " " + t.getValueAt(i, 1) + " [" + t.getValueAt(i, 2) + "] " + t.getValueAt(i, 3) + " ← " + t.getValueAt(i, 5) + " : " + t.getValueAt(i, 6)); });
    for (String l : lignes) System.out.println("  " + l);
    verifier("le jeton csrf est trouvé, avec sa source", lignes.stream().anyMatch(l -> l.startsWith("true csrf") && l.contains("← /login")));
    verifier("le numéro de commande est trouvé, avec sa source", lignes.stream().anyMatch(l -> l.contains("[nombre]") && l.startsWith("true")));
    capture("2-correlation.png", dc.getContentPane());
    cliquer(dc, "Appliquer les lignes cochées");
    System.out.println("  " + trouver(dc.getContentPane(), JButton.class, "Appliquer les lignes cochées").getParent().getComponent(0).toString().replaceAll(".*text=([^,]*),.*", "$1"));
    List<JMeterTreeNode> ra = requetes(A);
    HTTPSamplerBase post = (HTTPSamplerBase) ra.get(1).getTestElement(), detail = (HTTPSamplerBase) ra.get(2).getTestElement();
    System.out.println("  POST /login : " + post.getArguments().getArgumentsAsMap() + " ; détail : " + detail.getPath());
    verifier("POST /login envoie csrf=${csrf}", "${csrf}".equals(post.getArguments().getArgumentsAsMap().get("csrf")));
    verifier("le détail utilise la commande extraite", detail.getPath().matches("/commande/\\$\\{[a-z_0-9]+\\}/detail"));
    long extracteurs = gp().getTreeModel().getNodesOfType(org.apache.jmeter.extractor.BoundaryExtractor.class).size();
    verifier("2 extracteurs créés (" + extracteurs + ")", extracteurs == 2);
    edt(() -> dc.setVisible(false));

    // --- Vérificateur.
    // Désactiver B par la commande de JMeter (l'écran ouvert réécrirait sinon l'état activé).
    selectionner(B);
    edt(() -> ActionRouter.getInstance().doActionNow(new java.awt.event.ActionEvent(B, 1, ActionNames.DISABLE)));
    pause(500);
    verifier("B désactivé", !B.isEnabled());
    edt(() -> { try { Class.forName("fr.jmeter.plugins.verif.FenetreVerification").getMethod("ouvrir").invoke(null); } catch (Exception e) { throw new RuntimeException(e); } });
    pause(800);
    JDialog dv = dialogue("Vérification du plan");
    verifier("fenêtre de vérification ouverte", dv != null);
    JTable cons = trouver(dv.getContentPane(), JTable.class, null);
    Set<String> regles = new TreeSet<>();
    edt(() -> { TableModel t = cons.getModel(); for (int i = 0; i < t.getRowCount(); i++) { regles.add((String) t.getValueAt(i, 0));
      System.out.println("  " + t.getValueAt(i, 0) + " " + t.getValueAt(i, 1) + " — " + t.getValueAt(i, 2) + " : " + t.getValueAt(i, 3)); } });
    verifier("V01 enregistreur, V06 sans assertion, V07 serveur en dur, V08 sans minuterie, V13 plugins : " + regles,
        regles.containsAll(Arrays.asList("V01", "V06", "V07", "V08", "V13")));
    verifier("pas de faux positif V05/V10/V14", !regles.contains("V05") && !regles.contains("V10") && !regles.contains("V14"));
    capture("3-verification.png", dv.getContentPane());
    // Double-clic → l'élément est sélectionné dans l'arbre.
    edt(() -> { cons.setRowSelectionInterval(0, 0); trouver(dv.getContentPane(), JButton.class, "Montrer dans l'arbre").doClick(); });
    pause(400);
    verifier("« Montrer dans l'arbre » sélectionne l'élément", gp().getTreeListener().getJTree().getSelectionCount() == 1);
    edt(() -> dv.setVisible(false));

    // --- Enregistrer le plan (commande Enregistrer de JMeter).
    edt(() -> ActionRouter.getInstance().doActionNow(new java.awt.event.ActionEvent(cons, 1, ActionNames.SAVE)));
    pause(800);
    System.out.println(echecs == 0 ? "TOUT EST BON (interface)" : echecs + " ECHEC(S)");
    System.exit(echecs);
  }
}
