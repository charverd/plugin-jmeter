package fr.jmeter.plugins.enregistreur;

import org.apache.jmeter.protocol.http.proxy.ProxyControl;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enregistreur HTTP(S) identique à celui de JMeter, sauf qu'il ne remplace
 * jamais une valeur enregistrée par une référence de variable.
 *
 * <p>L'enregistreur d'origine cherche les « User Defined Variables » (sous
 * l'enregistreur, sous le contrôleur cible et ses parents, et celles du plan
 * de test) et remplace dans chaque requête toute valeur égale à l'une d'elles
 * par {@code ${nom}}. Ce remplacement est fait dans une méthode privée ; on
 * l'annule donc juste après coup, en comparant à une copie prise avant.
 */
public class EnregistreurSansVariables extends ProxyControl {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(EnregistreurSansVariables.class);

    @Override
    public synchronized void deliverSampler(HTTPSamplerBase sampler, TestElement[] testElements,
            SampleResult result) {
        TestElement copieRequete = sampler == null ? null : (TestElement) sampler.clone();
        TestElement[] copiesConfig = null;
        if (testElements != null) {
            copiesConfig = new TestElement[testElements.length];
            for (int i = 0; i < testElements.length; i++) {
                copiesConfig[i] = testElements[i] == null ? null : (TestElement) testElements[i].clone();
            }
        }

        super.deliverSampler(sampler, testElements, result);

        int restaurees = RestaurationValeurs.restaurer(sampler, copieRequete);
        if (testElements != null) {
            for (int i = 0; i < testElements.length; i++) {
                restaurees += RestaurationValeurs.restaurer(testElements[i], copiesConfig[i]);
            }
        }
        if (restaurees > 0 && log.isDebugEnabled()) {
            log.debug("{} valeur(s) gardée(s) brute(s) au lieu de ${...} : {}", restaurees,
                    sampler == null ? "" : sampler.getName());
        }
    }
}
