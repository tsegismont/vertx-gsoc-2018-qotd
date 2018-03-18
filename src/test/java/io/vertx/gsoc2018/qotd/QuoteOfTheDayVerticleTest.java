package io.vertx.gsoc2018.qotd;

import static io.vertx.gsoc2018.qotd.QuoteOfTheDayVerticle.AUTHOR_FIELD;
import static io.vertx.gsoc2018.qotd.QuoteOfTheDayVerticle.AUTHOR_FIELD_DEFAULT_VALUE;
import static io.vertx.gsoc2018.qotd.QuoteOfTheDayVerticle.QUOTES_PATH;
import static io.vertx.gsoc2018.qotd.QuoteOfTheDayVerticle.TEXT_FIELD;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;

@RunWith(VertxUnitRunner.class)
public class QuoteOfTheDayVerticleTest {
  private static final int PORT = 8888;
  private static String SAMPLE_AUTHOR = "Random Lorem";

  private Vertx vertx = Vertx.vertx();
  private WebClient webClient =
    WebClient.create(vertx, new WebClientOptions().setDefaultPort(PORT));

  @Before
  public void setup(TestContext testContext) {
    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", PORT));
    vertx.deployVerticle(new QuoteOfTheDayVerticle(), deploymentOptions,
                         testContext.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext testContext) {
    vertx.close(testContext.asyncAssertSuccess());
  }

  @Test
  public void testGetQuotes(TestContext testContext) {
    webClient.get(QUOTES_PATH)
             .as(BodyCodec.jsonArray())
             .send(testContext.asyncAssertSuccess(response -> {
               testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
               JsonArray quotes = response.body();
               testContext.assertFalse(quotes.isEmpty());
             }));
  }

  @Test
  public void testPostNewQuoteWithoutAuthor(TestContext testContext) {

    JsonObject newQuoteWithNoAuthor = new JsonObject()
      .put(TEXT_FIELD, SAMPLE_AUTHOR);

    JsonObject expected = new JsonObject()
      .put(AUTHOR_FIELD, AUTHOR_FIELD_DEFAULT_VALUE)
      .put(TEXT_FIELD, SAMPLE_AUTHOR);

    webClient.post(QUOTES_PATH)
             .as(BodyCodec.jsonObject())
             .sendJsonObject(newQuoteWithNoAuthor, testContext.asyncAssertSuccess(response -> {
               testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
               testContext.assertEquals(expected, response.body());
             }));
  }

  @Test
  public void testPostNewQuoteWithoutText(TestContext testContext) {

    JsonObject newQuoteWithNoAuthor = new JsonObject()
      .put(AUTHOR_FIELD, SAMPLE_AUTHOR);

    webClient.post(QUOTES_PATH)
             .as(BodyCodec.jsonObject())
             .sendJsonObject(newQuoteWithNoAuthor, testContext.asyncAssertSuccess(
               response -> testContext.assertEquals(400, response.statusCode())));
  }

  public void testPostQuoteSuccess(TestContext testContext, String text, String author) {
    JsonObject quote = new JsonObject()
      .put(TEXT_FIELD, text)
      .put(AUTHOR_FIELD, author);

    Async async = testContext.async();

    webClient.post(QUOTES_PATH)
             .as(BodyCodec.jsonObject())
             .sendJsonObject(quote, testContext.asyncAssertSuccess(response -> {
               testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
               async.countDown();
             }));

    //    async.await();
  }

}
