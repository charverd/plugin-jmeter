package fr.jmeter.plugins.masse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.testelement.TestPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.jmeter.plugins.masse.MoteurModification.Champ;
import fr.jmeter.plugins.masse.MoteurModification.Cible;
import fr.jmeter.plugins.masse.MoteurModification.StatNom;

/**
 * Arbre d'essai : un plan, trois transactions de deux requêtes, chaque
 * requête avec son gestionnaire d'en-têtes (comme après un enregistrement).
 */
class MoteurModificationTest {

    private JMeterTreeNode tc1;
    private JMeterTreeNode tc2;
    private JMeterTreeNode tc3;

    private static JMeterTreeNode noeud(org.apache.jmeter.testelement.TestElement e, JMeterTreeNode parent) {
        JMeterTreeNode n = new JMeterTreeNode(e, null);
        if (parent != null) {
            parent.add(n);
        }
        return n;
    }

    private static JMeterTreeNode transaction(String nom, JMeterTreeNode plan, String jeton) {
        TransactionController tc = new TransactionController();
        tc.setName(nom);
        JMeterTreeNode n = noeud(tc, plan);
        for (int i = 1; i <= 2; i++) {
            HTTPSamplerProxy s = new HTTPSamplerProxy();
            s.setName(nom + "/req-" + i);
            s.setProtocol("https");
            s.setDomain("app.exemple.fr");
            s.setPath("/api/v1/commande/" + i);
            s.addArgument("client", "C123");
            JMeterTreeNode ns = noeud(s, n);
            HeaderManager hm = new HeaderManager();
            hm.setName("HTTP Header Manager");
            hm.add(new Header("Authorization", "Bearer " + jeton));
            hm.add(new Header("X-Correlation", "abc-" + i));
            noeud(hm, ns);
        }
        return n;
    }

    @BeforeEach
    void arbre() {
        TestPlan tp = new TestPlan();
        tp.setName("Plan");
        JMeterTreeNode plan = noeud(tp, null);
        tc1 = transaction("TC_01", plan, "jeton1");
        tc2 = transaction("TC_02", plan, "jeton2");
        tc3 = transaction("TC_03", plan, "jeton3");
    }

    private static HeaderManager entetes(JMeterTreeNode tc, int requete) {
        return (HeaderManager) ((JMeterTreeNode) tc.getChildAt(requete).getChildAt(0)).getTestElement();
    }

    private static HTTPSamplerProxy requete(JMeterTreeNode tc, int requete) {
        return (HTTPSamplerProxy) ((JMeterTreeNode) tc.getChildAt(requete)).getTestElement();
    }

    @Test
    void perimetreAvecContenuSansDoublon() {
        // TC_01 sélectionnée avec une de ses propres requêtes : comptée une fois.
        JMeterTreeNode[] sel = { tc1, (JMeterTreeNode) tc1.getChildAt(0), tc2 };
        List<JMeterTreeNode> p = MoteurModification.perimetre(sel, true, false);
        assertEquals(10, p.size()); // 2 transactions + 4 requêtes + 4 gestionnaires
        assertEquals(4, MoteurModification.compter(p, HeaderManager.class));
        assertEquals(1, MoteurModification.perimetre(sel, false, false).stream()
                .filter(n -> n == tc1).count());
    }

    @Test
    void lesElementsDesactivesPeuventEtreIgnores() {
        tc2.getTestElement().setEnabled(false);
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1, tc2 }, true, true);
        assertEquals(5, p.size());
    }

    @Test
    void statistiquesDesEntetes() {
        List<StatNom> s = MoteurModification.entetes(
                MoteurModification.perimetre(new JMeterTreeNode[] { tc1, tc2 }, true, false));
        assertEquals(2, s.size());
        assertEquals("Authorization", s.get(0).getNom());
        assertEquals(4, s.get(0).getOccurrences());
        assertEquals(2, s.get(0).getValeurs().size());
    }

    @Test
    void memeVariablePourUnEnteteSurLaSelectionSeulement() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1, tc2 }, true, false);
        List<Modification> m = MoteurModification.definirEntete(p, "authorization", "Bearer ${token}", false);
        assertEquals(4, m.size());
        assertEquals("TC_01 > TC_01/req-1 > HTTP Header Manager", m.get(0).getElement());

        MoteurModification.Bilan b = MoteurModification.appliquer(m);
        assertEquals(4, b.getAppliquees().size());
        assertEquals("Bearer ${token}", entetes(tc1, 0).getFirstHeaderNamed("Authorization").getValue());
        assertEquals("Bearer ${token}", entetes(tc2, 1).getFirstHeaderNamed("Authorization").getValue());
        // TC_03 n'était pas sélectionnée.
        assertEquals("Bearer jeton3", entetes(tc3, 0).getFirstHeaderNamed("Authorization").getValue());
        // Les autres en-têtes ne bougent pas.
        assertEquals("abc-2", entetes(tc1, 1).getFirstHeaderNamed("X-Correlation").getValue());
    }

    @Test
    void ajouterUnEnteteLaOuIlManque() {
        entetes(tc1, 0).removeHeaderNamed("X-Correlation");
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        List<Modification> m = MoteurModification.definirEntete(p, "X-Correlation", "${corr}", true);
        assertEquals(2, m.size());
        assertEquals(Modification.ABSENT, m.get(0).getAvant());
        MoteurModification.appliquer(m);
        assertEquals("${corr}", entetes(tc1, 0).getFirstHeaderNamed("X-Correlation").getValue());
        assertEquals("${corr}", entetes(tc1, 1).getFirstHeaderNamed("X-Correlation").getValue());
    }

    @Test
    void supprimerUnEnteteEtDefaire() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        MoteurModification.Bilan b = MoteurModification.appliquer(
                MoteurModification.supprimerEntete(p, "X-Correlation"));
        assertEquals(2, b.getAppliquees().size());
        assertNull(entetes(tc1, 0).getFirstHeaderNamed("X-Correlation"));
        MoteurModification.defaire(b);
        assertEquals("abc-1", entetes(tc1, 0).getFirstHeaderNamed("X-Correlation").getValue());
    }

    @Test
    void rechercherRemplacerDansLesChampsChoisis() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1, tc2 }, true, false);
        List<Modification> m = MoteurModification.remplacer(p, "app.exemple.fr", "${serveur}", false, false,
                EnumSet.of(Champ.SERVEUR));
        assertEquals(4, m.size());
        MoteurModification.appliquer(m);
        assertEquals("${serveur}", requete(tc1, 0).getDomain());
        assertEquals("app.exemple.fr", requete(tc3, 0).getDomain());
        // Le chemin n'était pas dans les champs choisis.
        assertEquals(0, MoteurModification.remplacer(p, "app.exemple.fr", "x", false, false,
                EnumSet.of(Champ.CHEMIN)).size());
    }

    @Test
    void regexAvecGroupesEtVariableLitterale() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        List<Modification> m = MoteurModification.remplacer(p, "/api/v(\\d+)/commande/(\\d+)",
                "/api/v$1/commande/${id_$2}", true, true, EnumSet.of(Champ.CHEMIN));
        MoteurModification.appliquer(m);
        assertEquals("/api/v1/commande/${id_1}", requete(tc1, 0).getPath());
        assertEquals("/api/v1/commande/${id_2}", requete(tc1, 1).getPath());
    }

    @Test
    void parametresEtNomsDesElements() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        MoteurModification.appliquer(MoteurModification.remplacer(p, "C123", "${client}", false, true,
                EnumSet.of(Champ.PARAMETRES)));
        assertEquals("${client}", requete(tc1, 0).getArguments().getArgument(0).getValue());
        MoteurModification.appliquer(MoteurModification.remplacer(p, "TC_01", "Connexion", false, true,
                EnumSet.of(Champ.NOMS)));
        assertEquals("Connexion", tc1.getTestElement().getName());
        assertEquals("Connexion/req-1", requete(tc1, 0).getName());
    }

    @Test
    void uneModificationPerimeeEstEcartee() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        List<Modification> m = MoteurModification.definirEntete(p, "Authorization", "Bearer ${token}", false);
        // L'utilisateur modifie une valeur entre l'aperçu et l'application.
        entetes(tc1, 1).getFirstHeaderNamed("Authorization").setValue("Bearer autre");
        MoteurModification.Bilan b = MoteurModification.appliquer(m);
        assertEquals(1, b.getAppliquees().size());
        assertEquals(1, b.getPerimees());
        assertEquals("Bearer autre", entetes(tc1, 1).getFirstHeaderNamed("Authorization").getValue());
    }

    @Test
    void decocherUneLigneLaLaisseIntacte() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        List<Modification> m = MoteurModification.definirEntete(p, "Authorization", "Bearer ${token}", false);
        m.get(0).setCochee(false);
        MoteurModification.appliquer(m);
        assertEquals("Bearer jeton1", entetes(tc1, 0).getFirstHeaderNamed("Authorization").getValue());
        assertEquals("Bearer ${token}", entetes(tc1, 1).getFirstHeaderNamed("Authorization").getValue());
    }

    @Test
    void defaireRendLEtatDOrigine() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1, tc2 }, true, false);
        MoteurModification.Bilan b = MoteurModification.appliquer(MoteurModification.remplacer(p,
                "app.exemple.fr", "${serveur}", false, false, EnumSet.allOf(Champ.class)));
        assertTrue(b.getAppliquees().size() >= 4);
        MoteurModification.defaire(b);
        assertEquals("app.exemple.fr", requete(tc1, 0).getDomain());
        assertEquals("app.exemple.fr", requete(tc2, 1).getDomain());
    }

    @Test
    void regexInvalideSignalee() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        assertThrows(java.util.regex.PatternSyntaxException.class, () -> MoteurModification.remplacer(p, "([",
                "x", true, false, EnumSet.allOf(Champ.class)));
    }

    // ------------------------------------------------------------------
    // Paramètres de requête (V2)
    // ------------------------------------------------------------------

    private static String parametre(JMeterTreeNode tc, int req, String nom) {
        return requete(tc, req).getArguments().getArgumentsAsMap().get(nom);
    }

    @Test
    void statistiquesDesParametres() {
        List<StatNom> s = MoteurModification.noms(
                MoteurModification.perimetre(new JMeterTreeNode[] { tc1, tc2 }, true, false), Cible.PARAMETRE);
        assertEquals(1, s.size());
        assertEquals("client", s.get(0).getNom());
        assertEquals(4, s.get(0).getOccurrences());
    }

    @Test
    void memeVariablePourUnParametreSurLaSelectionSeulement() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1, tc2 }, true, false);
        List<Modification> m = MoteurModification.definir(p, Cible.PARAMETRE, "client", "${client}", false);
        assertEquals(4, m.size());
        assertEquals("Paramètre client", m.get(0).getChamp());
        MoteurModification.appliquer(m);
        assertEquals("${client}", parametre(tc1, 0, "client"));
        assertEquals("${client}", parametre(tc2, 1, "client"));
        assertEquals("C123", parametre(tc3, 0, "client"));
    }

    @Test
    void nomDeParametreSensibleALaCasse() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        assertEquals(0, MoteurModification.definir(p, Cible.PARAMETRE, "Client", "${client}", false).size());
    }

    @Test
    void ajouterUnParametreLaOuIlManqueEtDefaire() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        MoteurModification.Bilan b = MoteurModification.appliquer(
                MoteurModification.definir(p, Cible.PARAMETRE, "langue", "${langue}", true));
        assertEquals(2, b.getAppliquees().size());
        assertEquals("${langue}", parametre(tc1, 0, "langue"));
        assertEquals(2, requete(tc1, 0).getArguments().getArgumentCount());
        MoteurModification.defaire(b);
        assertNull(parametre(tc1, 0, "langue"));
        assertEquals(1, requete(tc1, 0).getArguments().getArgumentCount());
    }

    @Test
    void supprimerUnParametre() {
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc2 }, true, false);
        MoteurModification.appliquer(MoteurModification.supprimer(p, Cible.PARAMETRE, "client"));
        assertNull(parametre(tc2, 0, "client"));
        assertEquals("C123", parametre(tc1, 0, "client"));
    }

    @Test
    void lesRequetesACorpsBrutSontIgnorees() {
        HTTPSamplerProxy s = requete(tc1, 0);
        s.getArguments().clear();
        s.setPostBodyRaw(true);
        s.addNonEncodedArgument("", "{\"client\":\"C123\"}", "");
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        List<Modification> m = MoteurModification.definir(p, Cible.PARAMETRE, "client", "${client}", true);
        // Seule la seconde requête (paramètres classiques) est concernée, même avec « ajouter ».
        assertEquals(1, m.size());
        assertTrue(m.get(0).getElement().endsWith("req-2"));
    }

    @Test
    void lEnteteEtLeParametreDeMemeNomNeSeConfondentPas() {
        entetes(tc1, 0).add(new Header("client", "entete"));
        List<JMeterTreeNode> p = MoteurModification.perimetre(new JMeterTreeNode[] { tc1 }, true, false);
        MoteurModification.appliquer(MoteurModification.definir(p, Cible.PARAMETRE, "client", "${client}", false));
        assertEquals("entete", entetes(tc1, 0).getFirstHeaderNamed("client").getValue());
        assertEquals("${client}", parametre(tc1, 0, "client"));
    }
}
