package io.vertx.gsoc2018.qotd;

import static io.vertx.gsoc2018.qotd.QuoteOfTheDayVerticle.QUOTES_PATH;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;

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
    webClient.get(QUOTES_PATH)
             .as(BodyCodec.jsonArray())
             .send(testContext.asyncAssertSuccess(response -> {
          testContext.assertEquals(200, response.statusCode(), response.bodyAsString());
          JsonArray quotes = response.body();
          testContext.assertFalse(quotes.isEmpty());
        }));
  }
}
