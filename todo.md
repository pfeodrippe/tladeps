## Pulumi

- [x] Could we create resources using namespaced keywords?
  - [x] Make it work for bucket
  - [x] Get all attr values for a resource
  - [x] Autocompletion
  - [x] Make it work for Bucket.versioning
  - [x] It could be one map that's parsed
    - Like integrant
    - https://github.com/weavejester/integrant#composite-keys
  - [x] Each key has a defined type which it's derived from docs
  - https://github.com/pulumi/pulumi-java/blob/main/tests/examples/testing-unit-java/src/main/java/com/pulumi/example/unittest/WebserverInfra.java
- [ ] Proxy
  - [ ] Deploy lambda in Python just to see it working
  - [ ] Lambda in Clojure
  - [ ] Get more info from clojars
  - [ ] `tladeps.edn` file from resources
  - [ ] https://repo.clojars.org/io/github/pfeodrippe/tladeps-edn-module/0.4.0/tladeps-edn-module-0.4.0.pom
  - [ ] Scrap new POMs or continue filtering by `tladeps`?
- [x] Create wrapper for Clojure?
  - [x] How to scrap the docs?
  - [x] How to get autocompletion?
- [ ] Type information for an attribute
- [ ] Add `_output_` to attrs that are output only
- [ ] Check using another registry, e.g. Github
- [ ] Serialize cljs code?
- [ ] Navigate with Portal through resource attributes
- [ ] Make repo to store providers
- [ ] Show required methods
- [ ] Make autocompletion for inputs work
- [ ] clj-kondo linting for static values
- [ ] Check schema as early as possible
- [ ] Use Route53 instead of google domains?
  - [ ] Or manage things in google domains using Pulumi?
- [ ] Better error handling for async test

## TLA+
- [ ] Run TLA+ online
  - [ ] Multiple runs
  - [ ] Small specs
  - [ ] Try it out
    - [ ] Like we had with Guima
- [ ] Visual Editor?

## Other
- [ ] Typescript types in CLJ
- [ ] Module for helping TLA+ monitoring
