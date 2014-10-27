package slocachecker;

import is203.JWTUtility;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONAssert;

public class SlocaChecker {

    public static void main(String[] args) {
        try {
            // check for minimum number of arguments, fail fast if missing
            if (args.length < 1) {
                System.out.println("Usage:");
                System.out.println("java SlocaChecker <input filename>");
                return;
            }

            // read all lines from file and concat into a string for JSONArray constructor parser
            StringBuilder inputFileSB = new StringBuilder();
            for (String line : Files.readAllLines(Paths.get(args[0]))) {
                inputFileSB.append(line);
            }
            JSONArray inputFileJsonArray = new JSONArray(inputFileSB.toString());

            // setup the default settings Map
            Map<String, String> settings = new HashMap<>();
            settings.put("baseUrl", "http://localhost:8084/json/");
            settings.put("secret", "abcdefghijklmnop");
            settings.put("adminUsername", "admin");

            // look for the settings in the first object. if found, overwrite in our settings map
            JSONObject inputFileConfigObject = inputFileJsonArray.getJSONObject(0);
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                if (inputFileConfigObject.has(entry.getKey())) {
                    settings.put(entry.getKey(), inputFileConfigObject.getString(entry.getKey()));
                }
            }

            // TOOD: add some validation of the user-specified settings here (eg baseUrl endsWith /)
            // keep track of the total number of tests to be run, and tests passed
            System.out.println("Total number of tests to run: " + (inputFileJsonArray.length() - 1));
            System.out.println();
            int numTestsPassed = 0;

            // iterate through all the Test objects, skipping the Config object
            for (int t = 1; t < inputFileJsonArray.length(); t++) {
                // retrieve the Test object
                JSONObject testData = inputFileJsonArray.getJSONObject(t);

                // extract data from the Test object
                String description = null;
                String endpoint = null;
                boolean needsAuthentication = false;
                boolean isPost = false;

                JSONArray checks = null;

                String generatedToken = null;
                String returnedResponse = null;

                try {
                    // retrieve all the test-specific parameters that we recognise
                    // mandatory keys
                    description = testData.getString("description");
                    endpoint = testData.getString("endpoint");
                    // optional keys
                    needsAuthentication = testData.optBoolean("authenticate", true);
                    isPost = testData.optBoolean("post");
                    checks = testData.getJSONArray("checks");

                    // remove them from the JSONObject, to avoid them being placed
                    // into the GET or POST request later
                    testData.remove("description");
                    testData.remove("endpoint");
                    testData.remove("authenticate");
                    testData.remove("post");
                    testData.remove("checks");
                } catch (JSONException e) {
                    printException(t, description, "Can't test, missing mandatory attribute(s)", e);
                    continue;
                }

                // centralise the token generation here
                if (needsAuthentication) {
                    generatedToken = JWTUtility.sign(settings.get("secret"), settings.get("adminUsername"));
                }

                // send the request for this test, obtain result
                try {
                    if (isPost) {
                        // perform a POST request
                        // figure out the parameters to be sent as POST payload
                        // put in the token first, so that it can be overrided by the test file later
                        Form form = Form.form();
                        if (needsAuthentication) {
                            form.add("token", generatedToken);
                        }
                        // grab all remaining key/value pairs and add to the "form"
                        for (String key : (Set<String>) testData.keySet()) {
                            form.add(key, testData.getString(key));
                        }

                        // send HTTP POST request
                        returnedResponse = Request.Post(settings.get("baseUrl") + endpoint)
                                .socketTimeout(240 * 1000)
                                .bodyForm(form.build())
                                .execute().returnContent().asString();
                    } else {
                        // perform a GET request
                        // figure out the parameters to go into the query string
                        // put in the token first, so that it can be overrided by the test file later
                        URIBuilder uriBuilder = new URIBuilder(settings.get("baseUrl") + endpoint);
                        if (needsAuthentication) {
                            uriBuilder.setParameter("token", generatedToken);
                        }
                        // grab all remaining key/value pairs and add to the URI
                        for (String key : (Set<String>) testData.keySet()) {
                            uriBuilder.setParameter(key, "" + testData.get(key));
                        }

                        // send HTTP request
                        returnedResponse = Request.Get(uriBuilder.build())
                                .socketTimeout(240 * 1000)
                                .execute().returnContent().asString();
                    }
                } catch (IOException | IllegalArgumentException e) {
                    // HTTP client couldn't connect or send for whatever reason
                    printException(t, description, "Could not connect to the web service!", e);
                } catch (URISyntaxException e) {
                    // problem with constructing the URIBuilder
                    // (this is the Apache HttpComponents URIBuilder, not Java EE)
                    printException(t, description, "There was a problem with the baseUrl/endpoint!", e);
                }

                // process checks against the result
                if (returnedResponse != null) {
                    JSONObject actualResult = new JSONObject(returnedResponse);

                    for (int c = 0; c < checks.length(); c++) {
                        JSONObject check = checks.getJSONObject(c);

                        if (check.getString("type").equals("exact")) {
                            JSONObject expectedResult = check.getJSONObject("value");

                            try {
                                JSONAssert.assertEquals(expectedResult.toString(), returnedResponse, true);

                                System.out.println("Test #" + t + "-" + (c + 1) + " - PASS - " + description);
                                numTestsPassed++;
                            } catch (AssertionError e) {
                                System.out.println("Test #" + t + "-" + (c + 1) + " - FAIL - " + description);
                                System.out.print("    Assertion details:");
                                // don't print a newline for previous line as there will be one
                                // extra from the following replaceAll to indent message by 4 spaces
                                System.out.println(e.getMessage().replaceAll("^|\r\n|\n", "\r\n    "));
                                System.out.println("    Checker   details:");
                                System.out.println("    EXPECTED result: " + expectedResult);
                                System.out.println("    ACTUAL   result: " + actualResult);
                            }
                        }
                        // TODO: other types of checks go here
                    }
                }
            }

            System.out.println();
            System.out.println("Overall " + numTestsPassed + "/" + (inputFileJsonArray.length() - 1)
                    + " tests passed.");
        } catch (IOException ex) {
            System.out.println("Error! Could not read from specified file.");
            Logger.getLogger(SlocaChecker.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Perform an 'exact' type check between two JSONObjects, printing the result to the console. Note that this method
     * both prints output to the console as well as returns a boolean value! It is a little strange for a non-void
     * return type method.
     *
     * @param testNo Test # (should start from 1)
     * @param checkNo Check # (should start from 1 if from Check object, 0 from result)
     * @param testDescription test description
     * @param expected expected JSONObject
     * @param actual actual JSONObject
     * @return true if passed check, false otherwise
     */
    private static boolean performExactCheck(int testNo, int checkNo, String testDescription,
            JSONObject expected, JSONObject actual) {
        try {
            JSONAssert.assertEquals(expected.toString(), actual, true);

            System.out.println("Test #" + testNo + "-" + checkNo + " - PASS - " + testDescription);
            return true;
        } catch (AssertionError e) {
            System.out.println("Test #" + testNo + "-" + checkNo + " - FAIL - " + testDescription);
            System.out.print("    Assertion details:");
            // don't print a newline for previous line as there will be one
            // extra from the following replaceAll to indent message by 4 spaces
            System.out.println(e.getMessage().replaceAll("^|\r\n|\n", "\r\n    "));
            System.out.println("    Checker   details:");
            System.out.println("    EXPECTED result: " + expected);
            System.out.println("    ACTUAL   result: " + actual);
        }
        return false;
    }

    private static void printException(int testNo, String testDescription, String message, Exception e) {
        System.out.println("Test #" + testNo + " - ERROR - " + testDescription);
        System.out.print("    " + message);
        if (e.getMessage() != null) {
            System.out.println(e.getMessage().replaceAll("^|\r\n|\n", "\r\n    "));
        } else {
            // send a newline for after the message
            System.out.println(("Exception class: " + e.toString())
                    .replaceAll("^|\r\n|\n", "\r\n    "));
        }
    }

}
