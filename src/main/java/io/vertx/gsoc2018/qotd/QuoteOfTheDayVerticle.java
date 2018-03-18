package io.vertx.gsoc2018.qotd;

import static io.vertx.core.logging.LoggerFactory.getLogger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;



public class QuoteOfTheDayVerticle extends AbstractVerticle {

  private static final Logger log = getLogger(QuoteOfTheDayVerticle.class.getName());

  public static final String QUOTES_PATH = "/quotes";
  public static final String AUTHOR_FIELD = "author";
  public static final String AUTHOR_FIELD_DEFAULT_VALUE = "Unknown";
  public static final String TEXT_FIELD = "text";

  private JDBCClient jdbcClient;

  @Override
  public void start(Future<Void> startFuture) {
    JsonObject jdbcConfig = new JsonObject()
        .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
        .put("driver_class", "org.h2.Driver");

    jdbcClient = JDBCClient.createShared(vertx, jdbcConfig);

    HttpServer server = vertx.createHttpServer();

    Router router = Router.router(vertx);
    router.get(QUOTES_PATH).handler(this::getQuotes);
    router.post(QUOTES_PATH).handler(BodyHandler.create()).handler(this::postQuote);

    initDatabase()
      .setHandler(asyncRes -> {
        if (asyncRes.succeeded()) {
          Integer port = config().getInteger("http.port", 8080);
          server.requestHandler(router::accept).listen(port);
          startFuture.complete();
        }
        else {
          log.error("failed in attempt to load initial data", asyncRes.cause());
          startFuture.failed();
        }
      });

  }

  private Future<Void> initDatabase() {
    return runScript("classpath:db.sql")
      .compose(e -> runScript("classpath:import.sql"));
  }

  private void getQuotes(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    jdbcClient.getConnection(connectionAsyncResult -> {
      if (connectionAsyncResult.succeeded()) {
        SQLConnection connection = connectionAsyncResult.result();
        connection.query("SELECT * FROM quotes",
                         queryResult -> {
                           if (queryResult.succeeded()) {
                             ResultSet result = queryResult.result();
                             JsonArray jsonArray = new JsonArray();
                             for (JsonArray json : result.getResults()) {
                               jsonArray.add(json);
                             }
                             response
                               .putHeader("Content-Type", "application/json")
                               .end(jsonArray.toBuffer());
                           }
                           else {
                             respondWithError(response);
                           }
                           connection.close();
                         });
      }
      else {
        respondWithError(response);
      }
    });
  }

  private void postQuote(RoutingContext routingContext) {

    HttpServerResponse response = routingContext.response();
    JsonObject quote = routingContext.getBodyAsJson();

    String author = quote.getString(AUTHOR_FIELD, AUTHOR_FIELD_DEFAULT_VALUE);
    String text = quote.getString(TEXT_FIELD);
    if (text == null) {
      response.setStatusCode(400).end();
    }
    else {
      JsonArray updateParams = new JsonArray()
        .add(author)
        .add(text);

      jdbcClient.getConnection(connectionAsyncResult -> {
        SQLConnection conn = connectionAsyncResult.result();
        conn.updateWithParams("INSERT INTO quotes(author, text) VALUES (?,?)",
                              updateParams,
                              resultSet -> {
                                if (resultSet.succeeded()) {
                                  JsonObject result = new JsonObject()
                                    .put(AUTHOR_FIELD, author)
                                    .put(TEXT_FIELD, text);
                                  response.end(result.toBuffer());
                                }
                                else {
                                  respondWithError(response);
                                }
                                conn.close();
                              });
      });

    }

  }

  private void respondWithError(HttpServerResponse response) {
    response.setStatusCode(500)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .end();
  }

  private Future<Void> runScript(String script) {
    Future<Void> future = Future.future();
    jdbcClient.getConnection(getConn -> {
      if (getConn.succeeded()) {
        SQLConnection connection = getConn.result();
        connection.execute("RUNSCRIPT FROM '" + script + "'", exec -> {
          connection.close();
          if (exec.succeeded()) {
            future.complete();
          } else {
            future.fail(exec.cause());
          }
        });
      } else {
        future.fail(getConn.cause());
      }
    });
    return future;
  }
}
