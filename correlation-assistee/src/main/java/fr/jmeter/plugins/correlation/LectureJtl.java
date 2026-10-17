package fr.jmeter.plugins.correlation;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Lit les réponses d'un fichier de résultats JMeter au format XML
 * (« Save As XML », avec « Save Response Data », « Save URL » et
 * idéalement « Save Response Headers »). Sous-requêtes comprises, dans
 * l'ordre du fichier, la requête parente avant ses sous-requêtes.
 */
public final class LectureJtl {

    /** Une réponse enregistrée. */
    public static final class Reponse {
        private final String libelle;
        private final String methode;
        private final String url;
        private final String entetes;
        private final String corps;

        Reponse(String libelle, String methode, String url, String entetes, String corps) {
            this.libelle = libelle;
            this.methode = methode;
            this.url = url;
            this.entetes = entetes;
            this.corps = corps;
        }

        public String getLibelle() {
            return libelle;
        }

        public String getMethode() {
            return methode;
        }

        public String getUrl() {
            return url;
        }

        public String getEntetes() {
            return entetes;
        }

        public String getCorps() {
            return corps;
        }

        /** « GET /chemin », sans protocole, hôte ni query string. */
        public String cle() {
            return LectureJtl.cle(methode, cheminDeUrl(url));
        }
    }

    private LectureJtl() {
    }

    public static List<Reponse> lire(File fichier) throws Exception {
        SAXParserFactory f = SAXParserFactory.newInstance();
        // Un JTL de JMeter n'a pas de DOCTYPE : on les refuse, avec toute entité ou DTD externe
        // (aucun accès réseau ni fichier depuis le contenu lu).
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setXIncludeAware(false);
        SAXParser p = f.newSAXParser();
        List<Reponse> l = new ArrayList<>();
        p.parse(fichier, new DefaultHandler() {
            private final Deque<String[]> pile = new ArrayDeque<>(); // libelle, methode, url, entetes, corps
            private final Deque<Integer> rangs = new ArrayDeque<>();
            private StringBuilder texte;

            @Override
            public void startElement(String uri, String local, String balise, Attributes att) {
                if ("httpSample".equals(balise) || "sample".equals(balise)) {
                    pile.push(new String[] { att.getValue("lb"), "", "", "", "" });
                    // Place réservée : la parente avant ses sous-requêtes.
                    rangs.push(l.size());
                    l.add(null);
                } else if (!pile.isEmpty()) {
                    texte = new StringBuilder();
                }
            }

            @Override
            public void characters(char[] ch, int debut, int longueur) {
                if (texte != null) {
                    texte.append(ch, debut, longueur);
                }
            }

            @Override
            public void endElement(String uri, String local, String balise) {
                if ("httpSample".equals(balise) || "sample".equals(balise)) {
                    String[] s = pile.pop();
                    l.set(rangs.pop(), new Reponse(s[0], s[1], s[2], s[3], s[4]));
                    return;
                }
                if (pile.isEmpty() || texte == null) {
                    return;
                }
                String[] s = pile.peek();
                switch (balise) {
                case "method":
                    s[1] = texte.toString().trim();
                    break;
                case "java.net.URL":
                    s[2] = texte.toString().trim();
                    break;
                case "responseHeader":
                    s[3] = texte.toString();
                    break;
                case "responseData":
                    s[4] = texte.toString();
                    break;
                default:
                    break;
                }
                texte = null;
            }
        });
        l.removeIf(r -> r == null);
        return l;
    }

    static String cheminDeUrl(String url) {
        if (url == null) {
            return "";
        }
        String u = url;
        int schema = u.indexOf("://");
        if (schema >= 0) {
            int slash = u.indexOf('/', schema + 3);
            u = slash < 0 ? "/" : u.substring(slash);
        }
        int q = u.indexOf('?');
        return q < 0 ? u : u.substring(0, q);
    }

    static String cle(String methode, String chemin) {
        String c = chemin == null ? "" : chemin;
        int q = c.indexOf('?');
        if (q >= 0) {
            c = c.substring(0, q);
        }
        return (methode == null ? "" : methode.toUpperCase(java.util.Locale.ROOT)) + " " + c;
    }
}
