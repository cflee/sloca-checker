# sloca-checker

Very basic, very limited checker for SLOCA's JSON web services.

## Overview

This checker takes in an JSON input file that specifies configuration, and a set of tests to be run. For each test, it executes a POST or GET request to the specified endpoint, at the specified base URL, and runs one or more checks on the response.

`is203-jwt-v2.jar` is integrated to allow testing of services even when the web service's authenticate service is not working properly. Tokens are automatically generated for requests, unless otherwise specified.

All requests are GET requests, unless otherwise specified.

## Input file format

Only JSON files are accepted for input at the moment.

The file consists of an array of objects, starting with the Config object, followed by one or more Test objects.

All specified object keys are **case-sensitive**.

### Config object

The first object is special, as it specifies general configuration parameters for the test suite. They are defined as follows:

Key             | Description       | Default value
----------------|-------------------|---------------
baseUrl         | Base URL, that should start with "http://" and end with a trailing slash     | "http://localhost:8084/json/"
secret          | Secret that your application uses to sign and verify tokens | "abcdefghijklmnop"
adminUsername   | Username to be used when signing tokens | "admin"

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
result          | JSON object that is the expected result | mandatory
checks          | JSON array of Check objects | mandatory
authenticate    | Boolean: true if token should be generated and sent, false otherwise | optional, default true (send token)
post            | Boolean: true if request method should be POST, false if request should be GET | optional, default false (GET)

All other keys in the objects are treated as parameters for the POST or GET request. Values may be any valid JSON value, but they will be converted to String before being sent in the HTTP request.

*Sample POST request:*

```
{
    "endpoint":"authenticate",
    "authenticate":false,
    "description":"authenticate missing password",
    "post":true,
    "checks":[
        ... elided ...
    ],
    "username":"admin"
}
```

Note that it not only disables the token sending by setting `authenticate` to `false`, but also sets `post` to true.

*Sample GET request without token:*

```
{
    "endpoint":"heatmap",
    "description":"heatmap missing token",
    "checks":[
        ... elided ...
    ],
    "authenticate":false,
    "date":"2014-01-01T00:00:00",
    "floor":1
}
```

By setting `authenticate` to `false`, the automatically generated token is suppressed. If you don't provide a `token` key later, there won't be such a key sent entirely.

*Sample GET request with static token:*

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

Notice that even though `authenticate` is not set (and therefore at the default value of `true`), you can still override `token` field by specifying it. Remember, ordering within an JSON object is not significant, this sample's order is just specified for easy reading.

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

Sample check object:

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

Sample output:

```
Test #13-1 - FAIL - heatmap invalid floor non-numerical
    Assertion error:
    messages[0]
    Expected: invalid floor
         got: invalid token
     ; status
    Expected: success
         got: error

    Checker   error:
    EXPECTED result: {"messages":["invalid floor"],"status":"success"}
    ACTUAL   result: {"messages":["invalid token"],"status":"error"}
```

There are several parts to this.

First, there is the checker's standard line of test and check number, status, and description.

Next, there is the message from the JSON object checker, that details the **differences** between what was expected and actually received.

This is followed by the full result that was expected and actually received, to easy troubleshooting.

## Output format

Test results are output to the console.

At least one line is output per test and check. Each line specifies the test object number (starting from 1), followed by a dash, and the check object number (starting from 1). The `PASS` or `FAIL` result is then specified, followed by the test's description.

More details may follow in the case of a `FAIL` result, as specified per check type above.

Nonetheless, when investigating test results, it is likely easier to locate test objects by the (mandatory) description string, then look for the corresponding check object.

Sample output:

```
Total number of tests to run: 13
Test #1-1 - PASS - authenticate missing username and password
Test #2-1 - PASS - authenticate missing password

Overall 2/2 tests passed.
```

## Limitations

There are quite a lot of limitations. Here are some of them. Please feel free to send pull requests!

* Unable to do 'partial' or 'wildcard' actual/expected result comparisons
* Unable to do 'size of array' assertions, or only specific values'
* Unable to POST files for bootstrap
* JSON input file has many `{}`, should allow YAML 1.2 input too since it's a superset of JSON
* Ugly output on the console, no alternate output options
* Uses douglascrockford's reference `org.json` package, with the [bad license](http://tanguy.ortolo.eu/blog/article46/json-license), could be replaced with the [AOSP's clean-room implementation](https://android.googlesource.com/platform/libcore/+/ics-plus-aosp/json?autodive=0)

While it is not very good for running very specific, isolating tests, it should be good enough for testing the web service input validation at the very least.

If you have multiple sets of data for your test cases, you may wish to create multiple input files, one for each set of data that you (manually) bootstrap.

## Usage
You will need to compile and run it with the dependencies in `/lib/`.

Place the input JSON file in the your working directory. Run the `slocachecker.SlocaChecker` class with a single argument, the input file's filename.

See the included `test-sample.json` for a complete example.

## Dependencies

Libraries are checked in for your convenience, until I figure out how to switch to using Maven dependencies to fetch and keep these up to date.

* [JSONassert](http://jsonassert.skyscreamer.org/) ([github](https://github.com/skyscreamer/jsonassert)) is used to compare JSONObjects. It relies on the `org.json` package, which can only be used for Good, and not Evil
* [Apache HttpComponents](http://hc.apache.org/) `HttpClient` as the HTTP agent
* [Nimbus JOSE + JWT](http://connect2id.com/products/nimbus-jose-jwt) as used in `is203-jwt-v2.jar`, which relies on [json-smart](https://code.google.com/p/json-smart/)

Inspired by [manuskc/API_Tester](https://github.com/manuskc/API_Tester)'s usage of a JSON file as input.
