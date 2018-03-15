package io.vertx.gsoc2018.qotd;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

/**
 * @author Thomas Segismont
 * @author Billy Yuan <billy112487983@gmail.com>
 */
@RunWith(VertxUnitRunner.class)
public class QuoteOfTheDayVerticleTest {

  private static final int TEST_PORT = 8888;
  private static final String TEST_HOST = "localhost";

  private Vertx vertx = Vertx.vertx();
  private WebClient webClient = WebClient.create(vertx, new WebClientOptions().setDefaultPort(TEST_PORT));

  @Before
  public void setup(TestContext testContext) {
    JsonObject config = new JsonObject().put("http.port", TEST_PORT);
    DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle(new QuoteOfTheDayVerticle(), deploymentOptions, testContext.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext testContext) {
    vertx.close(testContext.asyncAssertSuccess());
  }

  @Test
  public void testGetQuotes(TestContext testContext) {
    webClient.get("/quotes")
      .as(BodyCodec.jsonArray())
      .send(testContext.asyncAssertSuccess(response -> {
        testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
        JsonArray quotes = response.body();
        testContext.assertFalse(quotes.isEmpty());
      }));
  }

  @Test
  public void testPostNewQuoteWithFullInformation(TestContext testContext) {
    JsonObject newQuoteWithFullInformation = new JsonObject()
      .put("author", "testAuthor1")
      .put("text", "testText1");

    webClient.post("/quotes")
      .putHeader("Content-Type", "application/json; charset=utf-8")
      .as(BodyCodec.jsonObject())
      .sendJsonObject(newQuoteWithFullInformation, testContext.asyncAssertSuccess(response -> {
        testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
        testContext.assertEquals(newQuoteWithFullInformation, response.body());
      }));
  }

  @Test
  public void testPostNewQuoteWithNoAuthor(TestContext testContext) {
    JsonObject newQuoteWithNoAuthor = new JsonObject()
      .put("text", "testText2");

    JsonObject expectedResponseBody = new JsonObject()
      .put("author", "Unknown")
      .put("text", "testText2");

    webClient.post("/quotes")
      .putHeader("Content-Type", "application/json; charset=utf-8")
      .as(BodyCodec.jsonObject())
      .sendJsonObject(newQuoteWithNoAuthor, testContext.asyncAssertSuccess(response -> {
        testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
        testContext.assertEquals(expectedResponseBody, response.body());
      }));
  }

  @Test
  public void testPostNewQuoteWithNoText(TestContext testContext) {
    JsonObject newQuoteWithNoText = new JsonObject()
      .put("author", "testAuthor3");

    webClient.post("/quotes")
      .putHeader("Content-Type", "application/json; charset=utf-8")
      .as(BodyCodec.jsonObject())
      .sendJsonObject(newQuoteWithNoText, testContext.asyncAssertSuccess(response -> {
        testContext.assertEquals(404, response.statusCode(), response.bodyAsString());
      }));
  }

  @Test
  public void testRealTimeQuoteNotification(TestContext testContext) throws InterruptedException {
    JsonObject newQuote = new JsonObject()
      .put("author", "testAuthor4")
      .put("text", "testText4");

    Async async1 = testContext.async();

    // Use CountDownLatch to make sure this test follow the steps:
    // 1.WebSocket connected --> 2. Post new quote --> 3. Receive real time push
    CountDownLatch connectLatch = new CountDownLatch(1);
    CountDownLatch postNewQuoteLatch = new CountDownLatch(1);

    HttpClient webSocketClient = vertx.createHttpClient();
    webSocketClient.websocket(TEST_PORT, TEST_HOST, "/realtime", webSocket -> {
      connectLatch.countDown();
      webSocket.handler(message -> {
        try {
          postNewQuoteLatch.await();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        JsonObject realtimeQuote = message.toJsonObject();

        testContext.assertEquals(newQuote, realtimeQuote);
        async1.complete();

      });
    });

    Async async2 = testContext.async();

    connectLatch.await();
    webClient.post("/quotes")
      .putHeader("Content-Type", "application/json; charset=utf-8")
      .as(BodyCodec.jsonObject())
      .sendJsonObject(newQuote, testContext.asyncAssertSuccess(response -> {
        testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
        testContext.assertEquals(newQuote, response.body());
        async2.complete();
        postNewQuoteLatch.countDown();
      }));
  }
}
