package fr.jmeter.plugins.transactionsi;

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.samplers.SampleResult;

/**
 * Ce qu'une transaction en échec écrit en plus dans le fichier de résultats : le code de
 * réponse du premier enfant en échec et, dans la colonne {@code failureMessage}, son libellé,
 * son code, son message et le message de sa première assertion en échec.
 */
final class DetailEchec {

    static final String PREFIXE = "Premier échec : ";

    static final int LONGUEUR_MAX = 500;

    private DetailEchec() {
    }

    /**
     * Premier sous-résultat en échec d'une transaction en mode parent. On descend dans les
     * transactions imbriquées, pas dans les sous-résultats d'une requête (redirections).
     */
    static SampleResult premierEchec(SampleResult transaction) {
        for (SampleResult sous : transaction.getSubResults()) {
            if (sous.isSuccessful()) {
                continue;
            }
            if (TransactionController.isFromTransactionController(sous) && sous.getSubResults().length > 0) {
                SampleResult dedans = premierEchec(sous);
                return dedans != null ? dedans : sous;
            }
            return sous;
        }
        return null;
    }

    static String message(SampleResult echec) {
        StringBuilder sb = new StringBuilder(PREFIXE).append(echec.getSampleLabel()).append(" (")
                .append(echec.getResponseCode());
        String message = echec.getResponseMessage();
        if (message != null && !message.isEmpty()) {
            sb.append(' ').append(message);
        }
        sb.append(')');
        String assertion = premiereAssertionEnEchec(echec);
        if (assertion != null) {
            sb.append(" ; ").append(assertion);
        }
        String texte = sb.toString().replaceAll("\\s*[\\r\\n]+\\s*", " ");
        return texte.length() <= LONGUEUR_MAX ? texte : texte.substring(0, LONGUEUR_MAX - 1) + "…";
    }

    /** Complète la ligne de la transaction en échec ; ne fait rien si elle a réussi. */
    static void completer(SampleResult transaction, SampleResult echec) {
        if (transaction == null || transaction.isSuccessful() || echec == null) {
            return;
        }
        transaction.setResponseCode(echec.getResponseCode());
        AssertionResult detail = new AssertionResult("Contrôleur de transaction Si");
        detail.setFailure(true);
        detail.setFailureMessage(message(echec));
        transaction.addAssertionResult(detail);
    }

    private static String premiereAssertionEnEchec(SampleResult echec) {
        for (AssertionResult a : echec.getAssertionResults()) {
            if ((a.isFailure() || a.isError()) && a.getFailureMessage() != null && !a.getFailureMessage().isEmpty()) {
                return a.getFailureMessage();
            }
        }
        return null;
    }
}
