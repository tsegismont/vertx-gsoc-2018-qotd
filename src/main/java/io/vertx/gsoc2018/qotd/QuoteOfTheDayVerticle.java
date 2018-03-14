package io.vertx.gsoc2018.qotd;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * @author Thomas Segismont
 * @author Billy Yuan
 */
public class QuoteOfTheDayVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    JsonObject jdbcConfig = new JsonObject()
      .put("url", "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
      .put("driver_class", "org.h2.Driver");

    DatabaseService databaseService = new DatabaseServiceImpl(vertx, jdbcConfig);

    Future<Void> prepareDatabaseFuture = Future.future();
    databaseService.prepareDatabase(prepareDatabaseFuture);

    int port = config().getInteger("http.port", 8080);

    // TODO setup database and web server and eventually...
    prepareDatabaseFuture.setHandler(res -> {
      if (res.succeeded()) {
        databaseService.getAllQuotes(query->{
          if (query.succeeded()){
            System.out.println(query.result());
          }
        });
        startFuture.complete();
      } else {
        startFuture.fail(res.cause());
      }
    });
  }
}
