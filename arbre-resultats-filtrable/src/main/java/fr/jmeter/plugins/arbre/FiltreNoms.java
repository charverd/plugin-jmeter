package fr.jmeter.plugins.arbre;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.jmeter.samplers.SampleResult;

/**
 * Décide quels résultats de premier niveau l'arbre affiche, d'après leur nom
 * (le libellé de l'échantillonneur ou du contrôleur de transaction).
 *
 * <p>En mode inclusion, un résultat est gardé si son nom correspond, ou si le
 * nom de l'un de ses sous-résultats correspond : filtrer sur une requête garde
 * donc la transaction qui la contient. En mode exclusion, un résultat est
 * masqué si son propre nom correspond.
 *
 * <p>Immuable : l'interface en reconstruit un à chaque changement de réglage.
 */
public final class FiltreNoms {

    /**
     * Début du message de réponse que JMeter pose sur les résultats produits
     * par un Transaction Controller (constante privée de
     * {@code org.apache.jmeter.control.TransactionController}).
     */
    static final String PREFIXE_TRANSACTION = "Number of samples in transaction : ";

    private static final FiltreNoms AUCUN = new FiltreNoms("", false, false, false, false);

    private final String motif;
    private final boolean regex;
    private final boolean sensibleCasse;
    private final boolean exclure;
    private final boolean transactionsSeules;
    private final Pattern pattern;
    private final String erreur;

    public FiltreNoms(String motif, boolean regex, boolean sensibleCasse,
            boolean exclure, boolean transactionsSeules) {
        this.motif = motif == null ? "" : motif;
        this.regex = regex;
        this.sensibleCasse = sensibleCasse;
        this.exclure = exclure;
        this.transactionsSeules = transactionsSeules;

        Pattern p = null;
        String e = null;
        if (regex && !this.motif.isEmpty()) {
            try {
                p = Pattern.compile(this.motif,
                        sensibleCasse ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (PatternSyntaxException ex) {
                e = ex.getDescription();
            }
        }
        this.pattern = p;
        this.erreur = e;
    }

    public static FiltreNoms aucun() {
        return AUCUN;
    }

    /** Un motif vide ou une expression invalide laisse tout passer. */
    public boolean estActif() {
        return !motif.isEmpty() && erreur == null;
    }

    /** Description de l'erreur de syntaxe de l'expression régulière, ou {@code null}. */
    public String getErreur() {
        return erreur;
    }

    public String getMotif() {
        return motif;
    }

    public boolean isRegex() {
        return regex;
    }

    public boolean isSensibleCasse() {
        return sensibleCasse;
    }

    public boolean isExclure() {
        return exclure;
    }

    public boolean isTransactionsSeules() {
        return transactionsSeules;
    }

    /** Le résultat de premier niveau doit-il apparaître dans l'arbre ? */
    public boolean garder(SampleResult resultat) {
        if (!estActif()) {
            return true;
        }
        if (exclure) {
            return !(estEligible(resultat) && nomCorrespond(resultat.getSampleLabel()));
        }
        return brancheCorrespond(resultat);
    }

    /** Vrai si ce résultat ou l'un de ses descendants porte un nom qui correspond. */
    public boolean brancheCorrespond(SampleResult resultat) {
        if (estEligible(resultat) && nomCorrespond(resultat.getSampleLabel())) {
            return true;
        }
        for (SampleResult sous : resultat.getSubResults()) {
            if (brancheCorrespond(sous)) {
                return true;
            }
        }
        return false;
    }

    public boolean nomCorrespond(String nom) {
        if (!estActif()) {
            return true;
        }
        if (nom == null) {
            return false;
        }
        if (pattern != null) {
            return pattern.matcher(nom).find();
        }
        if (sensibleCasse) {
            return nom.contains(motif);
        }
        return nom.toLowerCase(Locale.ROOT).contains(motif.toLowerCase(Locale.ROOT));
    }

    private boolean estEligible(SampleResult resultat) {
        return !transactionsSeules || estTransaction(resultat);
    }

    public static boolean estTransaction(SampleResult resultat) {
        String message = resultat.getResponseMessage();
        return message != null && message.startsWith(PREFIXE_TRANSACTION);
    }

    /**
     * Ajoute à {@code noms} le nom de ce résultat et de tous ses descendants
     * (seulement les transactions si {@code transactionsSeules}).
     */
    public static void collecterNoms(SampleResult resultat, boolean transactionsSeules,
            Collection<String> noms) {
        if (!transactionsSeules || estTransaction(resultat)) {
            String nom = resultat.getSampleLabel();
            if (nom != null && !nom.isEmpty()) {
                noms.add(nom);
            }
        }
        for (SampleResult sous : resultat.getSubResults()) {
            collecterNoms(sous, transactionsSeules, noms);
        }
    }

    /** Expression régulière qui correspond exactement à l'un des noms donnés. */
    public static String regexPourNoms(Collection<String> noms) {
        StringBuilder sb = new StringBuilder("^(?:");
        boolean premier = true;
        for (String nom : noms) {
            if (!premier) {
                sb.append('|');
            }
            sb.append(Pattern.quote(nom));
            premier = false;
        }
        return sb.append(")$").toString();
    }
}
