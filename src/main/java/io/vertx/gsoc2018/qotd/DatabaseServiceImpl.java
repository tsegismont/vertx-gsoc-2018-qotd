package io.vertx.gsoc2018.qotd;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

/**
 * @author Billy Yuan <billy112487983@gmail.com>
 */

public class DatabaseServiceImpl implements DatabaseService {
  private Vertx vertx;
  private JDBCClient jdbcClient;

  public DatabaseServiceImpl(Vertx vertx, JsonObject jdbcConfig) {
    this.vertx = vertx;
    jdbcClient = JDBCClient.createShared(vertx, jdbcConfig);
  }

  @Override
  public void prepareDatabase(Handler<AsyncResult<Void>> resultHandler) {
    Future<Void> initSchema = runScript("db.sql");
    initSchema.compose(v -> runScript("import.sql")).setHandler(res -> {
      if (res.succeeded()) {
        resultHandler.handle(Future.succeededFuture());
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
  }

  @Override
  public void getAllQuotes(Handler<AsyncResult<JsonArray>> resultHandler) {
    jdbcClient.getConnection(res -> {
      if (res.succeeded()) {
        SQLConnection sqlConnection = res.result();
        sqlConnection.query("SELECT * FROM quotes", res2 -> {
          if (res2.succeeded()) {
            sqlConnection.close();
            ResultSet resultSet = res2.result();
            resultHandler.handle(Future.succeededFuture(new JsonArray(resultSet.getRows())));
          } else {
            resultHandler.handle(Future.failedFuture(res2.cause()));
          }
        });
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
  }

  @Override
  public void postNewQuote(JsonObject quote, Handler<AsyncResult<Void>> resultHandler) {
    String text = quote.getString("text");
    String author = quote.getString("author");
    JsonArray params = new JsonArray()
      .add(text)
      .add(author);

    jdbcClient.getConnection(res -> {
      if (res.succeeded()) {
        SQLConnection sqlConnection = res.result();
        sqlConnection.updateWithParams("INSERT INTO quotes (text, author) VALUES (?, ?)", params, res2 -> {
          if (res2.succeeded()) {
            sqlConnection.close();
            resultHandler.handle(Future.succeededFuture());
          } else {
            resultHandler.handle(Future.failedFuture(res2.cause()));
          }
        });
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
  }

  /**
   * A method reading the resources in classpath, this method is executed asynchronously.
   *
   * @param filePath the path to load.
   * @return a Future representing the result of loading the resource to a String.
   */
  private Future<String> loadClasspathResourcesAsString(String filePath) {
    Future<String> future = Future.future();
    FileSystem fileSystem = vertx.fileSystem();
    fileSystem.readFile(filePath, res -> {
      if (res.succeeded()) {
        future.complete(res.result().toString());
      } else {
        future.fail("Fail to read the file");
      }
    });
    return future;
  }

  /**
   * A method to execute SQL scripts from the filesystem.
   *
   * @param script file classpath of the SQL script to run.
   * @return a Future representing the result of running the script.
   */
  private Future<Void> runScript(String script) {
    Future<Void> future = Future.future();
    loadClasspathResourcesAsString(script).setHandler(loadResource -> {
      if (loadResource.succeeded()) {
        String sqlScripts = loadResource.result();
        jdbcClient.getConnection(getConn -> {
          if (getConn.succeeded()) {
            SQLConnection connection = getConn.result();
            connection.execute(sqlScripts, exec -> {
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
        future.fail(loadResource.cause());
      }
    });

    return future;
  }

}
