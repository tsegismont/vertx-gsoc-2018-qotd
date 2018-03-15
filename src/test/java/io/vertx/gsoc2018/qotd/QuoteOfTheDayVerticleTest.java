package io.vertx.gsoc2018.qotd;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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

  private Vertx vertx = Vertx.vertx();
  private WebClient webClient = WebClient.create(vertx, new WebClientOptions().setDefaultPort(PORT));

  @Before
  public void setup(TestContext testContext) {
    JsonObject config = new JsonObject().put("http.port", PORT);
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
}