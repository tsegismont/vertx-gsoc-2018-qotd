package io.vertx.gsoc2018.qotd;

import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;

/**
 * @author Thomas Segismont
 */
public class QuoteOfTheDayVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(QuoteOfTheDayVerticle.class.getName());

  private JDBCClient jdbcClient;
  private final PublishSubject<JsonObject> dbUpdatesPublisher = PublishSubject.create();
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

    // web server setup
    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);

    router.post(QUOTES_PATH).handler(routingContext -> {
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
            String sqlToExecute = String.format("INSERT INTO quotes (text,author) VALUES ('%s', '%s');", text, finalAuthor);
            connection.execute(sqlToExecute, exec -> {
              if (exec.succeeded()) {
                response.setStatusCode(200);
                response.end();

                // notify subscribers about database update
                JsonObject newQuote = new JsonObject()
                  .put(AUTHOR_FILED, finalAuthor)
                  .put(TEXT_FILED, text);
                dbUpdatesPublisher.onNext(newQuote);
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
    });

    router.get(QUOTES_PATH).handler(routingContext -> {
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
    });

    server.websocketHandler(ws -> {
      if (ws.path().equals("/realtime")) {
        ws.accept();
        Disposable subscription = dbUpdatesPublisher.subscribe(updateMessage -> {
          ws.writeTextMessage(updateMessage.toString());
        });
        ws.closeHandler(v -> subscription.dispose());
      } else {
        ws.reject();
      }
    });


    int port = config().getInteger("http.port", 8080);
    server.requestHandler(router::accept).listen(port);

    Future<Void> initSchema = runScript("db.sql");

    initSchema.setHandler(initSchemaResult -> {
      if (initSchemaResult.succeeded()) {
        logger.info("initial schema successfully had been loaded");
        Future<Void> importData = runScript("import.sql");
        importData.setHandler(dataImportResult -> {
          if (dataImportResult.succeeded()) {
            logger.info("initial data had been loaded");
            startFuture.complete();
          } else {
            logger.error("failed in attempt to load initial data", dataImportResult.cause());
          }
        });
      } else {
        logger.error("failed in attempt to load initial schema", initSchemaResult.cause());
      }
    });
  }

  private void responseWithError(HttpServerResponse response) {
    response.setStatusCode(400);
    response.end("Something went wrong");
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
}
