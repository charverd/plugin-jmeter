package fr.jmeter.plugins.verif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.jmeter.assertions.ResponseAssertion;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.extractor.DebugPostProcessor;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.proxy.ProxyControl;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.sampler.DebugSampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.timers.ConstantTimer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReglesTest {

    private JMeterTreeNode plan;
    private JMeterTreeNode groupe;
    private LoopController boucle;

    private static JMeterTreeNode noeud(TestElement e, JMeterTreeNode parent) {
        JMeterTreeNode n = new JMeterTreeNode(e, null);
        if (parent != null) {
            parent.add(n);
        }
        return n;
    }

    private static HTTPSamplerProxy requete(String nom, String domaine) {
        HTTPSamplerProxy s = new HTTPSamplerProxy();
        s.setName(nom);
        s.setDomain(domaine);
        s.setPath("/x");
        return s;
    }

    private static List<Constat> constats(String id, JMeterTreeNode racine) {
        List<Constat> l = new ArrayList<>();
        for (Regle r : Regles.toutes()) {
            if (r.id().equals(id)) {
                r.verifier(racine, l);
            }
        }
        for (Constat c : l) {
            assertEquals(id, c.getRegle());
        }
        return l;
    }

    /** ResultCollector lit les propriétés de JMeter dès sa création : on en charge un jeu vide. */
    @BeforeAll
    static void proprietesJmeter() throws Exception {
        java.io.File f = java.io.File.createTempFile("jmeter", ".properties");
        f.deleteOnExit();
        org.apache.jmeter.util.JMeterUtils.loadJMeterProperties(f.getAbsolutePath());
    }

    @BeforeEach
    void arbre() {
        plan = noeud(new TestPlan(), null);
        ThreadGroup tg = new ThreadGroup();
        tg.setName("Utilisateurs");
        boucle = new LoopController();
        boucle.setLoops(1);
        tg.setSamplerController(boucle);
        groupe = noeud(tg, plan);
    }

    @Test
    void quatorzeReglesDansLOrdre() {
        List<Regle> r = Regles.toutes();
        assertEquals(14, r.size());
        for (int i = 0; i < 14; i++) {
            assertEquals(String.format("V%02d", i + 1), r.get(i).id());
        }
    }

    @Test
    void v01Enregistreur() {
        assertTrue(constats("V01", plan).isEmpty());
        JMeterTreeNode e = noeud(new ProxyControl(), plan);
        List<Constat> c = constats("V01", plan);
        assertEquals(1, c.size());
        assertEquals(Gravite.ERREUR, c.get(0).getGravite());
        assertSame(e, c.get(0).getNoeud());
    }

    @Test
    void v02MiseAuPointActiveSeulement() {
        JMeterTreeNode d = noeud(new DebugSampler(), groupe);
        JMeterTreeNode p = noeud(new DebugPostProcessor(), groupe);
        assertEquals(2, constats("V02", plan).size());
        d.getTestElement().setEnabled(false);
        p.getTestElement().setEnabled(false);
        assertTrue(constats("V02", plan).isEmpty());
    }

    @Test
    void v03RecepteursGourmands() {
        ResultCollector arbre = new ResultCollector();
        arbre.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.visualizers.ViewResultsFullVisualizer");
        ResultCollector resume = new ResultCollector();
        resume.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.visualizers.SummaryReport");
        JMeterTreeNode n = noeud(arbre, plan);
        noeud(resume, plan);
        List<Constat> c = constats("V03", plan);
        assertEquals(1, c.size());
        assertSame(n, c.get(0).getNoeud());
        assertEquals(Gravite.AVERTISSEMENT, c.get(0).getGravite());
    }

    private JMeterTreeNode csv(String fichier) {
        CSVDataSet d = new CSVDataSet();
        d.setProperty("filename", fichier);
        return noeud(d, groupe);
    }

    @Test
    void v04CheminsCsv() {
        csv("donnees/utilisateurs.csv");
        csv("${dossier}/donnees.csv");
        assertTrue(constats("V04", plan).isEmpty());
        JMeterTreeNode abs = csv("/home/moi/u.csv");
        csv("C:\\jmeter\\u.csv");
        csv("");
        List<Constat> c = constats("V04", plan);
        assertEquals(3, c.size());
        assertSame(abs, c.get(0).getNoeud());
        assertEquals(Gravite.ERREUR, c.get(0).getGravite());
        assertTrue(c.get(0).getMessage().contains("/home/moi/u.csv"));
        assertTrue(c.get(2).getMessage().contains("non renseigné"));
    }

    @Test
    void v05GroupeSansFin() {
        assertTrue(constats("V05", plan).isEmpty());
        boucle.setLoops(-1);
        List<Constat> c = constats("V05", plan);
        assertEquals(1, c.size());
        assertSame(groupe, c.get(0).getNoeud());
        ((ThreadGroup) groupe.getTestElement()).setScheduler(true);
        assertTrue(constats("V05", plan).isEmpty());
    }

    @Test
    void v06AssertionDansLaPortee() {
        JMeterTreeNode r = noeud(requete("r", "a.fr"), groupe);
        assertEquals(1, constats("V06", plan).size());
        assertSame(r, constats("V06", plan).get(0).getNoeud());
        // Une assertion au niveau du groupe couvre la requête.
        noeud(new ResponseAssertion(), groupe);
        assertTrue(constats("V06", plan).isEmpty());
    }

    @Test
    void v07ServeurEnDurParDomaine() {
        JMeterTreeNode r1 = noeud(requete("r1", "a.fr"), groupe);
        noeud(requete("r2", "a.fr"), groupe);
        noeud(requete("r3", "b.fr"), groupe);
        noeud(requete("r4", "${serveur}"), groupe);
        List<Constat> c = constats("V07", plan);
        assertEquals(2, c.size());
        assertSame(r1, c.get(0).getNoeud());
        assertTrue(c.get(0).getMessage().contains("« a.fr »") && c.get(0).getMessage().contains("2 requête(s)"));
        assertEquals(Gravite.INFO, c.get(0).getGravite());
    }

    @Test
    void v08SansMinuterie() {
        assertTrue(constats("V08", plan).isEmpty(), "groupe sans requête : rien");
        JMeterTreeNode r = noeud(requete("r", "a.fr"), groupe);
        List<Constat> c = constats("V08", plan);
        assertEquals(1, c.size());
        assertSame(groupe, c.get(0).getNoeud());
        noeud(new ConstantTimer(), r);
        assertTrue(constats("V08", plan).isEmpty());
    }

    @Test
    void v09EtV10Transactions() {
        TransactionController tc = new TransactionController();
        JMeterTreeNode t = noeud(tc, groupe);
        assertEquals(1, constats("V10", plan).size());
        assertTrue(constats("V09", plan).isEmpty(), "transaction vide : V10 seulement");
        noeud(requete("r", "a.fr"), t);
        assertTrue(constats("V10", plan).isEmpty());
        List<Constat> c = constats("V09", plan);
        assertEquals(1, c.size());
        assertSame(t, c.get(0).getNoeud());
        tc.setGenerateParentSample(true);
        assertTrue(constats("V09", plan).isEmpty());
    }

    @Test
    void v11CookiesSuperposesUnSeulConstat() {
        noeud(new CookieManager(), groupe);
        assertTrue(constats("V11", plan).isEmpty());
        noeud(new CookieManager(), plan);
        List<Constat> c = constats("V11", plan);
        assertEquals(1, c.size());
        assertEquals(Gravite.AVERTISSEMENT, c.get(0).getGravite());
    }

    @Test
    void v12NomsDUrlParParent() {
        TransactionController tc = new TransactionController();
        JMeterTreeNode t = noeud(tc, groupe);
        noeud(requete("/login-1", "a.fr"), t);
        noeud(requete("https://a.fr/x", "a.fr"), t);
        noeud(requete("Connexion", "a.fr"), t);
        List<Constat> c = constats("V12", plan);
        assertEquals(1, c.size());
        assertSame(t, c.get(0).getNoeud());
        assertTrue(c.get(0).getMessage().startsWith("2 requête(s)"));
    }

    @Test
    void v13PluginsRequis() {
        noeud(requete("r", "a.fr"), groupe);
        assertTrue(constats("V13", plan).isEmpty());
        ResultCollector rc = new ResultCollector();
        rc.setProperty(TestElement.GUI_CLASS, "kg.apc.jmeter.vizualizers.ResponseTimesOverTimeGui");
        noeud(rc, plan);
        List<Constat> c = constats("V13", plan);
        assertEquals(1, c.size());
        assertSame(plan, c.get(0).getNoeud());
        assertTrue(c.get(0).getMessage().contains("kg.apc.jmeter.vizualizers.ResponseTimesOverTimeGui"));
    }

    @Test
    void v14EnTetesVides() {
        HeaderManager plein = new HeaderManager();
        plein.add(new Header("Accept", "*/*"));
        noeud(plein, groupe);
        assertTrue(constats("V14", plan).isEmpty());
        JMeterTreeNode vide = noeud(new HeaderManager(), groupe);
        List<Constat> c = constats("V14", plan);
        assertEquals(1, c.size());
        assertSame(vide, c.get(0).getNoeud());
    }

    @Test
    void verificateurTrieParGraviteEtResiste() {
        noeud(new ProxyControl(), plan);
        noeud(requete("/r", "a.fr"), groupe);
        List<Constat> c = Verificateur.verifier(plan);
        assertEquals(Gravite.ERREUR, c.get(0).getGravite());
        assertEquals(Gravite.INFO, c.get(c.size() - 1).getGravite());
        assertTrue(Verificateur.rapport(c).contains("[V01]"));
    }
}
