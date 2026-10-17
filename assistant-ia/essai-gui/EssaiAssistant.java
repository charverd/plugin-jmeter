import java.awt.*;
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
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.testelement.TestElement;

/**
 * Essai de l'assistant IA dans la vraie interface de JMeter, contre le faux
 * serveur OpenAI (faux_openai.py). Arguments : plan.jmx
 */
public class EssaiAssistant {
  static int echecs;
  static void verifier(String quoi, boolean ok) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
  static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
  static void pause(long ms) throws Exception { Thread.sleep(ms); edt(() -> {}); }
  static GuiPackage gp() { return GuiPackage.getInstance(); }
  static JMeterTreeNode noeud(String nom) {
    for (JMeterTreeNode n : gp().getTreeModel().getNodesOfType(TestElement.class)) if (nom.equals(n.getName())) return n;
    return null; }
  static JDialog dialogue(String titre) { for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible() && titre.equals(((JDialog) w).getTitle())) return (JDialog) w; return null; }
  static <T extends Component> T trouver(Container c, Class<T> type, String texte) {
    for (Component x : c.getComponents()) {
      if (type.isInstance(x) && (texte == null || (x instanceof AbstractButton && texte.equals(((AbstractButton) x).getText())))) return type.cast(x);
      if (x instanceof Container) { T r = trouver((Container) x, type, texte); if (r != null) return r; } }
    return null; }
  static <T> List<T> tous(Container c, Class<T> k) { List<T> l = new ArrayList<>(); tous(c, k, l); return l; }
  static <T> void tous(Container c, Class<T> k, List<T> l) { for (Component x : c.getComponents()) { if (k.isInstance(x)) l.add(k.cast(x)); if (x instanceof Container) tous((Container) x, k, l); } }
  static void cliquer(JDialog d, String b) throws Exception { edt(() -> trouver(d.getContentPane(), JButton.class, b).doClick()); pause(500); }
  static void capture(String nom, Component c) throws Exception {
    edt(() -> { BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
      c.paint(im.getGraphics()); try { ImageIO.write(im, "png", new File(nom)); } catch (Exception e) { throw new RuntimeException(e); } }); }
  static String etat(JDialog d) { for (JLabel l : tous(d.getContentPane(), JLabel.class)) if (l.getText().startsWith("Terminé") || l.getText().startsWith("Erreur") || l.getText().startsWith("Prêt") || l.getText().contains("…")) return l.getText(); return ""; }
  static void poser(JDialog d, String question) throws Exception {
    edt(() -> { JTextArea saisie = null; for (JTextArea t : tous(d.getContentPane(), JTextArea.class)) if (t.isEditable()) saisie = t; saisie.setText(question); });
    cliquer(d, "Envoyer (Ctrl+Entrée)");
    for (int i = 0; i < 100 && !(etat(d).startsWith("Terminé") || etat(d).startsWith("Erreur")); i++) Thread.sleep(200);
    pause(300); }

  public static void main(String[] a) throws Exception {
    new Thread(() -> new org.apache.jmeter.JMeter().start(new String[]{"-t", a[0], "-j", "jmeter-gui.log"})).start();
    for (int i = 0; i < 120 && (gp() == null || gp().getTreeModel() == null || noeud("Debug") == null); i++) Thread.sleep(500);
    pause(2000);
    JMenuItem item = null; JMenuBar barre = gp().getMainFrame().getJMenuBar();
    for (int i = 0; i < barre.getMenuCount(); i++) for (Component c : barre.getMenu(i).getMenuComponents())
      if (c instanceof JMenuItem && "Assistant IA…".equals(((JMenuItem) c).getText())) item = (JMenuItem) c;
    verifier("« Assistant IA… » dans le menu Outils", item != null);

    // Catalogue du prompt : chaque fragment doit se charger sans propriété inconnue ;
    // la minuterie inventée par Bonsai lors d'un essai réel doit être signalée.
    Class<?> app = Class.forName("fr.jmeter.plugins.ia.ApplicationActions");
    Class<?> val = Class.forName("fr.jmeter.plugins.ia.ValidationJmx");
    Class<?> ps = Class.forName("fr.jmeter.plugins.ia.PromptSysteme");
    java.lang.reflect.Method charger = app.getDeclaredMethod("charger", String.class); charger.setAccessible(true);
    java.lang.reflect.Method avert = val.getDeclaredMethod("avertissements", org.apache.jorphan.collections.HashTree.class); avert.setAccessible(true);
    java.lang.reflect.Field cat = ps.getDeclaredField("CATALOGUE"); cat.setAccessible(true);
    for (String ligne : (String[]) cat.get(null)) {
      String xml = ligne.substring(ligne.indexOf('<'));
      final Object[] res = new Object[1];
      edt(() -> { try { res[0] = avert.invoke(null, charger.invoke(null, xml)); } catch (Exception e) { res[0] = e.getCause(); } });
      verifier("catalogue sans avertissement : " + ligne.substring(2, ligne.indexOf(':')).trim() + (res[0] instanceof List && ((List<?>) res[0]).isEmpty() ? "" : " → " + res[0]),
          res[0] instanceof List && ((List<?>) res[0]).isEmpty());
    }
    String invente = "<ConstantTimer guiclass=\"ConstantTimerGui\" testclass=\"ConstantTimer\" testname=\"t\" enabled=\"true\"><stringProp name=\"ConstantTimer.value\">2</stringProp><intProp name=\"ConstantTimer.unit\">1000</intProp></ConstantTimer><hashTree/>";
    final Object[] r2 = new Object[1];
    edt(() -> { try { r2[0] = avert.invoke(null, charger.invoke(null, invente)); } catch (Exception e) { r2[0] = e; } });
    System.out.println("  minuterie inventée : " + r2[0]);
    verifier("propriétés inventées signalées", r2[0].toString().contains("ConstantTimer.value") && r2[0].toString().contains("ConstantTimer.unit"));

    // Sélection : la transaction, puis ouverture par le menu.
    edt(() -> gp().getTreeListener().getJTree().setSelectionPath(new TreePath(noeud("T01").getPath())));
    pause(500);
    final JMenuItem it = item; edt(() -> it.doClick()); pause(800);
    JDialog d = dialogue("Assistant IA");
    verifier("fenêtre ouverte", d != null);

    // Réglages : lister les modèles du serveur.
    SwingUtilities.invokeLater(() -> trouver(d.getContentPane(), JButton.class, "Réglages…").doClick());
    pause(1000);
    JDialog r = dialogue("Réglages de l'assistant IA");
    verifier("réglages ouverts", r != null);
    if (r != null) {
      cliquer(r, "Lister les modèles"); pause(800);
      JComboBox<?> modeles = null; for (JComboBox<?> c : tous(r.getContentPane(), JComboBox.class)) if (c.isEditable()) modeles = c;
      verifier("« Lister les modèles » interroge /v1/models (" + modeles.getItemCount() + ")", modeles.getItemCount() == 2);
      cliquer(r, "Tester"); pause(1200);
      boolean testOk = false; for (JLabel l : tous(r.getContentPane(), JLabel.class)) if (l.getText().contains("« OK »")) testOk = true;
      verifier("« Tester » obtient une réponse", testOk);
      capture("1-reglages.png", r.getContentPane());
      edt(() -> trouver(r.getContentPane(), JButton.class, "Annuler").doClick()); pause(400);
    }

    // Question.
    poser(d, "Ajoute une assertion 200 sur la commande, passe-la en v2, renomme la transaction, désactive le debug et supprime la pause.");
    System.out.println("  état : " + etat(d));
    verifier("réponse reçue en flux", etat(d).startsWith("Terminé"));
    List<String> lignes = Files.readAllLines(Paths.get("requetes.jsonl"));
    com.fasterxml.jackson.databind.JsonNode req = new com.fasterxml.jackson.databind.ObjectMapper().readTree(lignes.get(lignes.size() - 1)).path("corps");
    String systeme = req.path("messages").path(0).path("content").asText(), user = req.path("messages").path(1).path("content").asText();
    verifier("prompt système JMeter envoyé", req.path("messages").path(0).path("role").asText().equals("system") && systeme.contains("jmeter-actions"));
    verifier("arborescence jointe avec ids", user.contains("### Arborescence du plan") && user.contains("TransactionController « T01 »"));
    verifier("XML de la sélection joint", user.contains("### Éléments sélectionnés") && user.contains("<TransactionController"));
    verifier("aucun max_tokens envoyé", !req.has("max_tokens") && req.path("stream").asBoolean());

    JTable actions = null; for (JTable t : tous(d.getContentPane(), JTable.class)) actions = t;
    final JTable ta = actions; List<String> vues = new ArrayList<>();
    edt(() -> { TableModel m = ta.getModel(); for (int i = 0; i < m.getRowCount(); i++) vues.add(m.getValueAt(i, 0) + " " + m.getValueAt(i, 1) + " " + m.getValueAt(i, 2) + " | " + m.getValueAt(i, 4)); });
    for (String v : vues) System.out.println("  " + v);
    verifier("6 actions proposées, l'id inconnu signalé et décoché", vues.size() == 6 && vues.get(5).startsWith("false") && vues.get(5).contains("id inconnu"));
    verifier("aucun avertissement sur les 5 actions valides", vues.subList(0, 5).stream().allMatch(v -> v.endsWith("| ")));
    capture("2-actions.png", d.getContentPane());

    cliquer(d, "Appliquer les actions cochées"); pause(500);
    JMeterTreeNode cmd = noeud("/api/commande");
    boolean assertion = false; for (int i = 0; cmd != null && i < cmd.getChildCount(); i++) if ("Code 200".equals(((JMeterTreeNode) cmd.getChildAt(i)).getName())) assertion = true;
    verifier("assertion ajoutée sous /api/commande", assertion);
    verifier("chemin passé en v2", cmd != null && "/api/v2/commande".equals(((HTTPSamplerBase) cmd.getTestElement()).getPath()));
    verifier("transaction renommée", noeud("Connexion") != null && noeud("T01") == null);
    verifier("Debug désactivé", noeud("Debug") != null && !noeud("Debug").isEnabled());
    verifier("minuterie supprimée", noeud("Pause inutile") == null);

    cliquer(d, "Annuler la dernière application"); pause(500);
    verifier("annulation : tout revient", noeud("T01") != null && noeud("Pause inutile") != null && noeud("Debug").isEnabled()
        && "/api/commande".equals(((HTTPSamplerBase) noeud("/api/commande").getTestElement()).getPath()) && noeud("Code 200") == null);

    // Deuxième échange (l'historique suit), application, enregistrement.
    poser(d, "Recommence, s'il te plaît.");
    lignes = Files.readAllLines(Paths.get("requetes.jsonl"));
    req = new com.fasterxml.jackson.databind.ObjectMapper().readTree(lignes.get(lignes.size() - 1)).path("corps");
    verifier("l'historique est renvoyé (" + req.path("messages").size() + " messages), contexte joint au dernier seulement",
        req.path("messages").size() == 4 && !req.path("messages").path(1).path("content").asText().contains("### Arborescence"));
    cliquer(d, "Appliquer les actions cochées"); pause(500);
    edt(() -> ActionRouter.getInstance().doActionNow(new java.awt.event.ActionEvent(d, 1, ActionNames.SAVE)));
    pause(800);
    System.out.println(echecs == 0 ? "TOUT EST BON (interface)" : echecs + " ECHEC(S)");
    System.exit(echecs);
  }
}
