# sloca-checker

Very basic, very limited checker for SLOCA's JSON web services.

## Overview

This checker takes in an JSON input file that specifies configuration, and a set of tests to be run. For each test, it executes a POST or GET request to the specified endpoint, at the specified base URL, and runs one or more checks on the response.

`is203-jwt-v2.jar` is integrated to allow testing of services even when the web service's authenticate service is not working properly. Tokens are automatically generated for requests, unless otherwise specified.

All requests are GET requests, unless otherwise specified.

There are two 'modes' of usage, as decided by how you choose to write your JSON input files. In the _simple_ mode, the functionality is similar to the official jsonchecker, in that it makes a single web service request, and just compares entire JSON objects. In the _complex_ (or advanced) mode, the file format allows specifying multiple and advanced checks to be run on a single web service request.

## Input file format

Only JSON files are accepted for input at the moment.

The file consists of an array of objects, starting with the Config object, followed by one or more Test objects. The Config object must be the first object in the array.

In the _simple_ mode, the Test objects only contain a single expected `result`. In the _complex_ mode, the Test objects contain multiple Check objects that specify the checks to be run against the response.

All specified object keys are **case-sensitive**.

### Config object

The first object is special, as it specifies general configuration parameters for the test suite. They are defined as follows:

Key             | Description       | Default value
----------------|-------------------|---------------
baseUrl         | Base URL, that should start with "http://" and end with a trailing slash     | "http://localhost:8084/json/"
secret          | Secret that your application uses to sign and verify tokens | "abcdefghijklmnop"
adminUsername   | Username to be used when signing tokens | "admin"

All parameters are case-sensitive, apart from the protocol (`http`) and the hostname (`localhost`).

Sample config object:

```
{
    "baseUrl":"http://localhost:8084/json/",
    "secret":"abcdefghijklmnop",
    "adminUsername":"admin"
}
```

### Test object

Subsequent objects are all treated as test objects. They are defined as follows:

Key             | Description       | Required?
----------------|-------------------|---------------
endpoint        | String that should be appended to the Base URL | mandatory
description     | String description of the test, used only for output | mandatory
result          | JSON object that is the expected result | mandatory if `checks` is not specified
checks          | JSON array of Check objects | mandatory if `result` is not specified
authenticate    | Boolean: true if token should be generated and sent, false otherwise | optional, default true (send token)
post            | Boolean: true if request method should be POST, false if request should be GET | optional, default false (GET)

**At least one** of `result` or `checks` keys must be provided.

Note that the `result` and `checks` keys are not mutually exclusive. You may include both, and the `result` value will be taken as check #0 with 'exact' check type.

All other keys in the objects are treated as parameters for the POST or GET request. Values may be any valid JSON value, but they will be converted to String before being sent in the HTTP request.

Handling of JSON arrays as a URL value is not defined, `["year","gender"]` will likely end up exactly like that instead of `year,gender`, so specify that exact String instead.

**Sample POST request:**

```
{
    "endpoint":"authenticate",
    "authenticate":false,
    "description":"authenticate missing password",
    "post":true,
    "result":{
        ... elided ...
    },
    "username":"admin"
}
```

Note that `post` is set to `true`. By default, requests are made using the GET method.

**Sample GET request without token:**

```
{
    "endpoint":"heatmap",
    "description":"heatmap missing token",
    "result":{
        ... elided ...
    },
    "authenticate":false,
    "date":"2014-01-01T00:00:00",
    "floor":1
}
```

By setting `authenticate` to `false`, the automatically generated token is suppressed. If you don't provide a `token` key later, there won't be such a key sent entirely.

**Sample GET request with static token:**

```
{
    "endpoint":"heatmap",
    "description":"heatmap blank token",
    "result":{
        ... elided ...
    },
    "token":"",
    "date":"2014-01-01T00:00:00",
    "floor":1
}
```

Even though `authenticate` is not set (and therefore at the default value of `true`, so a token is generated for you), you can still override `token` field by specifying it.

**Using the complex mode:**

```
{
    "endpoint":"heatmap",
    "description":"heatmap blank token",
    "checks":[
        ... elided ...
    ],
    "token":"",
    "date":"2014-01-01T00:00:00",
    "floor":1
}
```

Just add a `checks` array of Check objects (as specified below) to the Test object.

### Check object

Each test object above corresponds to one request/response made to the web service. In anticipation of multiple types of checks being made available, as well as flexibility in allowing multiple checks to be made on the same response, the `checks` attribute in the Test object is an array of Check objects.

Each check object is defined as follows:

Key             | Description       | Required?
----------------|-------------------|---------------
type            | String, specifies the check type | mandatory
key             | Reserved for future use | optional, depends on check type
value           | Varies with check type | mandatory

For now, only the `exact` check type is available.

#### exact check

Key             | Description
----------------|-------------------
type            | 'exact'
key             | ignored, may be omitted
value           | JSON object that is the expected result

A full and exact match (of the entire JSON object) is required to pass. Criteria:

* Order of elements in JSON arrays is significant
* Order of elements in JSON objects is ignored
* Extra, unexpected elements in JSON objects are rejected
* All keys and values are case-sensitive

**Sample check object:**

```
{
    "type":"exact",
    "key":"sample",
    "value":{
        "status":"error",
        "messages":[
            "missing password"
        ]
    }
}
```

Upon failure, this check type outputs the expected and actual JSON objects.

**Sample output:**

```
Test #13-1 - FAIL - heatmap invalid floor non-numerical
    Assertion details:
    messages[0]
    Expected: invalid floor
         got: invalid token
     ; status
    Expected: success
         got: error

    Checker   details:
    EXPECTED result: {"messages":["invalid floor"],"status":"success"}
    ACTUAL   result: {"messages":["invalid token"],"status":"error"}
```

There are several parts to this.

First, there is the checker's standard line of test and check number, status, and description.

Next, there is the message from the JSON object checker, that details the *differences* between what was expected and actually received. This output may be a little cryptic, so it is more useful just to identify which part of a long response is different.

This is followed by the full result that was expected and actually received, for make benefit easy troubleshooting.

## Output format

Test results are output to the console.

At least one line is output per test and check. Each line specifies the test object number (starting from 1), followed by a dash, and the check object number (starting from 1). The `PASS` or `FAIL` result is then specified, followed by the test's description.

More details may follow in the case of a `FAIL` result, as specified per check type above.

Nonetheless, when investigating test results, it is likely easier to locate test objects by the (mandatory) description string, then look for the corresponding check object.

**Sample output in simple mode:**

```
Total number of tests to run: 2
Test #1-0 - PASS - authenticate missing username and password
Test #2-0 - PASS - authenticate missing password

Overall 2/2 tests passed.
```

**Sample output in complex mode:**

```
Total number of tests to run: 2
Test #1-1 - PASS - authenticate missing username and password
Test #2-1 - PASS - authenticate missing password

Overall 2/2 tests passed.
```

## Limitations

There are quite a lot of limitations. Here are some of them. Please feel free to send pull requests!

* Unable to POST files for bootstrap
* Unable to do 'partial' or 'wildcard' actual/expected result comparisons
* Unable to do 'size of array' assertions, or assertions on specific values
* JSON input file has many `{}`, should allow YAML 1.2 input too since it's a superset of JSON
* Ugly output on the console, no alternate output options
* Uses douglascrockford's reference `org.json` package, with the [bad license](http://tanguy.ortolo.eu/blog/article46/json-license), could be replaced with the [AOSP's clean-room implementation](https://android.googlesource.com/platform/libcore/+/ics-plus-aosp/json?autodive=0)

Depending on how you write your test cases, it may not be very good for running very specific, isolating tests. Nonetheless, it should be good enough for testing the web service input validation at the very least. If you write your test cases such that the output is fully specified, this might be very useful too.

If you have multiple sets of data for your test cases, you may wish to create multiple input files, one for each set of data that you manually bootstrap before executing the test.

## Usage
You will need to compile and run it with the dependencies in `/lib/`.

Place the input JSON file in the your working directory. Run the `slocachecker.SlocaChecker` class with a single argument, the input file's filename.

See the included `test-sample.json` for a complete example.

You should be able to run these tests against your OpenShift gear, but keep in mind the 4-minute connection timeout by their reverse proxy. The connection timeout in this application's HTTP client has been raised to 4 minutes.

## Dependencies

Libraries are checked in for your convenience, until I figure out how to switch to using Maven dependencies to fetch and keep these up to date.

* [JSONassert](http://jsonassert.skyscreamer.org/) ([github](https://github.com/skyscreamer/jsonassert)) is used to compare JSONObjects. It relies on the `org.json` package, which can only be used for Good, and not Evil
* [Apache HttpComponents](http://hc.apache.org/) `HttpClient` as the HTTP agent
* [Nimbus JOSE + JWT](http://connect2id.com/products/nimbus-jose-jwt) as used in `is203-jwt-v2.jar`, which relies on [json-smart](https://code.google.com/p/json-smart/)

## Credits

Inspired by [manuskc/API_Tester](https://github.com/manuskc/API_Tester)'s usage of a JSON file as input.

## License

Released under a MIT license. See the `LICENSE` file for details.
