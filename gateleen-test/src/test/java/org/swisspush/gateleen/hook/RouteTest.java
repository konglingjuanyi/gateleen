package org.swisspush.gateleen.hook;

import com.jayway.restassured.RestAssured;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.swisspush.gateleen.AbstractTest;
import org.swisspush.gateleen.TestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.jayway.restassured.RestAssured.*;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * Test class for the hook route feature.
 * 
 * @author https://github.com/ljucam [Mario Ljuca]
 */
@RunWith(VertxUnitRunner.class)
public class RouteTest extends AbstractTest {
    private String requestUrlBase;
    private String targetUrlBase;

    /**
     * Overwrite RestAssured configuration
     */
    public void initRestAssured() {
        super.initRestAssured();
        RestAssured.requestSpecification.basePath(SERVER_ROOT);

        requestUrlBase = "/tests/gateleen/routesource";
        targetUrlBase = "http://localhost:" + MAIN_PORT + SERVER_ROOT + "/tests/gateleen/routetarget";
    }

    /**
     * Init the routing roules for the hooking.
     */
    private void initRoutingRules() {
        // add a routing
        JsonObject rules = new JsonObject();
        rules = TestUtils.addRoutingRuleMainStorage(rules);
        rules = TestUtils.addRoutingRuleHooks(rules);
        TestUtils.putRoutingRules(rules);
    }

    /**
     * Test for registration / unregistration. <br />
     */
    @Test
    public void testUnRegistration(TestContext context) {
        Async async = context.async();
        delete();
        initRoutingRules();

        // Settings
        String subresource = "registration";
        String routeName = "regtest";
        // -------

        String requestUrl = requestUrlBase + "/" + subresource + TestUtils.getHookRouteUrlSuffix();
        String target = targetUrlBase + "/" + routeName;
        String[] methods = new String[] { "GET", "PUT", "DELETE", "POST" };

        registerRoute(requestUrl, target, methods);
        unregisterRoute(requestUrl);

        async.complete();
    }

    /**
     * Tests if the staticHeaders are properly put as headers
     * to a routed request.
     *
     * @param context
     */
    @Test
    public void testRouteWithStaticHeaders(TestContext context) {
        Async async = context.async();
        delete();
        initRoutingRules();

        // Settings
        String subresource = "routeTest";
        // -------

        String requestUrl = requestUrlBase + "/" + subresource + TestUtils.getHookRouteUrlSuffix();
        String target = "http://localhost:" + MAIN_PORT + ROOT;
        String[] methods = new String[] { "GET", "PUT", "DELETE", "POST" };

        final String routedResource = requestUrlBase + "/" + subresource + "/debug";
        Map<String, String> staticHeaders = new LinkedHashMap<>();
        staticHeaders.put("x-test1", "1");
        staticHeaders.put("x-test2", "2");
        // -------

        // register route
        registerRoute(requestUrl, target, methods, staticHeaders);

        //
        String body = get(routedResource).getBody().asString();
        Assert.assertTrue(body.contains("x-test1"));
        Assert.assertTrue(body.contains("x-test2"));

        // unregister route
        unregisterRoute(requestUrl);

        async.complete();
    }



    /**
     * Test for create a route, and testing if requests
     * are rerouted to the new target.
     */
    @Test
    public void testRoute(TestContext context) {
        Async async = context.async();
        delete();
        initRoutingRules();

        // Settings
        String subresource = "routeTest";
        String routeName = "routeTest";
        // -------

        String requestUrl = requestUrlBase + "/" + subresource + TestUtils.getHookRouteUrlSuffix();
        String target = targetUrlBase + "/" + routeName;
        String[] methods = new String[] { "GET", "PUT", "DELETE", "POST" };

        // just for security reasons (unregister route)
        delete(requestUrl);

        // -------

        final String routedResource = requestUrlBase + "/" + subresource + "/test";
        final String checkTarget = targetUrlBase + "/" + routeName + "/test";

        /*
         * PUT something to the not yet routed resource.
         * -------
         */
        String body1 = "{ \"name\" : \"routeTest 1\"}";
        given().body(body1).put(routedResource).then().assertThat().statusCode(200);
        when().get(routedResource).then().assertThat().body(containsString(body1));
        when().get(checkTarget).then().assertThat().statusCode(404);
        delete(routedResource);

        // -------

        /*
         * Register a route
         * -------
         */
        registerRoute(requestUrl, target, methods);

        // -------

        /*
         * PUT something to the routed resource.
         * -------
         */
        String body2 = "{ \"name\" : \"routeTest 2\"}";
        given().body(body2).put(routedResource).then().assertThat().statusCode(200);
        when().get(routedResource).then().assertThat().body(containsString(body2));
        when().get(checkTarget).then().assertThat().body(containsString(body2));

        // -------

        /*
         * DELETE something from the routed resource.
         * -------
         */
        delete(routedResource).then().assertThat().statusCode(200);
        when().get(routedResource).then().assertThat().statusCode(404);
        when().get(checkTarget).then().assertThat().statusCode(404);

        // -------

        /*
         * Unregister a route
         * -------
         */
        unregisterRoute(requestUrl);

        // -------

        /*
         * PUT something to the no longer routed resource.
         * -------
         */
        String body3 = "{ \"name\" : \"routeTest 3\"}";
        given().body(body3).put(routedResource).then().assertThat().statusCode(200);
        when().get(routedResource).then().assertThat().body(containsString(body3));
        when().get(checkTarget).then().assertThat().statusCode(404);
        delete(routedResource);

        // -------
        async.complete();
    }

    /**
     * Registers a route.
     *
     * @param requestUrl
     * @param target
     * @param methods
     */
    private void registerRoute(final String requestUrl, final String target, String[] methods) {
        registerRoute(requestUrl, target, methods, null);
    }

    /**
     * Registers a route.
     *
     * @param requestUrl
     * @param target
     * @param methods
     * @param staticHeaders
     */
    private void registerRoute(final String requestUrl, final String target, String[] methods, Map<String, String> staticHeaders) {
        String body = "{ \"destination\":\"" + target + "\"";

        String m = null;
        if (methods != null) {
            for (String method : methods) {
                m += "\"" + method + "\", ";
            }
            m = m.endsWith(", ") ? m.substring(0, m.lastIndexOf(",")) : m;
            m = "\"methods\": [" + m + "]";
        }

        if ( staticHeaders != null && staticHeaders.size() > 0 ) {
            body = body + ", \"staticHeaders\" : {";

            boolean notFirst = false;
            for (Map.Entry<String, String> entry : staticHeaders.entrySet() ) {
                body = body + ( notFirst ? ", " : "" ) + "\"" + entry.getKey() + "\" : \"" + entry.getValue() + "\"";

                if ( ! notFirst ) {
                    notFirst = true;
                }
            }

            body = body + "}";
        }

        body = body + "}";

        with().body(body).put(requestUrl).then().assertThat().statusCode(200);
    }

    /**
     * Unregisters a route.
     *
     * @param request
     */
    private void unregisterRoute(String request) {
        delete(request).then().assertThat().statusCode(200);
    }
}
