package fr.jmeter.plugins.sauvegarde;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.jmeter.samplers.SampleResult;

/** Un arbre de résultats du plan, tel qu'il sera écrit dans la page. */
public final class ArbreExporte {

    final String nom;
    final String chemin;
    final boolean filtrable;
    final Filtre filtre;
    final List<SampleResult> resultats;

    /**
     * @param nom nom du récepteur dans le plan
     * @param chemin chemin du récepteur dans le plan (« Plan > Groupe > Arbre »)
     * @param filtrable vrai pour l'arbre de résultats filtrable
     * @param filtre filtre actif de l'arbre filtrable, ou {@code null}
     * @param resultats résultats de premier niveau, dans l'ordre d'arrivée (copie)
     */
    public ArbreExporte(String nom, String chemin, boolean filtrable, Filtre filtre, List<SampleResult> resultats) {
        this.nom = nom;
        this.chemin = chemin;
        this.filtrable = filtrable;
        this.filtre = filtre;
        this.resultats = Collections.unmodifiableList(new ArrayList<>(resultats));
    }

    public String getNom() {
        return nom;
    }

    public List<SampleResult> getResultats() {
        return resultats;
    }

    public Filtre getFiltre() {
        return filtre;
    }

    public boolean isFiltrable() {
        return filtrable;
    }
}
