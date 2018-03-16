package io.vertx.gsoc2018.qotd;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Thomas Segismont
 */
public class QuoteOfTheDayVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(QuoteOfTheDayVerticle.class.getName());

  private JDBCClient jdbcClient;
  private static final String DB_UPDATES_ADDRESS = "db.updates";
  private static final String QUOTES_PATH = "/quotes";
  private static final String DEFAULT_AUTHOR_VALUE = "Unknown";
  private static final String AUTHOR_FILED = "author";
  private static final String TEXT_FILED = "text";

  @Override
  public void start(Future<Void> startFuture) {

    JsonObject jdbcConfig = new JsonObject()
        .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
        .put("driver_class", "org.h2.Driver");

    jdbcClient = JDBCClient.createShared(vertx, jdbcConfig);

    Future<Void> initSchema = runScript("db.sql");

    initSchema
      .compose(result -> {
        logger.info("initial schema successfully had been loaded");
        return runScript("import.sql");
      })
      .compose(result -> {
        logger.info("initial data had been loaded");
        return startHttpServer();
      })
      .compose(result -> {
        logger.info("http server started");
        startFuture.complete();
      }, startFuture);
  }

  private void responseWithError(HttpServerResponse response) {
    response.setStatusCode(500);
    response.end("Something went wrong");
  }

  private Future<HttpServer> startHttpServer() {
    Future<HttpServer> serverStarted = Future.future();
    // web server setup
    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);

    router.post(QUOTES_PATH).handler(this::handlePostQuotes);
    router.get(QUOTES_PATH).handler(this::handleGetQuotes);

    server.websocketHandler(ws -> {
      if (ws.path().equals("/realtime")) {
        ws.accept();
        MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(DB_UPDATES_ADDRESS, message -> {
          ws.writeTextMessage(message.body().toString());
        });
        ws.closeHandler(v -> consumer.unregister());
      } else {
        ws.reject();
      }
    });


    int port = config().getInteger("http.port", 8080);
    server.requestHandler(router::accept).listen(port, serverStarted.completer());
    return serverStarted;
  }

  private Future<Void> runScript(String script) {
    Future<Void> future = Future.future();

    vertx.fileSystem().readFile(script, fileReadResult -> {
      if (fileReadResult.succeeded()) {
        jdbcClient.getConnection(getConn -> {
          if (getConn.succeeded()) {
            SQLConnection connection = getConn.result();
            String sqlToExecute = fileReadResult.result().toString();
            logger.info("Executing sql statement " + sqlToExecute);
            connection.execute(sqlToExecute, exec -> {
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
      } else {
        future.fail(fileReadResult.cause());
      }
    });
    return future;
  }

  @Override
  public void stop() {
    jdbcClient.close();
  }

  private void handlePostQuotes(RoutingContext routingContext) {
    logger.info("receive post");
    HttpServerResponse response = routingContext.response();
    routingContext.request().bodyHandler(requestBody -> {
      JsonObject request = requestBody.toJsonObject();
      String text = request.getString(TEXT_FILED);
      if (text == null) {
        response.setStatusCode(404);
        response.end("json in a POST request should have a 'text' filed");
        return;
      }

      String author = request.getString(AUTHOR_FILED);
      if (author == null) {
        author = DEFAULT_AUTHOR_VALUE;
      }

      String finalAuthor = author;
      jdbcClient.getConnection(getConn -> {
        if (getConn.succeeded()) {
          SQLConnection connection = getConn.result();
          String sqlToExecute = "INSERT INTO quotes (text,author) VALUES (?, ?);";
          JsonArray sqlParams = new JsonArray().add(text).add(finalAuthor);
          connection.updateWithParams(sqlToExecute, sqlParams, exec -> {
            if (exec.succeeded()) {
              response.setStatusCode(200);
              response.end();

              // notify subscribers about database update
              JsonObject newQuote = new JsonObject()
                .put(AUTHOR_FILED, finalAuthor)
                .put(TEXT_FILED, text);
              vertx.eventBus().publish(DB_UPDATES_ADDRESS, newQuote);
            } else {
              logger.error("failed in query executing", exec.cause());
              responseWithError(response);
            }
            connection.close();
          });
        } else {
          logger.error("failed in db connection establishing", getConn.cause());
          responseWithError(response);
        }
      });

    });
  }

  private void handleGetQuotes(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    jdbcClient.getConnection(getConn -> {
      if (getConn.succeeded()) {
        SQLConnection connection = getConn.result();
        String sqlToExecute = "SELECT * FROM quotes";
        connection.query(sqlToExecute, query -> {
          if (query.succeeded()) {
            JsonArray responseBody = new JsonArray();
            query.result().getRows().forEach(responseBody::add);
            response.end(responseBody.toBuffer());
          } else {
            logger.error("failed in query executing", query.cause());
            responseWithError(response);
          }
          connection.close();
        });
      } else {
        logger.error("failed in db connection establishing", getConn.cause());
        responseWithError(response);
      }
    });
  }
}
