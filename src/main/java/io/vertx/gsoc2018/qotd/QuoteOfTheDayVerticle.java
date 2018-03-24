package io.vertx.gsoc2018.qotd;

import io.reactivex.Single;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.Future;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;

/**
 * @author Thomas Segismont
 */
public class QuoteOfTheDayVerticle extends AbstractVerticle {

  private final Logger LOGGER = LoggerFactory.getLogger(QuoteOfTheDayVerticle.class);
  private JDBCClient jdbcClient;
  private int port;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    JsonObject jdbcConfig = new JsonObject()
        .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
        .put("driver_class", "org.h2.Driver");

    jdbcClient = JDBCClient.createShared(vertx, jdbcConfig);
    port = config().getInteger("http.port", 8080);

    Single<Boolean> initSchema = runScript("classpath:db.sql");
    Single<Boolean> importData = runScript("classpath:import.sql");

    Single.concat(initSchema, importData)
      .all(res -> res)
      .subscribe(res -> {
        if(res) {
          LOGGER.info("Initialized database successfully");
          startServer(startFuture);
        } else {
          LOGGER.error("Error in initializing database");
        }
      }, err -> LOGGER.error("Error in initializing database: " + err.getMessage()));
  }

  public void startServer(Future<Void> startFuture) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.get("/quotes").handler(this::getQuotesHandler);
    router.post("/quotes").handler(this::addQuoteHandler);

    vertx.createHttpServer()
      .requestHandler(router::accept)
      .websocketHandler(serverWebSocket -> {
        if(serverWebSocket.path().equals("/realtime")) {
          serverWebSocket.accept();
          vertx.eventBus().consumer("realtime", message -> {
            serverWebSocket.writeTextMessage(message.body().toString());
          });
        } else {
          serverWebSocket.reject();
        }
      })
      .rxListen(port)
      .subscribe(res -> {
        LOGGER.info("Initialized http server successfully");
        startFuture.complete();
      }, err -> LOGGER.error("Failed to initialize http server: " + err.getMessage()));
  }

  public void getQuotesHandler(RoutingContext routingContext) {
    jdbcClient.rxGetConnection()
      .flatMap(conn -> conn.rxQuery("SELECT * FROM quotes").doFinally(conn::close))
      .flattenAsFlowable(ResultSet::getRows)
      .map(Quote::new)
      .toList()
      .subscribe(quotes -> {
        routingContext.response()
          .putHeader("content-type", "application/json; charset=utf-8")
          .end(Json.encodePrettily(quotes));
      }, error -> routingContext.response().end("Error occurred"));
  }

  public void addQuoteHandler(RoutingContext routingContext) {
    JsonObject requestBody = routingContext.getBodyAsJson();
    String text = requestBody.getString("text");
    String author = requestBody.getString("author");
    if(text == null) {
      routingContext.response().setStatusCode(400).end();
      return;
    }
    if(author == null || author.equals("")) author = "Unknown";
    Quote quote = new Quote(text, author);

    JsonArray params = new JsonArray().add(quote.getText()).add(quote.getAuthor());
    jdbcClient.rxGetConnection()
      .flatMap(conn -> conn.rxUpdateWithParams("INSERT INTO quotes (text, author) VALUES(?,?)", params)
        .doFinally(conn::close))
      .map(UpdateResult::toJson)
      .map(res -> !res.isEmpty())
      .subscribe(res -> {
      if(res) {
        vertx.eventBus().publish("realtime", Json.encode(quote));
        routingContext.response().end(Json.encode(quote));
      } else {
        routingContext.response().end("Some error occurred");
      }
    }, error -> routingContext.response().end("Some error occurred"));
  }

  private Single<Boolean> runScript(String script) {
    return jdbcClient.rxGetConnection()
      .flatMap(conn -> {
        Single<Boolean> res = conn.rxExecute("RUNSCRIPT FROM '" + script + "'")
          .toSingleDefault(true)
          .onErrorReturnItem(false);
        return res.doFinally(conn::close);
      });
  }
}
