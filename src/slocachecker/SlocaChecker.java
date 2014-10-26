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
            settings.put("baseUrl", "https://localhost:8084/json/");
            settings.put("secret", "abcdefghijklmnop");
            settings.put("adminUsername", "admin");

            // look for the settings in the first object. if found, overwrite in our settings map
            JSONObject inputFileConfigObject = inputFileJsonArray.getJSONObject(0);
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                if (!inputFileConfigObject.isNull(entry.getKey())) {
                    settings.put(entry.getKey(), inputFileConfigObject.getString(entry.getKey()));
                }
            }

            System.out.println("Total number of tests to run: " + (inputFileJsonArray.length() - 1));
            int numTestsPassed = 0;

            for (int i = 1; i < inputFileJsonArray.length(); i++) {
                JSONObject testData = inputFileJsonArray.getJSONObject(i);

                String description = null;
                String endpoint = null;
                boolean needsAuthentication = false;
                boolean isPost = false;
                JSONObject expectedResult = null;

                String generatedToken = null;
                String returnedResponse = null;

                try {
                    description = testData.getString("description");
                    endpoint = testData.getString("endpoint");
                    needsAuthentication = testData.optBoolean("authenticate", true);
                    isPost = testData.optBoolean("post");
                    expectedResult = testData.getJSONObject("result");

                    testData.remove("description");
                    testData.remove("endpoint");
                    testData.remove("authenticate");
                    testData.remove("post");
                    testData.remove("result");
                } catch (JSONException e) {
                    System.out.println("Test #" + i + " - ERROR - Can't test, missing mandatory attribute(s)");
                    System.out.println("    Exception: " + e.getMessage());
                    continue;
                }

                if (needsAuthentication) {
                    generatedToken = JWTUtility.sign(settings.get("secret"), settings.get("adminUsername"));
                }

                if (isPost) {
                    // perform a POST

                    Form form = Form.form();
                    // put in the token first, so that it can be overrided by the test file later
                    if (needsAuthentication) {
                        form.add("token", generatedToken);
                    }
                    // grab all remaining key/value pairs and add to the "form"
                    for (String key : (Set<String>) testData.keySet()) {
                        form.add(key, testData.getString(key));
                    }

                    // send HTTP request
                    returnedResponse = Request.Post(settings.get("baseUrl") + endpoint)
                            .socketTimeout(240 * 1000)
                            .bodyForm(form.build())
                            .execute().returnContent().asString();
                } else {
                    try {
                        // perform a GET

                        URIBuilder uriBuilder = new URIBuilder(settings.get("baseUrl") + endpoint);

                        // put in the token first, so that it can be overrided by the test file later
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
                    } catch (URISyntaxException ex) {
                        Logger.getLogger(SlocaChecker.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                if (returnedResponse == null) {
                    System.out.println("    Unexpected error! No response to process.");
                } else {
                    JSONObject actualResult = new JSONObject(returnedResponse);

                    try {
                        JSONAssert.assertEquals(expectedResult.toString(), returnedResponse, true);

                        System.out.println("Test #" + i + " - PASS - " + description);
                        numTestsPassed++;
                    } catch (AssertionError e) {
                        System.out.println("Test #" + i + " - FAIL - " + description);
                        System.out.println("    Expected result: " + expectedResult);
                        System.out.println("    Actual   result: " + actualResult);
                    }
                }
            }

            System.out.println();
            System.out.println("Overall " + numTestsPassed + "/" + (inputFileJsonArray.length() - 1) + " tests passed.");
        } catch (IOException ex) {
            System.out.println("Error! Could not read from specified file.");
            Logger.getLogger(SlocaChecker.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
