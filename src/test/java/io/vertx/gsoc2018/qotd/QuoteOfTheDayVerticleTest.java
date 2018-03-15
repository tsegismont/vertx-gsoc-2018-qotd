package io.vertx.gsoc2018.qotd;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
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

/**
 * @author Thomas Segismont
 */
@RunWith(VertxUnitRunner.class)
public class QuoteOfTheDayVerticleTest {

  private static final int PORT = 8888;
  private static final String HOST = "0.0.0.0";

  private Vertx vertx;
  private WebClient webClient;
  private static final String DEFAULT_AUTHOR_VALUE = "Unknown";
  private static String SAMPLE_QUOTE_TEXT = "What I did not know was I was deeply attracted to the big space.";
  private static String SAMPLE_AUTHOR_NAME = "David Hockney";

  @Before
  public void setup(TestContext testContext) {
    vertx = Vertx.vertx();
    webClient = WebClient.create(vertx, new WebClientOptions().setDefaultPort(PORT));
    JsonObject config = new JsonObject().put("http.port", PORT);
    DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle(new QuoteOfTheDayVerticle(), deploymentOptions, testContext.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext testContext) {
    webClient.close();
    vertx.close(testContext.asyncAssertSuccess());
  }

  @Test
  public void testRealtimeWebSocket(TestContext testContext) {

    Async async = testContext.async();
    Async connected = testContext.async();
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.websocket(PORT, HOST, "/realtime", ws -> {
      connected.countDown();
      ws.handler(data -> {
        JsonObject json = data.toJsonObject();
        testContext.assertEquals(json.getString("author"), SAMPLE_AUTHOR_NAME);
        testContext.assertEquals(json.getString("text"), SAMPLE_QUOTE_TEXT);
        async.countDown();
      });
    });

    connected.await();
    // force receiving a update
    postQuote(SAMPLE_AUTHOR_NAME, SAMPLE_QUOTE_TEXT, testContext);
    async.await(5000);
    httpClient.close();
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
  public void testPostQuotes(TestContext testContext) {
    int currentAmountOfQuotes = getCurrentQuotes(testContext).size();
    postQuote(SAMPLE_AUTHOR_NAME, SAMPLE_QUOTE_TEXT, testContext);
    testContext.assertTrue(getCurrentQuotes(testContext).size() == currentAmountOfQuotes + 1);
  }

  @Test
  public void testPostQuotesWithoutAuthorName(TestContext testContext) {

    JsonObject quote = new JsonObject()
      .put("text", SAMPLE_QUOTE_TEXT);

    Async async = testContext.async();

    webClient.post("/quotes")
      .as(BodyCodec.none())
      .sendJsonObject(quote, testContext.asyncAssertSuccess(response -> {
        testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
        async.countDown();
      }));

    async.await();

    testContext.assertTrue(getCurrentQuotes(testContext).stream()
      .map(element -> (JsonObject) element)
      .anyMatch(element ->
        element.getString("author").equals(DEFAULT_AUTHOR_VALUE) &&
          element.getString("text").equals(SAMPLE_QUOTE_TEXT)
      )
    );
  }

  @Test
  public void testPostQuotesWithoutText(TestContext testContext) {
    JsonObject quote = new JsonObject()
      .put("author", SAMPLE_AUTHOR_NAME);

    webClient.post("/quotes")
      .as(BodyCodec.none())
      .sendJsonObject(quote, testContext.asyncAssertSuccess(response -> {
        testContext.assertEquals(404, response.statusCode(), response.bodyAsString());
      }));
  }

  private JsonArray getCurrentQuotes(TestContext testContext) {
    Async async = testContext.async();
    Future<JsonArray> quotes = Future.future();
    webClient.get("/quotes")
      .as(BodyCodec.jsonArray())
      .send(testContext.asyncAssertSuccess(response -> {
        testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
        quotes.complete(response.body());
        async.countDown();
      }));
    async.await();
    return quotes.result();
  }


  private void postQuote(String author, String text, TestContext testContext) {
    JsonObject quote = new JsonObject()
      .put("text", text)
      .put("author", author);

    Async async = testContext.async();

    webClient.post("/quotes")
      .as(BodyCodec.none())
      .sendJsonObject(quote, testContext.asyncAssertSuccess(response -> {
        testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
        async.countDown();
      }));

    async.await();
  }
}
