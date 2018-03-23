package io.vertx.gsoc2018.qotd;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.gsoc18.qotd.Quote;
import io.vertx.reactivex.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

@RunWith(VertxUnitRunner.class)
public class QuoteOfTheDayVerticleTest {

  final private int PORT = 8080;
  final private String HOST_NAME = "localhost";
  final private String QUOTES_ROUTE = "/quotes";
  final private String REALTIME_ROUTE = "/realtime";

  private Vertx vertx;

  @Before
  public void setUp(TestContext context) throws IOException {
    vertx = Vertx.vertx();
    DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.PORT", PORT));
    vertx.deployVerticle(QuoteOfTheDayVerticle.class.getName(), options, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testGetQuotes(TestContext context) throws Exception {
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
    final Async async = context.async();
    final CountDownLatch latch = new CountDownLatch(1);

    Quote sendQuote = new Quote("This is a test quote", "Test author");
    String quoteReq = Json.encode(sendQuote);
    String length = String.valueOf(quoteReq.length());

    vertx.createHttpClient().websocket(PORT, HOST_NAME, REALTIME_ROUTE, ws -> {
      try {
        latch.await();
        ws.handler(message -> {
          Quote resQuote = new Quote(message.toJsonObject());
          context.assertEquals(sendQuote.getAuthor(), resQuote.getAuthor());
          context.assertEquals(sendQuote.getText(), resQuote.getText());
          async.complete();
        });
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }, err -> context.fail(err.getMessage()))
    .post(PORT, HOST_NAME, QUOTES_ROUTE, resp -> latch.countDown())
    .putHeader("content-type", "application/json")
    .putHeader("content-length", length)
    .write(quoteReq).end();

    async.await();
  }
}
