package io.vertx.gsoc2018.qotd;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

/**
 * @author Thomas Segismont
 */
public class QuoteOfTheDayVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(QuoteOfTheDayVerticle.class.getName());

  private JDBCClient jdbcClient;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    JsonObject jdbcConfig = new JsonObject()
        .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
        .put("driver_class", "org.h2.Driver");

    jdbcClient = JDBCClient.createShared(vertx, jdbcConfig);

    Future<Void> initSchema = runScript("db.sql");

    initSchema.setHandler(initSchemaResult -> {
      if (initSchemaResult.succeeded()) {
        logger.info("initial schema successfully had been loaded");
        Future<Void> importData = runScript("import.sql");
        importData.setHandler(dataImportResult -> {
          if (dataImportResult.succeeded()) {
            logger.info("initial data had been loaded");
          } else {
            logger.error("failed in attempt to load initial data", dataImportResult.cause());
          }
        });
      } else {
        logger.error("failed in attempt to load initial schema", initSchemaResult.cause());
      }
    });

    int port = config().getInteger("http.port", 8080);

    // TODO setup database and web server and eventually...
    startFuture.complete();
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
}
