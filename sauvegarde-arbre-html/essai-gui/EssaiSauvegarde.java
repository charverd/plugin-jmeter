import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.TreePath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.gui.util.JMeterToolBar;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;

/**
 * Essai du bouton « Sauvegarder en HTML puis nettoyer » dans la vraie interface de JMeter, avec
 * l'arbre de résultats filtrable installé à côté. Arguments : plan.jmx
 */
public class EssaiSauvegarde {
  static int echecs;
  static void verifier(String quoi, boolean ok) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
  static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
  static void pause(long ms) throws Exception { Thread.sleep(ms); edt(() -> {}); }
  static GuiPackage gp() { return GuiPackage.getInstance(); }
  static JMeterTreeNode noeud(String nom) {
    for (JMeterTreeNode n : gp().getTreeModel().getNodesOfType(TestElement.class)) if (nom.equals(n.getName())) return n;
    return null; }
  static JMeterToolBar barre() { return chercher(gp().getMainFrame().getContentPane()); }
  static JMeterToolBar chercher(Container c) { for (Component x : c.getComponents()) { if (x instanceof JMeterToolBar) return (JMeterToolBar) x;
    if (x instanceof Container) { JMeterToolBar t = chercher((Container) x); if (t != null) return t; } } return null; }
  static JButton boutonCommande(String cmd) { for (Component c : barre().getComponents()) if (c instanceof JButton && cmd.equals(((JButton) c).getActionCommand())) return (JButton) c; return null; }
  static JButton notreBouton() { for (Component c : barre().getComponents()) if ("fr.jmeter.plugins.sauvegarde.bouton".equals(c.getName())) return (JButton) c; return null; }
  static int indexDans(Container p, Component c) { for (int i = 0; i < p.getComponentCount(); i++) if (p.getComponent(i) == c) return i; return -1; }

  /** Nombre de résultats conservés par un écran (champ privé « buffer » ou « tous »). */
  static int nb(String nomNoeud, String champ) throws Exception {
    Object gui = gp().getGui(noeud(nomNoeud).getTestElement());
    for (Class<?> c = gui.getClass(); c != null; c = c.getSuperclass()) {
      try { Field f = c.getDeclaredField(champ); f.setAccessible(true); Collection<?> col = (Collection<?>) f.get(gui); synchronized (col) { return col.size(); } }
      catch (NoSuchFieldException e) { /* parent */ } }
    return -1; }
  static void selectionner(String nom) throws Exception {
    edt(() -> gp().getTreeListener().getJTree().setSelectionPath(new TreePath(noeud(nom).getPath()))); pause(600); }
  static void lancerTir() throws Exception {
    edt(() -> ActionRouter.getInstance().doActionNow(new java.awt.event.ActionEvent(gp().getMainFrame(), 0, ActionNames.ACTION_START)));
    pause(1000);
    for (int i = 0; i < 120 && !boutonCommande(ActionNames.ACTION_START).isEnabled(); i++) Thread.sleep(250);
    pause(1500); // laisse le rafraîchissement (500 ms) de l'arbre passer
  }
  static List<File> pages(File dossier) { File[] f = dossier.listFiles((d, n) -> n.endsWith(".html")); List<File> l = new ArrayList<>(Arrays.asList(f == null ? new File[0] : f)); Collections.sort(l); return l; }
  static JsonNode donnees(File f) throws Exception {
    String h = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    String debut = "<script id=\"donnees\" type=\"application/json\">";
    int i = h.indexOf(debut) + debut.length();
    return new ObjectMapper().readTree(h.substring(i, h.indexOf("</script>", i))); }
  static JDialog dialogue(String titre) { for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible() && titre.equals(((JDialog) w).getTitle())) return (JDialog) w; return null; }
  static String texte(Container c) { StringBuilder sb = new StringBuilder(); for (Component x : c.getComponents()) {
      if (x instanceof JLabel) sb.append(((JLabel) x).getText()).append('\n'); if (x instanceof JTextArea) sb.append(((JTextArea) x).getText()).append('\n');
      if (x instanceof Container) sb.append(texte((Container) x)); } return sb.toString(); }
  static void capture(String nom, Component c) throws Exception {
    edt(() -> { BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
      c.paint(im.getGraphics()); try { ImageIO.write(im, "png", new File(nom)); } catch (Exception e) { throw new RuntimeException(e); } }); }

  public static void main(String[] a) throws Exception {
    File dossier = new File(a[0]).getAbsoluteFile().getParentFile();
    new Thread(() -> new org.apache.jmeter.JMeter().start(new String[]{"-t", a[0], "-j", "jmeter-gui.log",
        "-Jsauvegarde.arbre.html.message=false"})).start();
    for (int i = 0; i < 120 && (gp() == null || gp().getTreeModel() == null || noeud("Arbre filtrable") == null); i++) Thread.sleep(500);
    pause(2500);

    // 1. Bouton et menu.
    JButton nous = notreBouton(), tout = boutonCommande(ActionNames.CLEAR_ALL);
    verifier("bouton présent dans la barre d'outils", nous != null);
    verifier("bouton placé juste après « Nettoyer tout »", nous != null && indexDans(barre(), nous) == indexDans(barre(), tout) + 1);
    List<String> menu = new ArrayList<>(); JMenuBar mb = gp().getMainFrame().getJMenuBar();
    for (int i = 0; i < mb.getMenuCount(); i++) for (Component c : mb.getMenu(i).getMenuComponents())
      if (c instanceof JMenuItem && ((JMenuItem) c).getText() != null && ((JMenuItem) c).getText().startsWith("Sauvegarder")) menu.add(mb.getMenu(i).getText() + " > " + ((JMenuItem) c).getText());
    menu.forEach(m -> System.out.println("  " + m));
    verifier("3 entrées dans le menu Lancer", menu.size() == 3 && menu.get(0).startsWith(JMeterUtils.getResString("run") + " > "));

    // 2. Tir : 2 itérations × (T01 avec 2 requêtes, panier, erreur) = 6 résultats de premier niveau.
    lancerTir();
    int std = nb("Arbre standard", "buffer"), tous = nb("Arbre filtrable", "tous"), affiches = nb("Arbre filtrable", "buffer");
    System.out.println("  standard " + std + ", filtrable " + tous + " conservés / " + affiches + " affichés");
    verifier("tir terminé : 6 résultats, filtre « panier » en affiche 2", std == 6 && tous == 6 && affiches == 2);
    selectionner("Arbre filtrable");
    capture("1-avant.png", gp().getMainFrame().getContentPane());

    // 3. Arbre filtrable affiché → sauvegardé puis nettoyé seul (comme « Nettoyer »).
    edt(() -> notreBouton().doClick()); pause(800);
    List<File> p = pages(dossier);
    verifier("une page HTML écrite à côté du .jmx", p.size() == 1 && p.get(0).getName().matches("plan_arbre-resultats_\\d{8}-\\d{6}\\.html"));
    verifier("arbre filtrable nettoyé, arbre standard intact", nb("Arbre filtrable", "tous") == 0 && nb("Arbre filtrable", "buffer") == 0 && nb("Arbre standard", "buffer") == 6);
    JsonNode d = donnees(p.get(0));
    JsonNode arb = d.path("arbres");
    verifier("page : l'arbre filtrable seul, avec ses 6 résultats (masqués compris)", arb.size() == 1 && "Arbre filtrable".equals(arb.path(0).path("nom").asText()) && arb.path(0).path("resultats").size() == 6);
    verifier("page : filtre « panier » repris", "panier".equals(arb.path(0).path("filtre").path("motif").asText()) && arb.path(0).path("filtrable").asBoolean());
    JsonNode r0 = arb.path(0).path("resultats");
    verifier("page : transaction T01 avec ses 2 requêtes", "T01_Accueil".equals(r0.path(0).path("l").asText()) && r0.path(0).path("tx").asBoolean() && r0.path(0).path("enf").size() == 2);
    verifier("page : corps HTML gardé tel quel (piège </script>)", r0.path(0).path("enf").path(0).path("corps").asText().contains("'</script><!--'") && r0.path(0).path("enf").path(0).path("corps").asText().contains("Bienvenue é"));
    verifier("page : image embarquée", "image/png".equals(r0.path(0).path("enf").path(1).path("mime").asText()) && r0.path(0).path("enf").path(1).path("img").asText().startsWith("iVBOR"));
    JsonNode err = r0.path(2);
    verifier("page : erreur 500 en échec, assertion en nœud enfant", "GET /erreur".equals(err.path("l").asText()) && !err.path("ok").asBoolean() && "500".equals(err.path("code").asText())
        && err.path("enf").path(0).path("a").asBoolean() && err.path("enf").path(0).path("stats").asText().contains("Test failed"));
    verifier("page : requête et en-têtes", err.path("req").asText().startsWith("GET http://127.0.0.1:18790/erreur") && err.path("resh").asText().contains("Content-Type"));
    verifier("page : libellés de JMeter en français", "Résultat de l'échantillon".equals(d.path("libelles").path("view_results_tab_sampler").asText()));
    verifier("page : origine « Nettoyer »", d.path("origine").asText().contains("« Nettoyer »"));
    Files.move(p.get(0).toPath(), dossier.toPath().resolve("page-filtrable.html"));

    // 4. Échec d'écriture : rien n'est nettoyé.
    JMeterUtils.setProperty("sauvegarde.arbre.html.dossier", "/proc/impossible");
    selectionner("Arbre standard");
    SwingUtilities.invokeLater(() -> notreBouton().doClick());
    JDialog e = null; for (int i = 0; i < 40 && (e = dialogue("Sauvegarde de l'arbre de résultats")) == null; i++) Thread.sleep(250);
    String msg = e == null ? "" : texte(e.getContentPane());
    verifier("échec d'écriture signalé", e != null && msg.contains("rien n'a été nettoyé"));
    if (e != null) { final JDialog fe = e; edt(fe::dispose); pause(500); }
    verifier("échec d'écriture : l'arbre n'est pas nettoyé", nb("Arbre standard", "buffer") == 6);
    JMeterUtils.getJMeterProperties().remove("sauvegarde.arbre.html.dossier");

    // 5. Autre élément affiché → tous les arbres, puis « Nettoyer tout ». Message activé cette fois.
    lancerTir();
    verifier("deuxième tir : standard 12, filtrable 6", nb("Arbre standard", "buffer") == 12 && nb("Arbre filtrable", "tous") == 6);
    JMeterUtils.setProperty("sauvegarde.arbre.html.message", "true");
    selectionner("Utilisateurs");
    SwingUtilities.invokeLater(() -> notreBouton().doClick());
    JDialog m = null; for (int i = 0; i < 40 && (m = dialogue("Sauvegarde de l'arbre de résultats")) == null; i++) Thread.sleep(250);
    String info = m == null ? "" : texte(m.getContentPane());
    System.out.println("  message : " + info.replace('\n', ' ').trim());
    verifier("message : 18 résultats, 2 arbres, filtre signalé", info.contains("18 résultats sauvegardés (2 arbres), puis nettoyés.") && info.contains("filtre « panier »"));
    if (m != null) { capture("2-message.png", m.getContentPane()); final JDialog fm = m; edt(fm::dispose); pause(500); }
    p = pages(dossier); p.removeIf(f -> f.getName().startsWith("page-"));
    verifier("tous les récepteurs nettoyés", nb("Arbre standard", "buffer") == 0 && nb("Arbre filtrable", "tous") == 0);
    JsonNode d2 = donnees(p.get(0));
    verifier("page : 2 arbres (12 + 6) dans l'ordre du plan", d2.path("arbres").size() == 2 && d2.path("arbres").path(0).path("resultats").size() == 12
        && d2.path("arbres").path(1).path("resultats").size() == 6 && "Essai sauvegarde HTML > Arbre filtrable".equals(d2.path("arbres").path(1).path("chemin").asText()));
    verifier("page : origine « Nettoyer tout »", d2.path("origine").asText().contains("« Nettoyer tout »"));
    Files.move(p.get(0).toPath(), dossier.toPath().resolve("page-tous.html"));

    // 6. Arbres vides : le bouton nettoie sans écrire de fichier.
    edt(() -> notreBouton().doClick()); pause(500);
    p = pages(dossier); p.removeIf(f -> f.getName().startsWith("page-"));
    verifier("rien à sauvegarder : aucun fichier", p.isEmpty());

    // 7. Changement de langue : JMeter reconstruit la barre d'outils, le bouton revient.
    edt(() -> JMeterUtils.setLocale(Locale.ENGLISH)); pause(1500);
    JButton apres = notreBouton();
    verifier("bouton réinstallé après changement de langue", apres != null && indexDans(barre(), apres) == indexDans(barre(), boutonCommande(ActionNames.CLEAR_ALL)) + 1);
    edt(() -> JMeterUtils.setLocale(Locale.FRENCH)); pause(1500);
    capture("3-barre.png", barre());

    System.out.println(echecs == 0 ? "TOUT EST BON" : echecs + " ECHEC(S)");
    System.exit(0);
  }
}
