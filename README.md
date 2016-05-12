# Pension Lifetime Allowance (PLA) Dynamic Stub

[![Apache-2.0 license](http://img.shields.io/badge/license-Apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html) [![Build Status](https://travis-ci.org/hmrc/pla-dynamic-stub.svg?branch=master)](https://travis-ci.org/hmrc/pla-dynamic-stub) [ ![Download](https://api.bintray.com/packages/hmrc/releases/pla-dynamic-stub/images/download.svg) ](https://bintray.com/hmrc/releases/pla-dynamic-stub/_latestVersion)

This is a stub for the PLA service. The stub is a test double that supports the PLA service REST API in development or test environments, this enables testing of clients of the service without requiring a full end-to-end test environment that has all the backend services and systems available.

The stub is a Play/Scala application backed by a Mongo database for the test data, which is dynamically created (hence it is termed a dynamic stub, because it does not contain hardcoded, static test data). The test data can be set up either by making requests to the relevant apply or amend operations of the API, or directly loaded into the database using e.g. `mongoimport`. 

The stub supports these PLA service API operations:

- `GET /protect-your-lifetime-allowance/individuals/{nino}/protections` - get all pension lifetime allowance protections granted to the individual with the specified NINO
- `GET /protect-your-lifetime-allowance/individuals/{nino}/protections/{protectionId}` - get a specific protection by NINO and protection ID
- `POST /protect-your-lifetime-allowance/individuals/{nino}/protections` - apply for a new protection for the individual with the specified NINO
- `PUT /protect-your-lifetime-allowance/individuals/{nino}/protections/{protectionId}` - amend the specified existing protection
- `GET /psa/check-protection?ref=<protectionReference>&psaref=<psaCheckReference>` - enables pension scheme administrator (PSA) check of validity and amount of a protection

The stub attempts to apply the same business rules as the full production service to protection application and amendment requests, which can return different outcomes based partly on whether and what type of protections are already in place for the indivudual. A notification ID returned with the response identifies the specific outcome.

(The stub also supports DELETE operations for the purposes of making it easy to tear down test data - these are not supported on the production API of the service.)

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
