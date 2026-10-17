package fr.jmeter.plugins.sauvegarde;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;

/**
 * Écrit une page HTML autonome qui reproduit l'arbre de résultats : arbre à gauche, onglets
 * « Résultat de l'échantillon », « Requête » et « Données de réponse » à droite.
 *
 * <p>Les données partent dans un bloc JSON de la page, écrit au fil de l'eau (pas de chaîne
 * géante en mémoire). Les réponses sont affichées comme du texte, jamais interprétées : aucun
 * script d'une page enregistrée ne s'exécute à l'ouverture.
 */
public final class ExportHtml {

    /** Début du message de réponse d'un Transaction Controller (constante privée de JMeter). */
    static final String PREFIXE_TRANSACTION = "Number of samples in transaction : ";

    static final String MARQUE_DONNEES = "__DONNEES__";
    static final String MARQUE_TITRE = "__TITRE__";

    /** Libellés de JMeter repris dans la page, pour qu'elle parle la langue de l'interface. */
    static final String[] CLES_LIBELLES = { "view_results_tree_title", "view_results_tab_sampler",
            "view_results_tab_request", "view_results_tab_response", "view_results_tab_assertion",
            "view_results_request_body", "view_results_request_headers", "view_results_response_body",
            "view_results_response_headers", "view_results_table_request_raw_nodata" };

    /** Informations de l'en-tête de la page. */
    public static final class Entete {
        final String plan;
        final String fichierPlan;
        final Date date;
        final String origine;

        /**
         * @param plan nom du plan de test
         * @param fichierPlan chemin du .jmx, ou {@code null} s'il n'est pas enregistré
         * @param date moment de la sauvegarde
         * @param origine ce qui a déclenché la sauvegarde (« avant Nettoyer tout »…)
         */
        public Entete(String plan, String fichierPlan, Date date, String origine) {
            this.plan = plan;
            this.fichierPlan = fichierPlan;
            this.date = date;
            this.origine = origine;
        }
    }

    private final Function<String, String> libelle;
    private final int maxCaracteres;
    private final DateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.ROOT);

    /**
     * @param libelle traduction d'une clé de libellé JMeter ({@code JMeterUtils::getResString})
     * @param maxCaracteres taille maximale gardée par réponse (caractères, ou octets d'une image) ;
     *            0 ou moins : pas de limite
     */
    public ExportHtml(Function<String, String> libelle, int maxCaracteres) {
        this.libelle = libelle;
        this.maxCaracteres = maxCaracteres;
    }

    public void ecrire(Writer sortie, Entete entete, List<ArbreExporte> arbres) throws IOException {
        String modele = modele();
        int iTitre = modele.indexOf(MARQUE_TITRE);
        int iDonnees = modele.indexOf(MARQUE_DONNEES);
        if (iTitre < 0 || iDonnees < iTitre) {
            throw new IllegalStateException("modèle HTML invalide");
        }
        sortie.write(modele, 0, iTitre);
        sortie.write(echapperHtml(titre(entete)));
        sortie.write(modele, iTitre + MARQUE_TITRE.length(), iDonnees - iTitre - MARQUE_TITRE.length());
        ecrireDonnees(sortie, entete, arbres);
        sortie.write(modele, iDonnees + MARQUE_DONNEES.length(), modele.length() - iDonnees - MARQUE_DONNEES.length());
        sortie.flush();
    }

    private String titre(Entete entete) {
        return libelle.apply("view_results_tree_title") + " — " + entete.plan + " — " + formatDate.format(entete.date);
    }

    private void ecrireDonnees(Writer sortie, Entete entete, List<ArbreExporte> arbres) throws IOException {
        JsonFactory fabrique = new JsonFactory();
        fabrique.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        fabrique.setCharacterEscapes(new EchappementsScript());
        try (JsonGenerator g = fabrique.createGenerator(sortie)) {
            g.writeStartObject();
            g.writeStringField("plan", entete.plan);
            g.writeStringField("fichierPlan", entete.fichierPlan);
            g.writeStringField("date", formatDate.format(entete.date));
            g.writeStringField("origine", entete.origine);
            g.writeObjectFieldStart("libelles");
            for (String cle : CLES_LIBELLES) {
                g.writeStringField(cle, libelle.apply(cle));
            }
            g.writeEndObject();
            g.writeArrayFieldStart("arbres");
            for (ArbreExporte a : arbres) {
                g.writeStartObject();
                g.writeStringField("nom", a.nom);
                g.writeStringField("chemin", a.chemin);
                g.writeBooleanField("filtrable", a.filtrable);
                if (a.filtre != null) {
                    g.writeObjectFieldStart("filtre");
                    g.writeStringField("motif", a.filtre.motif);
                    g.writeBooleanField("regex", a.filtre.regex);
                    g.writeBooleanField("casse", a.filtre.sensibleCasse);
                    g.writeBooleanField("exclure", a.filtre.exclure);
                    g.writeBooleanField("transactions", a.filtre.transactionsSeules);
                    g.writeEndObject();
                }
                g.writeArrayFieldStart("resultats");
                for (SampleResult r : a.resultats) {
                    ecrireResultat(g, r);
                }
                g.writeEndArray();
                g.writeEndObject();
            }
            g.writeEndArray();
            g.writeEndObject();
        }
    }

    private void ecrireResultat(JsonGenerator g, SampleResult r) throws IOException {
        g.writeStartObject();
        g.writeStringField("l", r.getSampleLabel());
        g.writeBooleanField("ok", r.isSuccessful());
        if (estTransaction(r)) {
            g.writeBooleanField("tx", true);
        }
        g.writeNumberField("t", r.getTime());
        g.writeStringField("code", r.getResponseCode());
        g.writeStringField("stats", statistiques(r));
        String donnees = r.getSamplerData();
        g.writeStringField("req", donnees == null || donnees.isEmpty()
                ? libelle.apply("view_results_table_request_raw_nodata") : donnees);
        g.writeStringField("reqh", r.getRequestHeaders());
        g.writeStringField("resh", r.getResponseHeaders());
        ecrireCorps(g, r);

        SampleResult[] sous = r.getSubResults();
        AssertionResult[] assertions = r.getAssertionResults();
        g.writeArrayFieldStart("enf");
        for (SampleResult s : sous) {
            ecrireResultat(g, s);
        }
        // Comme dans JMeter : seules les assertions en échec ou en erreur deviennent des nœuds.
        for (AssertionResult a : assertions) {
            if (a.isFailure() || a.isError()) {
                g.writeStartObject();
                g.writeBooleanField("a", true);
                g.writeStringField("l", a.getName());
                g.writeBooleanField("ok", false);
                g.writeStringField("stats", assertion(a));
                g.writeEndObject();
            }
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private void ecrireCorps(JsonGenerator g, SampleResult r) throws IOException {
        byte[] octets = r.getResponseData();
        int n = octets == null ? 0 : octets.length;
        String type = r.getContentType() == null ? "" : r.getContentType();
        boolean image = type.toLowerCase(Locale.ROOT).startsWith("image/");
        if (image || SampleResult.BINARY.equals(r.getDataType())) {
            if (image && n > 0 && (maxCaracteres <= 0 || n <= maxCaracteres)) {
                g.writeStringField("img", Base64.getEncoder().encodeToString(octets));
                g.writeStringField("mime", type.split(";")[0].trim());
            } else {
                g.writeStringField("corps", "");
                g.writeStringField("note", "Données binaires non reproduites : " + n + " octets"
                        + (type.isEmpty() ? "" : " (" + type + ")") + ".");
            }
            return;
        }
        String texte = r.getResponseDataAsString();
        if (maxCaracteres > 0 && texte.length() > maxCaracteres) {
            g.writeStringField("corps", texte.substring(0, maxCaracteres));
            g.writeStringField("note", "Réponse tronquée : " + maxCaracteres + " caractères gardés sur "
                    + texte.length() + ".");
        } else {
            g.writeStringField("corps", texte);
        }
    }

    /** Même texte que l'onglet « Résultat de l'échantillon » de JMeter 5.4.3 (SamplerResultTab). */
    String statistiques(SampleResult r) {
        String classe = r.getClass().getName();
        StringBuilder sb = new StringBuilder(400);
        ligne(sb, "view_results_thread_name", r.getThreadName());
        ligne(sb, "view_results_sample_start", formatDate.format(new Date(r.getStartTime())));
        ligne(sb, "view_results_load_time", r.getTime());
        ligne(sb, "view_results_connect_time", r.getConnectTime());
        ligne(sb, "view_results_latency", r.getLatency());
        ligne(sb, "view_results_size_in_bytes", r.getBytesAsLong());
        ligne(sb, "view_results_sent_bytes", r.getSentBytes());
        ligne(sb, "view_results_size_headers_in_bytes", r.getHeadersSize());
        ligne(sb, "view_results_size_body_in_bytes", r.getBodySizeAsLong());
        ligne(sb, "view_results_sample_count", r.getSampleCount());
        ligne(sb, "view_results_error_count", r.getErrorCount());
        ligne(sb, "view_results_datatype", r.getDataType());
        ligne(sb, "view_results_response_code", r.getResponseCode());
        ligne(sb, "view_results_response_message", r.getResponseMessage());
        sb.append('\n').append('\n');
        sb.append(classe.substring(classe.lastIndexOf('.') + 1)).append(' ')
                .append(libelle.apply("view_results_fields")).append('\n');
        sb.append("ContentType: ").append(r.getContentType()).append('\n');
        sb.append("DataEncoding: ").append(r.getDataEncodingNoDefault()).append('\n');
        return sb.toString();
    }

    String assertion(AssertionResult a) {
        StringBuilder sb = new StringBuilder(200);
        ligne(sb, "view_results_assertion_error", a.isError());
        ligne(sb, "view_results_assertion_failure", a.isFailure());
        ligne(sb, "view_results_assertion_failure_message", a.getFailureMessage());
        return sb.toString();
    }

    private void ligne(StringBuilder sb, String cle, Object valeur) {
        sb.append(libelle.apply(cle)).append(valeur).append('\n');
    }

    static boolean estTransaction(SampleResult r) {
        String m = r.getResponseMessage();
        return m != null && m.startsWith(PREFIXE_TRANSACTION);
    }

    static String echapperHtml(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '&':
                sb.append("&amp;");
                break;
            case '"':
                sb.append("&quot;");
                break;
            default:
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String modele() throws IOException {
        try (InputStream in = ExportHtml.class.getResourceAsStream("modele.html")) {
            if (in == null) {
                throw new IOException("modèle HTML absent du jar");
            }
            java.io.ByteArrayOutputStream tampon = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int lus;
            while ((lus = in.read(b)) > 0) {
                tampon.write(b, 0, lus);
            }
            return new String(tampon.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Le JSON est placé dans un élément {@code <script>} : « </script> » ou « <!-- » dans une
     * réponse fermeraient le bloc. On échappe donc {@code < > &} en {@code \\u003c}…, ainsi que
     * les séparateurs de ligne Unicode.
     */
    static final class EchappementsScript extends CharacterEscapes {
        private static final long serialVersionUID = 1L;
        private final int[] codes;

        EchappementsScript() {
            codes = standardAsciiEscapesForJSON();
            codes['<'] = ESCAPE_STANDARD;
            codes['>'] = ESCAPE_STANDARD;
            codes['&'] = ESCAPE_STANDARD;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return codes;
        }

        @Override
        public SerializableString getEscapeSequence(int ch) {
            if (ch == 0x2028 || ch == 0x2029) {
                return new com.fasterxml.jackson.core.io.SerializedString(String.format("\\u%04x", ch));
            }
            return null;
        }
    }
}
