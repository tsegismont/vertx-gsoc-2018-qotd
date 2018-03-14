package io.vertx.gsoc2018.qotd;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A service to contact with the H2 database.
 *
 * @author Billy Yuan <billy112487983@gmail.com>
 */

public interface DatabaseService {
  void prepareDatabase(Handler<AsyncResult<Void>> resultHandler);

  void getAllQuotes(Handler<AsyncResult<JsonArray>> resultHandler);

  void postNewQuote(JsonObject quote, Handler<AsyncResult<Void>> resultHandler);
}
