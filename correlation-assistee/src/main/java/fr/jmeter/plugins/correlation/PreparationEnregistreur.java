package fr.jmeter.plugins.correlation;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.proxy.ProxyControl;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleSaveConfiguration;
import org.apache.jmeter.visualizers.SimpleDataWriter;

/**
 * Ajoute sous chaque enregistreur HTTP(S) du plan un récepteur qui écrit les
 * réponses dans le format attendu par la corrélation.
 */
final class PreparationEnregistreur {

    static final String NOM = "Réponses pour la corrélation";

    private PreparationEnregistreur() {
    }

    /** @return le nombre d'enregistreurs équipés (0 si aucun enregistreur dans le plan) */
    static int preparer(String fichier) {
        GuiPackage gp = GuiPackage.getInstance();
        JMeterTreeModel modele = gp.getTreeModel();
        List<JMeterTreeNode> enregistreurs = new ArrayList<>();
        chercher((JMeterTreeNode) modele.getRoot(), enregistreurs);
        for (JMeterTreeNode e : enregistreurs) {
            ResultCollector rc = (ResultCollector) gp.createTestElement(SimpleDataWriter.class, ResultCollector.class);
            rc.setName(NOM);
            rc.setFilename(fichier);
            SampleSaveConfiguration c = rc.getSaveConfig();
            c.setAsXml(true);
            c.setResponseData(true);
            c.setResponseHeaders(true);
            c.setUrl(true);
            c.setSubresults(true);
            c.setLabel(true);
            c.setCode(true);
            c.setMessage(true);
            c.setSuccess(true);
            c.setSamplerData(false);
            c.setRequestHeaders(false);
            modele.insertNodeInto(new JMeterTreeNode(rc, modele), e, e.getChildCount());
        }
        if (!enregistreurs.isEmpty()) {
            gp.setDirty(true);
        }
        return enregistreurs.size();
    }

    private static void chercher(JMeterTreeNode n, List<JMeterTreeNode> l) {
        if (n.getTestElement() instanceof ProxyControl) {
            l.add(n);
        }
        Enumeration<?> e = n.children();
        while (e.hasMoreElements()) {
            chercher((JMeterTreeNode) e.nextElement(), l);
        }
    }
}
