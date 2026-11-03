package net.atos.entng.wiki.service;

import org.entcore.edificewikigenerator.ContentRequest;

/**
 * Lance la génération par IA d'un wiki déjà créé (vide, marqué {@code aiGenerated}).
 *
 * <p>La génération est asynchrone : le résultat revient par
 * {@link WikiService#updateWikiStructureFromAI} (les titres des pages) puis
 * {@link WikiService#updateWikiContentFromAI} (le contenu, page par page), ce qui permet au front
 * d'afficher la progression.</p>
 *
 * <ul>
 *   <li>{@code dashboard} (défaut) — le service IA du dashboard, avec le fournisseur configuré dans
 *       Configuration → IA (Claude de préférence) ;</li>
 *   <li>{@code edifice} — le service de génération d'Edifice, par son broker.</li>
 * </ul>
 */
public interface WikiContentGenerator {

    /**
     * @param request la demande (niveau, matière, séquence, mots-clés, wiki cible)
     * @param cookies l'en-tête Cookie de la requête de l'enseignant : le cookie de session est signé,
     *                {@link ContentRequest#getSession()} n'en porte que l'identifiant
     */
    void generate(ContentRequest request, String cookies);
}
