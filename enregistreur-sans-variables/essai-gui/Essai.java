import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.*;
import javax.swing.tree.TreePath;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.proxy.ProxyControl;
import org.apache.jmeter.save.SaveService;

/**
 * Lance la vraie interface de JMeter sur un plan, démarre l'enregistreur qu'il
 * contient, attend que le script lancer.sh ait envoyé ses requêtes à travers
 * le proxy (fichier « requetes-envoyees »), arrête l'enregistreur et enregistre
 * le plan obtenu avec la commande « Enregistrer » de JMeter.
 * Arguments : plan.jmx resultat.jmx
 */
public class Essai {
  public static void main(String[] a) throws Exception {
    String plan = a[0], sortie = a[1];
    new Thread(() -> new org.apache.jmeter.JMeter().start(new String[]{"-t", plan, "-j", plan + ".log"})).start();
    // Ferme les boîtes de dialogue modales (certificat de l'autorité racine…).
    Thread fermeur = new Thread(() -> { while (true) { try { Thread.sleep(500); } catch (InterruptedException e) { return; }
      SwingUtilities.invokeLater(() -> { for (Window w : Window.getWindows())
        if (w instanceof JDialog && w.isVisible() && ((JDialog) w).isModal()) { System.out.println("  dialogue fermé : " + ((JDialog) w).getTitle()); w.dispose(); } }); } });
    fermeur.setDaemon(true); fermeur.start();

    JMeterTreeNode noeud = null;
    for (int i = 0; i < 120 && noeud == null; i++) {
      Thread.sleep(500);
      GuiPackage gp = GuiPackage.getInstance();
      if (gp == null || gp.getTreeModel() == null) continue;
      for (JMeterTreeNode n : gp.getTreeModel().getNodesOfType(ProxyControl.class)) noeud = n;
    }
    if (noeud == null) { System.out.println("ECHEC enregistreur introuvable dans l'arbre"); System.exit(1); }
    final JMeterTreeNode enr = noeud;
    ProxyControl modele = (ProxyControl) enr.getTestElement();
    System.out.println("  classe de l'enregistreur : " + modele.getClass().getName()
        + " / écran : " + enr.getTestElement().getPropertyAsString("TestElement.gui_class"));
    Thread.sleep(1500);
    SwingUtilities.invokeAndWait(() -> GuiPackage.getInstance().getTreeListener().getJTree()
        .setSelectionPath(new TreePath(enr.getPath())));
    Thread.sleep(1000);
    SwingUtilities.invokeLater(() -> {
      Component gui = (Component) GuiPackage.getInstance().getCurrentGui();
      AbstractButton b = trouver((Container) gui, "start");
      System.out.println("  bouton de démarrage : " + (b != null));
      if (b != null) b.doClick();
    });
    Path pret = Paths.get("proxy-pret"), envoye = Paths.get("requetes-envoyees");
    for (int i = 0; i < 60 && !Files.exists(pret); i++) { Thread.sleep(500);
      try (java.net.Socket s = new java.net.Socket("127.0.0.1", Integer.parseInt(modele.getPortString()))) { Files.write(pret, new byte[0]); } catch (IOException e) { } }
    System.out.println("  proxy à l'écoute : " + Files.exists(pret));
    for (int i = 0; i < 120 && !Files.exists(envoye); i++) Thread.sleep(500);
    Thread.sleep(3000); // l'enregistreur range les requêtes dans l'arbre sur une minuterie
    SwingUtilities.invokeAndWait(() -> modele.stopProxy());
    Thread.sleep(500);
    // Commande « Enregistrer » de JMeter, comme le ferait l'utilisateur : le
    // plan ouvert (plan.jmx) est réécrit, puis copié sous le nom demandé.
    SwingUtilities.invokeAndWait(() -> org.apache.jmeter.gui.action.ActionRouter.getInstance().doActionNow(
        new java.awt.event.ActionEvent(enr, java.awt.event.ActionEvent.ACTION_PERFORMED,
            org.apache.jmeter.gui.action.ActionNames.SAVE)));
    Files.copy(Paths.get(plan), Paths.get(sortie), StandardCopyOption.REPLACE_EXISTING);
    System.out.println("  plan enregistré : " + sortie);
    System.exit(0);
  }
  static AbstractButton trouver(Container c, String commande) {
    for (Component x : c.getComponents()) {
      if (x instanceof AbstractButton && commande.equals(((AbstractButton) x).getActionCommand())) return (AbstractButton) x;
      if (x instanceof Container) { AbstractButton r = trouver((Container) x, commande); if (r != null) return r; } }
    return null; }
}
