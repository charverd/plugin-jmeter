package fr.jmeter.plugins.correlation;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jmeter.config.Argument;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;

import fr.jmeter.plugins.correlation.Requete.Champ;
import fr.jmeter.plugins.correlation.Requete.TypeChamp;

/** Passage entre l'arbre JMeter et les objets de la corrélation. */
final class LiaisonArbre {

    private LiaisonArbre() {
    }

    /** Requêtes HTTP actives sous {@code conteneur}, dans l'ordre de l'arbre. */
    static List<JMeterTreeNode> requetes(JMeterTreeNode conteneur) {
        List<JMeterTreeNode> l = new ArrayList<>();
        collecter(conteneur, l);
        return l;
    }

    private static void collecter(JMeterTreeNode n, List<JMeterTreeNode> l) {
        if (!n.isEnabled()) {
            return;
        }
        if (n.getTestElement() instanceof HTTPSamplerBase) {
            l.add(n);
        }
        Enumeration<?> e = n.children();
        while (e.hasMoreElements()) {
            collecter((JMeterTreeNode) e.nextElement(), l);
        }
    }

    static List<HeaderManager> entetes(JMeterTreeNode requete) {
        List<HeaderManager> l = new ArrayList<>();
        Enumeration<?> e = requete.children();
        while (e.hasMoreElements()) {
            JMeterTreeNode c = (JMeterTreeNode) e.nextElement();
            if (c.isEnabled() && c.getTestElement() instanceof HeaderManager) {
                l.add((HeaderManager) c.getTestElement());
            }
        }
        return l;
    }

    static Requete versRequete(JMeterTreeNode n) {
        HTTPSamplerBase s = (HTTPSamplerBase) n.getTestElement();
        List<Champ> champs = new ArrayList<>(Requete.segmentsDuChemin(s.getPath()));
        Arguments args = s.getArguments();
        for (int i = 0; i < args.getArgumentCount(); i++) {
            Argument a = args.getArgument(i);
            if (s.getPostBodyRaw()) {
                champs.add(new Champ(TypeChamp.CORPS, "corps", a.getValue()));
            } else {
                champs.add(new Champ(TypeChamp.PARAMETRE, a.getName(), a.getValue()));
            }
        }
        for (HeaderManager hm : entetes(n)) {
            for (int i = 0; i < hm.size(); i++) {
                Header h = hm.get(i);
                champs.add(new Champ(TypeChamp.ENTETE, h.getName(), h.getValue()));
            }
        }
        return new Requete(n.getName(), s.getMethod(), s.getPath(), champs);
    }

    static List<Requete> versRequetes(List<JMeterTreeNode> noeuds) {
        List<Requete> l = new ArrayList<>();
        for (JMeterTreeNode n : noeuds) {
            l.add(versRequete(n));
        }
        return l;
    }

    /**
     * Pour chaque requête de A, sa réponse dans le fichier JTL : la k-ième
     * requête de chemin « /x » de A va avec la k-ième réponse de chemin « /x »
     * du fichier. Le chemin seul, et non la méthode : JMeter n'écrit la
     * méthode que si les données de requête sont sauvées. Les deux listes
     * suivent l'ordre de l'enregistrement (GET puis POST sur /login, par
     * exemple), même si A a été nettoyé depuis. {@code null} quand il n'y
     * en a pas.
     */
    static List<LectureJtl.Reponse> apparier(List<JMeterTreeNode> requetes, List<LectureJtl.Reponse> reponses) {
        Map<String, List<LectureJtl.Reponse>> parCle = new HashMap<>();
        for (LectureJtl.Reponse r : reponses) {
            parCle.computeIfAbsent(LectureJtl.cle("", LectureJtl.cheminDeUrl(r.getUrl())), k -> new ArrayList<>()).add(r);
        }
        Map<String, Integer> vus = new HashMap<>();
        List<LectureJtl.Reponse> l = new ArrayList<>();
        for (JMeterTreeNode n : requetes) {
            HTTPSamplerBase s = (HTTPSamplerBase) n.getTestElement();
            String cle = LectureJtl.cle("", s.getPath());
            int k = vus.merge(cle, 1, Integer::sum) - 1;
            List<LectureJtl.Reponse> candidates = parCle.get(cle);
            l.add(candidates != null && k < candidates.size() ? candidates.get(k) : null);
        }
        return l;
    }
}
