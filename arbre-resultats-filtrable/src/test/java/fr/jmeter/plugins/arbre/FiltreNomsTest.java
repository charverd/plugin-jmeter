package fr.jmeter.plugins.arbre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.jmeter.samplers.SampleResult;
import org.junit.jupiter.api.Test;

class FiltreNomsTest {

    private static SampleResult requete(String nom) {
        SampleResult r = new SampleResult();
        r.setSampleLabel(nom);
        r.setResponseMessage("OK");
        return r;
    }

    private static SampleResult transaction(String nom, SampleResult... enfants) {
        SampleResult t = new SampleResult();
        t.setSampleLabel(nom);
        t.setResponseMessage(FiltreNoms.PREFIXE_TRANSACTION + enfants.length
                + ", number of failing samples : 0");
        for (SampleResult e : enfants) {
            // Comme TransactionSampler : sans renommer les enfants en « parent-N ».
            t.addSubResult(e, false);
        }
        return t;
    }

    @Test
    void sansMotifToutPasse() {
        assertTrue(FiltreNoms.aucun().garder(requete("n'importe")));
        assertTrue(new FiltreNoms("", true, false, true, true).garder(requete("x")));
    }

    @Test
    void contientSansCasseParDefaut() {
        FiltreNoms f = new FiltreNoms("login", false, false, false, false);
        assertTrue(f.garder(requete("01 - LOGIN page")));
        assertFalse(f.garder(requete("02 - Accueil")));
    }

    @Test
    void contientAvecCasse() {
        FiltreNoms f = new FiltreNoms("Login", false, true, false, false);
        assertTrue(f.garder(requete("Login")));
        assertFalse(f.garder(requete("login")));
    }

    @Test
    void regex() {
        FiltreNoms f = new FiltreNoms("^T\\d{2}_", true, false, false, false);
        assertTrue(f.garder(requete("T01_Connexion")));
        assertFalse(f.garder(requete("Connexion T01_")));
    }

    @Test
    void regexInvalideLaisseToutPasserEtSignaleLErreur() {
        FiltreNoms f = new FiltreNoms("([", true, false, false, false);
        assertNotNull(f.getErreur());
        assertFalse(f.estActif());
        assertTrue(f.garder(requete("quelconque")));
    }

    @Test
    void unEnfantQuiCorrespondGardeSaTransaction() {
        SampleResult t = transaction("TC_Commande", requete("GET /panier"), requete("POST /payer"));
        assertTrue(new FiltreNoms("payer", false, false, false, false).garder(t));
        assertFalse(new FiltreNoms("catalogue", false, false, false, false).garder(t));
    }

    @Test
    void transactionsSeulesIgnoreLesNomsDesRequetes() {
        SampleResult t = transaction("TC_Commande", requete("POST /payer"));
        assertFalse(new FiltreNoms("payer", false, false, false, true).garder(t));
        assertTrue(new FiltreNoms("commande", false, false, false, true).garder(t));
        // Une requête hors transaction n'est jamais retenue dans ce mode.
        assertFalse(new FiltreNoms("payer", false, false, false, true).garder(requete("POST /payer")));
    }

    @Test
    void transactionImbriquee() {
        SampleResult t = transaction("TC_Parcours", transaction("TC_Paiement", requete("POST /payer")));
        assertTrue(new FiltreNoms("paiement", false, false, false, true).garder(t));
    }

    @Test
    void exclureMasqueSurLeNomPropre() {
        FiltreNoms f = new FiltreNoms("debug", false, false, true, false);
        assertFalse(f.garder(requete("Debug Sampler")));
        assertTrue(f.garder(requete("GET /accueil")));
        // Seul le nom du résultat de premier niveau compte pour l'exclusion.
        assertTrue(f.garder(transaction("TC_Accueil", requete("Debug Sampler"))));
    }

    @Test
    void collecteDesNoms() {
        SampleResult t = transaction("TC_Commande", requete("GET /panier"),
                transaction("TC_Paiement", requete("POST /payer")));
        Set<String> tous = new LinkedHashSet<>();
        FiltreNoms.collecterNoms(t, false, tous);
        assertEquals(Arrays.asList("TC_Commande", "GET /panier", "TC_Paiement", "POST /payer"),
                Arrays.asList(tous.toArray()));

        Set<String> transactions = new LinkedHashSet<>();
        FiltreNoms.collecterNoms(t, true, transactions);
        assertEquals(Arrays.asList("TC_Commande", "TC_Paiement"), Arrays.asList(transactions.toArray()));
    }

    @Test
    void regexPourNomsEchappeLesCaracteresSpeciaux() {
        String regex = FiltreNoms.regexPourNoms(Arrays.asList("GET /a?b=1", "TC (1)"));
        FiltreNoms f = new FiltreNoms(regex, true, true, false, false);
        assertTrue(f.garder(requete("GET /a?b=1")));
        assertTrue(f.garder(requete("TC (1)")));
        assertFalse(f.garder(requete("GET /a?b=12")));
        assertFalse(f.garder(requete("TC 1")));
    }
}
