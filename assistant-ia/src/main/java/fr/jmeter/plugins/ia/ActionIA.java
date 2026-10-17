package fr.jmeter.plugins.ia;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Une modification proposée par le modèle, lue dans son bloc « jmeter-actions ». */
public final class ActionIA {

    public enum Type {
        MODIFIER, RENOMMER, ACTIVER, DESACTIVER, SUPPRIMER, AJOUTER, REMPLACER, DEPLACER
    }

    /** Lecture tolérante : les modèles laissent souvent des virgules finales ou des retours à la ligne bruts. */
    private static final ObjectMapper TOLERANT = new ObjectMapper(JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA, JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS,
                    JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .build());

    private static final Pattern BLOC = Pattern.compile("```\\s*" + PromptSysteme.BALISE + "\\s*\\n(.*?)```",
            Pattern.DOTALL);
    private static final Pattern BLOC_JSON = Pattern.compile("```\\s*json\\s*\\n(.*?)```", Pattern.DOTALL);

    Type type;
    String cible;
    String parent;
    Integer position;
    String nom;
    String xml;
    final Map<String, String> proprietes = new LinkedHashMap<>();
    /** Problème qui empêche d'appliquer l'action, ou null. */
    String erreur;
    /** Doute qui n'empêche pas d'appliquer (propriété inconnue de JMeter…), ou null. */
    String avertissement;
    boolean cochee = true;

    public Type getType() {
        return type;
    }

    public String getCible() {
        return cible;
    }

    public String getParent() {
        return parent;
    }

    public String getXml() {
        return xml;
    }

    public String getNom() {
        return nom;
    }

    public Map<String, String> getProprietes() {
        return proprietes;
    }

    public String getErreur() {
        return erreur;
    }

    public String getAvertissement() {
        return avertissement;
    }

    public boolean isCochee() {
        return cochee && erreur == null;
    }

    public void setCochee(boolean c) {
        cochee = c;
    }

    /** Résultat de la lecture d'une réponse. */
    public static final class Lecture {
        public final List<ActionIA> actions = new ArrayList<>();
        /** Erreur de syntaxe du bloc, ou null. */
        public String erreur;
        public boolean blocTrouve;
    }

    /**
     * Cherche les blocs « jmeter-actions » de la réponse (à défaut, un bloc
     * « json » fait d'objets portant un champ « action ») et les lit.
     */
    public static Lecture lire(String reponse) {
        Lecture l = new Lecture();
        List<String> blocs = new ArrayList<>();
        Matcher m = BLOC.matcher(reponse == null ? "" : reponse);
        while (m.find()) {
            blocs.add(m.group(1));
        }
        if (blocs.isEmpty()) {
            Matcher j = BLOC_JSON.matcher(reponse == null ? "" : reponse);
            while (j.find()) {
                if (j.group(1).contains("\"action\"")) {
                    blocs.add(j.group(1));
                }
            }
        }
        for (String b : blocs) {
            l.blocTrouve = true;
            try {
                JsonNode n = TOLERANT.readTree(b);
                if (n.isObject()) {
                    l.actions.add(depuis(n));
                } else {
                    for (JsonNode a : n) {
                        l.actions.add(depuis(a));
                    }
                }
            } catch (Exception e) {
                l.erreur = "bloc d'actions illisible : " + e.getMessage().split("\n")[0];
            }
        }
        return l;
    }

    static ActionIA depuis(JsonNode n) {
        ActionIA a = new ActionIA();
        String t = n.path("action").asText("").trim().toUpperCase(Locale.ROOT)
                .replace("É", "E").replace("È", "E").replace("Ê", "E");
        try {
            a.type = Type.valueOf(t);
        } catch (IllegalArgumentException e) {
            a.erreur = "action inconnue « " + n.path("action").asText("") + " »";
        }
        a.cible = texte(n, "cible");
        a.parent = texte(n, "parent");
        a.nom = texte(n, "nom");
        a.xml = texte(n, "xml");
        if (n.path("position").canConvertToInt()) {
            a.position = n.path("position").asInt();
        }
        Iterator<Map.Entry<String, JsonNode>> it = n.path("proprietes").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            a.proprietes.put(e.getKey(), e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString());
        }
        if (a.erreur == null) {
            a.erreur = manque(a);
        }
        return a;
    }

    private static String texte(JsonNode n, String champ) {
        JsonNode v = n.get(champ);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String manque(ActionIA a) {
        switch (a.type) {
        case MODIFIER:
            return a.cible == null ? "cible manquante" : a.proprietes.isEmpty() ? "aucune propriété à modifier" : null;
        case RENOMMER:
            return a.cible == null ? "cible manquante" : a.nom == null || a.nom.isEmpty() ? "nom manquant" : null;
        case AJOUTER:
            return a.parent == null ? "parent manquant" : a.xml == null || a.xml.trim().isEmpty() ? "xml manquant" : null;
        case REMPLACER:
            return a.cible == null ? "cible manquante" : a.xml == null || a.xml.trim().isEmpty() ? "xml manquant" : null;
        case DEPLACER:
            return a.cible == null ? "cible manquante" : a.parent == null ? "parent manquant" : null;
        default:
            return a.cible == null ? "cible manquante" : null;
        }
    }

    /** Une ligne lisible pour l'aperçu. */
    public String resume() {
        if (type == null) {
            return "?";
        }
        switch (type) {
        case MODIFIER:
            List<String> p = new ArrayList<>();
            for (Map.Entry<String, String> e : proprietes.entrySet()) {
                p.add(e.getKey() + " = " + e.getValue());
            }
            return String.join(" ; ", p);
        case RENOMMER:
            return "→ « " + nom + " »";
        case AJOUTER:
        case REMPLACER:
            return xml == null ? "" : xml.replaceAll("\\s+", " ").trim();
        case DEPLACER:
            return "vers [" + parent + "]" + (position == null ? "" : " position " + position);
        default:
            return "";
        }
    }
}
