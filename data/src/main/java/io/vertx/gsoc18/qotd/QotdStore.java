package io.vertx.gsoc18.qotd;

import io.reactivex.Single;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.jdbc.JDBCClient;

public class QotdStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(QotdStore.class.getName());

  private JDBCClient jdbcClient;

  public QotdStore(Vertx vertx) {
    JsonObject jdbcConfig = new JsonObject()
      .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
      .put("driver_class", "org.h2.Driver");
    jdbcClient = JDBCClient.createShared(vertx, jdbcConfig);

    Single<Boolean> initSchema = runScript("classpath:db.sql");
    Single<Boolean> importData = runScript("classpath:import.sql");

    Single.zip(initSchema, importData, (b1, b2) -> b1 && b2)
      .subscribe(res -> {
        if(res) {
          LOGGER.info("Initialized database successfully");
        } else {
          LOGGER.error("Error in initializing database");
        }
      }, err -> LOGGER.error("Error in initializing database: " + err.getMessage()));
  }

  public void addQuote(Quote quote) {

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
