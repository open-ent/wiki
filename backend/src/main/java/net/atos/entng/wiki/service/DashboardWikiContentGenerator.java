package net.atos.entng.wiki.service;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.edificewikigenerator.ContentRequest;
import org.entcore.edificewikigenerator.Course;
import org.entcore.edificewikigenerator.CourseHierarchy;
import org.entcore.edificewikigenerator.CourseResponse;
import org.entcore.edificewikigenerator.Owner;
import org.entcore.edificewikigenerator.Page;
import org.entcore.edificewikigenerator.PageStructure;
import org.entcore.edificewikigenerator.Usage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Génération par le service IA du dashboard ({@code POST /dashboard/api/ai/wiki-generate}).
 *
 * <p>Les cookies de l'enseignant sont transmis (dont {@code oneSessionId}, signé) : le dashboard
 * l'authentifie, lui impute quota et consommation, et choisit le fournisseur configuré par
 * l'administrateur. La réponse — titres et contenus — est appliquée avec les mêmes méthodes que
 * celle du service d'Edifice : la structure d'abord, puis les pages une à une.</p>
 *
 * <p>Un échec ne doit pas laisser l'enseignant devant un wiki qui « charge » indéfiniment : le
 * wiki reçoit alors une page qui explique pourquoi la génération n'a pas abouti.</p>
 */
public class DashboardWikiContentGenerator implements WikiContentGenerator {

    private static final Logger log = LoggerFactory.getLogger(DashboardWikiContentGenerator.class);
    /** Une séquence complète demande parfois plusieurs minutes au modèle. */
    private static final long TIMEOUT_MS = 6 * 60 * 1000L;

    private final HttpClient client;
    private final String endpoint;
    private final WikiService wikiService;

    public DashboardWikiContentGenerator(Vertx vertx, String dashboardUrl, WikiService wikiService) {
        this.client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false));
        this.endpoint = dashboardUrl.replaceAll("/+$", "") + "/dashboard/api/ai/wiki-generate";
        this.wikiService = wikiService;
    }

    @Override
    public void generate(ContentRequest request, String cookies) {
        final String wikiId = request.getWikiId();
        final JsonObject body = new JsonObject()
                .put("level", request.getLevel())
                .put("subject", request.getSubject())
                .put("sequence", request.getContent())
                .put("keywords", request.getKeywords());
        final RequestOptions options = new RequestOptions()
                .setMethod(HttpMethod.POST)
                .setAbsoluteURI(endpoint)
                .setTimeout(TIMEOUT_MS)
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookies);
        if (request.getBrowser() != null && !request.getBrowser().isEmpty()) {
            options.putHeader("User-Agent", request.getBrowser());
        }
        client.request(options)
                .compose(req -> req.send(body.encode()))
                .compose(resp -> resp.body().map(buffer -> {
                    final JsonObject json = buffer.toJsonObject();
                    if (resp.statusCode() != 200) {
                        throw new IllegalStateException(json.getString("error", "HTTP " + resp.statusCode()));
                    }
                    return json;
                }))
                .compose(course -> apply(wikiId, course))
                .onSuccess(v -> log.info("[Wiki] Génération IA terminée pour le wiki " + wikiId))
                .onFailure(error -> {
                    log.error("[Wiki] Génération IA impossible pour le wiki " + wikiId, error);
                    apply(wikiId, new JsonObject()
                            .put("description", "")
                            .put("pages", new JsonArray().add(new JsonObject()
                                    .put("title", "Génération impossible")
                                    .put("content", "<p>La génération automatique de ce wiki n'a pas abouti : "
                                            + escape(error.getMessage()) + "</p><p>Vous pouvez la relancer, ou "
                                            + "rédiger les pages vous-même.</p>"))))
                            .onFailure(e -> log.error("[Wiki] Échec non signalé au wiki " + wikiId, e));
                });
    }

    /** Structure (titres), puis contenu page par page : c'est ce que le front sait afficher. */
    private Future<Void> apply(String wikiId, JsonObject course) {
        final JsonArray pages = course.getJsonArray("pages", new JsonArray());
        final List<PageStructure> structure = new ArrayList<PageStructure>();
        for (int i = 0; i < pages.size(); i++) {
            structure.add(new PageStructure(i == 0, null, i, null, pages.getJsonObject(i).getString("title")));
        }
        final JsonObject usageJson = course.getJsonObject("usage", new JsonObject());
        final int input = usageJson.getInteger("inputTokens", 0);
        final int output = usageJson.getInteger("outputTokens", 0);
        Future<Void> chain = wikiService.updateWikiStructureFromAI(wikiId, new CourseHierarchy(structure, wikiId));
        for (int i = 0; i < pages.size(); i++) {
            final int position = i;
            final JsonObject page = pages.getJsonObject(i);
            chain = chain.compose(v -> {
                final Page generated = new Page(null, null, null, page.getString("content", ""), 0, null, true, null,
                        null, null, null, position, page.getString("title"));
                final Course single = new Course(wikiId, null, course.getString("description", ""), null,
                        new Owner(null, null), Collections.singletonList(generated), null,
                        course.getString("title"));
                return wikiService.updateWikiContentFromAI(wikiId, new CourseResponse(single, null,
                        course.getString("model"), "success", new Usage(input, output, input + output),
                        "dashboard", wikiId));
            });
        }
        return chain;
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
