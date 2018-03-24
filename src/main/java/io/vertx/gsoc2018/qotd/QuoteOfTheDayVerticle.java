package io.vertx.gsoc2018.qotd;

import io.reactivex.Single;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.jdbc.JDBCClient;

/**
 * @author Thomas Segismont
 */
public class QuoteOfTheDayVerticle extends AbstractVerticle {

  private final Logger LOGGER = LoggerFactory.getLogger(QuoteOfTheDayVerticle.class);
  private JDBCClient jdbcClient;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    JsonObject jdbcConfig = new JsonObject()
        .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
        .put("driver_class", "org.h2.Driver");

    jdbcClient = JDBCClient.createShared(vertx, jdbcConfig);

    Single<Boolean> initSchema = runScript("classpath:db.sql");
    Single<Boolean> importData = runScript("classpath:import.sql");

    Single.concat(initSchema, importData)
      .all(res -> res)
      .subscribe(res -> {
        if(res) {
          LOGGER.info("Initialized database successfully");
          startFuture.complete();
        } else {
          LOGGER.error("Error in initializing database");
        }
      }, err -> LOGGER.error("Error in initializing database: " + err.getMessage()));
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
