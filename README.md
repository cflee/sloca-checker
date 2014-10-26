# sloca-checker

Very basic, very limited checker for SLOCA's JSON web services.

## Overview

This checker takes in an input file that specifies configuration, and a set of tests to be run. It executes a POST or GET request to the specified endpoint, at the specified base URL, and compares the actual JSON output result with the expected output supplied in the test.

A full and exact match (of the entire JSON object) is required to pass each test. Order of elements in JSONArrays are important, while order of elements in JSONObjects are ignored.

is203-jwt-v2.jar is integrated to allow testing of services even when the authenticate service is not working properly. Tokens are automatically generated for requests, unless otherwise specified.

All requests are GET requests, unless otherwise specified.

## Input file format

Only JSON files are accepted for input at the moment.

The file consists of an array of objects.

### Config object

The first object is special, as it specifies general configuration parameters for the test suite. They are defined as follows:

Key             | Description       | Default value
----------------|-------------------|---------------
baseUrl         | Base URL, that should start with "http://" and end with a trailing slash.     | "http://localhost:8084/json/"
secret          | Secret that you use to sign and verify tokens. | "abcdefghijklmnop"
adminUsername   | Username that you expect for your web services. | "admin"

Sample config object:

```
{
    "baseUrl": "http://localhost:8084/json/",
    "secret": "abcdefghijklmnop",
    "adminUsername": "admin"
}
```

### Test object

Subsequent objects are all treated as test objects. They are defined as follows:

Key             | Description       | Required?
----------------|-------------------|---------------
endpoint        | String that should be appended to the Base URL | mandatory
description     | String description of the test, to identify it | mandatory
result          | JSON Object that is the expected result | mandatory
authenticate    | Boolean, true if token should be generated and sent, false otherwise | optional, default true
post            | Boolean, true if request method should be POST, false if request should be GET | optional, default false (GET)

All other keys in the objects are treated as parameters for the POST or GET request. Values may be any valid JSON value, but they will be converted to String before being sent in the HTTP request.

Sample POST request:

```
{
    "endpoint": "authenticate",
    "authenticate": false,
    "description": "authenticate missing password",
    "post": true,
    "result": {
        "status": "error",
        "messages": ["missing password"]
    },
    "username": "admin"
}
```

Note that it not only disables the token sending by setting `authenticate` to `false`, but also sets `post` to true.

Sample GET request without token:

```
{
    "endpoint": "heatmap",
    "description": "heatmap missing token",
    "result": {
        "status": "error",
        "messages": ["missing token"]
    },
    "authenticate": false,
    "date": "2014-01-01T00:00:00",
    "floor": 1
}
```

By setting `authenticate` to `false`, the automatically generated token is suppressed. If you don't provide a `token` key later, there won't be such a key sent entirely.

Sample GET request with token:

```
{
    "endpoint": "heatmap",
    "description": "heatmap blank token",
    "result": {
        "status": "error",
        "messages": ["blank token"]
    },
    "token": "",
    "date": "2014-01-01T00:00:00",
    "floor": 1
}
```

Notice that even though `authenticate` is not set (and therefore at the default value of `true`), you can still override `token` field by specifying it. Remember, ordering within an JSON object is not significant, this sample's order is just specified for easy reading.

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

## Dependencies

Libraries are checked in for your convenience, until I figure out how to switch to using Maven dependencies to fetch and keep these up to date.

* [JSONassert](http://jsonassert.skyscreamer.org/) ([github](https://github.com/skyscreamer/jsonassert)) is used to compare JSONObjects. It relies on the `org.json` package, which can only be used for Good, and not Evil
* [Apache HttpComponents](http://hc.apache.org/) `HttpClient` as the HTTP agent
* [Nimbus JOSE + JWT](http://connect2id.com/products/nimbus-jose-jwt) as used in `is203-jwt-v2.jar`, which relies on [json-smart](https://code.google.com/p/json-smart/)

