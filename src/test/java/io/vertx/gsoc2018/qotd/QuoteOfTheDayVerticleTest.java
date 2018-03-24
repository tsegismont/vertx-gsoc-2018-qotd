package io.vertx.gsoc2018.qotd;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Thomas Segismont
 */
@RunWith(VertxUnitRunner.class)
public class QuoteOfTheDayVerticleTest {

  private static final int PORT = 8888;
  final private String HOST_NAME = "localhost";
  final private String QUOTES_ROUTE = "/quotes";

  private Vertx vertx;

  @Before
  public void setup(TestContext testContext) {
    vertx = Vertx.vertx();
    JsonObject config = new JsonObject().put("http.port", PORT);
    DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle(QuoteOfTheDayVerticle.class.getName(), deploymentOptions, testContext.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext testContext) {
    vertx.close(testContext.asyncAssertSuccess());
  }

  @Test
  public void testGetQuotes(TestContext context) {
    final Async async = context.async();
    vertx.createHttpClient().getNow(PORT, HOST_NAME, QUOTES_ROUTE, response -> {
      context.assertEquals(200, response.statusCode());
      response.bodyHandler(body -> {
        JsonArray quotesJson = body.toJsonArray();
        context.assertFalse(quotesJson.isEmpty());
        async.complete();
      });
    });
  }

  @Test
  public void testPostQuoteWithAuthorAndText(TestContext context) {
    final Async async = context.async();

    Quote sendQuote = new Quote("This is a test quote", "Test author");
    String quoteReq = Json.encode(sendQuote);
    String length = String.valueOf(quoteReq.length());

    vertx.createHttpClient().post(PORT, HOST_NAME, QUOTES_ROUTE, response -> {
      context.assertEquals(200, response.statusCode());
      response.bodyHandler(body -> {
        Quote resQuote = new Quote(body.toJsonObject());
        context.assertEquals(sendQuote.getAuthor(), resQuote.getAuthor());
        context.assertEquals(sendQuote.getText(), resQuote.getText());
        async.complete();
      });
    }).putHeader("content-type", "application/json")
    .putHeader("content-length", length)
    .write(quoteReq).end();
  }

  @Test
  public void testPostQuoteWithNoAuthor(TestContext context) {
    final Async async = context.async();

    JsonObject sendQuoteJson = new JsonObject().put("text", "This is a post with no author");
    String quoteReq = Json.encode(sendQuoteJson);
    String length = String.valueOf(quoteReq.length());

    vertx.createHttpClient().post(PORT, HOST_NAME, QUOTES_ROUTE, response -> {
      context.assertEquals(200, response.statusCode());
      response.bodyHandler(body -> {
        Quote resQuote = new Quote(body.toJsonObject());
        context.assertEquals(sendQuoteJson.getString("text"), resQuote.getText());
        context.assertEquals("Unknown", resQuote.getAuthor());
        async.complete();
      });
    }).putHeader("content-type", "application/json")
      .putHeader("content-length", length)
      .write(quoteReq).end();
  }

  @Test
  public void testPostQuoteWithNoText(TestContext context) {
    final Async async = context.async();

    JsonObject sendQuoteJson = new JsonObject().put("author", "Test author");
    String quoteReq = Json.encode(sendQuoteJson);
    String length = String.valueOf(quoteReq.length());

    vertx.createHttpClient().post(PORT, HOST_NAME, QUOTES_ROUTE, response -> {
      context.assertEquals(400, response.statusCode());
      async.complete();
    }).putHeader("content-type", "application/json")
      .putHeader("content-length", length)
      .write(quoteReq).end();
  }

  @Test
  public void testRealtimeQuotes(TestContext context) {
    final Async connectionAsync = context.async();
    final Async addQuoteAsync = context.async();
    final Async async = context.async();

    Quote sendQuote = new Quote("This is a test quote", "Test author");
    String sendQuoteString = Json.encode(sendQuote);
    String length = String.valueOf(sendQuoteString.length());

    HttpClient client = vertx.createHttpClient();
    client.websocket(PORT, HOST_NAME, "/realtime", webSocket -> {
      connectionAsync.complete();
      webSocket.handler(message -> {
        addQuoteAsync.await();
        Quote resQuote = new Quote(message.toJsonObject());
        context.assertEquals(sendQuote.getAuthor(), resQuote.getAuthor());
        context.assertEquals(sendQuote.getText(), resQuote.getText());
        async.complete();
      });
    });

    connectionAsync.await();

    vertx.createHttpClient().post(PORT, HOST_NAME, QUOTES_ROUTE, response -> {
      context.assertEquals(200, response.statusCode());
      addQuoteAsync.complete();
    }).putHeader("content-type", "application/json")
    .putHeader("content-length", length)
    .write(sendQuoteString).end();

    async.await();
  }
}
