package io.vertx.gsoc2018.qotd;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

/**
 * @author Thomas Segismont
 */
public class QuoteOfTheDayVerticle extends AbstractVerticle {

  private JDBCClient jdbcClient;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    JsonObject jdbcConfig = new JsonObject()
        .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
        .put("driver_class", "org.h2.Driver");

    jdbcClient = JDBCClient.createShared(vertx, jdbcConfig);

    Future<Void> initSchema = runScript("classpath:db.sql");
    Future<Void> importData = runScript("classpath:import.sql");

    int port = config().getInteger("http.port", 8080);

    // TODO setup database and web server and eventually...
    startFuture.complete();
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
