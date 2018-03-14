package io.vertx.gsoc2018.qotd;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Thomas Segismont
 * @author Billy Yuan
 */
public class QuoteOfTheDayVerticle extends AbstractVerticle {
  private DatabaseService databaseService;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    JsonObject jdbcConfig = new JsonObject()
        .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
        .put("driver_class", "org.h2.Driver");

    databaseService = new DatabaseServiceImpl(vertx, jdbcConfig);

    Future<Void> prepareDatabaseFuture = Future.future();
    databaseService.prepareDatabase(prepareDatabaseFuture);

    int port = config().getInteger("http.port", 8080);

    Router router = Router.router(vertx);

    // router.route().handler(BodyHandler.create());

    // router.route().consumes("application/json").produces("application/json");

    router.get("/quotes").handler(this::getAllQuotes);

    // setup database and web server

    prepareDatabaseFuture.compose(v -> {
      Future<HttpServer> webServerFuture = Future.future();
      vertx.createHttpServer().requestHandler(router::accept)
           .listen(port, webServerFuture.completer());
      return webServerFuture;
    }).setHandler(res -> {
      if (res.succeeded()) {
        startFuture.complete();
      } else {
        startFuture.fail(res.cause());
      }
    });
  }

  private void getAllQuotes(RoutingContext routingContext) {
    databaseService.getAllQuotes(res -> {
      if (res.succeeded()) {
        routingContext.response().setStatusCode(200)
                      .putHeader("Content-Type", "application/json; charset=utf-8")
                      .end(res.result().toString());
      } else {
        routingContext.response().setStatusCode(404)
                      .putHeader("Content-Type", "application/json; charset=utf-8")
                      .end();
      }
    });
  }
}
