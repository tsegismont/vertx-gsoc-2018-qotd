package io.vertx.gsoc2018.qotd;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * @author Thomas Segismont
 * @author Billy Yuan <billy112487983@gmail.com>
 */
public class QuoteOfTheDayVerticle extends AbstractVerticle {
  private static final String DEFAULT_AUTHOR = "Unknown";
  private static final String REAL_TIME_QUEUE = "real_time_quote";

  private DatabaseService databaseService;

  @Override
  public void start(Future<Void> startFuture) {
    JsonObject jdbcConfig = new JsonObject()
      .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
      .put("driver_class", "org.h2.Driver");

    databaseService = new DatabaseServiceImpl(vertx, jdbcConfig);

    Future<Void> prepareDatabaseFuture = Future.future();
    databaseService.prepareDatabase(prepareDatabaseFuture);

    int port = config().getInteger("http.port", 8080);

    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());

    router.route().consumes("application/json").produces("application/json");

    router.get("/quotes").handler(this::getAllQuotes);
    router.post("/quotes").handler(this::postNewQuote);

    // setup database and web server

    prepareDatabaseFuture.compose(v -> {
      Future<HttpServer> webServerFuture = Future.future();
      vertx.createHttpServer()
        .websocketHandler(getWebSocketHandler())
        .requestHandler(router::accept)
        .listen(port, webServerFuture);
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

  private void postNewQuote(RoutingContext routingContext) {
    JsonObject body = routingContext.getBodyAsJson();
    String text = body.getString("text");
    String author = body.getString("author");

    if (text == null) {
      routingContext.response().setStatusCode(404)
        .putHeader("Content-Type", "application/json; charset=utf-8")
        .end();
      return;
    }

    JsonObject newQuote = new JsonObject().put("text", text);
    if (author == null) {
      newQuote.put("author", DEFAULT_AUTHOR);
    } else {
      newQuote.put("author", author);
    }

    databaseService.postNewQuote(newQuote, res -> {
      if (res.succeeded()) {
        vertx.eventBus().publish(REAL_TIME_QUEUE, newQuote);
        routingContext.response().setStatusCode(200)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end(newQuote.encodePrettily());
      } else {
        routingContext.response().setStatusCode(400)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end();
      }
    });
  }

  private Handler<ServerWebSocket> getWebSocketHandler() {
    return serverWebSocket -> {
      if (serverWebSocket.path().equals("/realtime")) {
        serverWebSocket.accept();
        vertx.eventBus().consumer(REAL_TIME_QUEUE, message -> {
          serverWebSocket.writeTextMessage(message.body().toString());
        });
      } else {
        serverWebSocket.reject();
      }
    };
  }
}
