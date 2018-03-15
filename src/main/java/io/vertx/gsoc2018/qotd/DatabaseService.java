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
  /**
   * Setup the schema and import the initial data.
   *
   * @param resultHandler the handler which is called once the database is ready.
   */
  void prepareDatabase(Handler<AsyncResult<Void>> resultHandler);

  /**
   * Get all quotes from the database.
   *
   * @param resultHandler the handler which is called once the data is fetched.
   */
  void getAllQuotes(Handler<AsyncResult<JsonArray>> resultHandler);

  /**
   * Post a new quote to the database.
   *
   * @param quote         the new quote which will be inserted into the database.
   * @param resultHandler the handler which is called once the insertion is complete.
   */
  void postNewQuote(JsonObject quote, Handler<AsyncResult<Void>> resultHandler);
}
