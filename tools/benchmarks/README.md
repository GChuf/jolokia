# Jolokia JMH benchmarks

Benchmarks are built only with the `benchmarks` profile:

```shell
mvn -Pbenchmarks -pl tools/benchmarks -am package -DskipTests
```

Run all benchmarks (see the `-h` option for more options):

```shell
java -jar tools/benchmarks/target/benchmarks.jar -prof gc
```

## JsonParserBenchmark

Parses JSON documents with the JSON parser of `jolokia-json`, using lexers which differ only in the initial size of
their read buffer (`%buffer` in `json/src/main/jflex/jsonstrict.flex`). For each size, `pom.xml` copies the current
grammar and parser into a separate package and generates the lexer with JFlex. `16384` is the JFlex default.

The benchmark checks that each copy parses a document to the same result as the parser of `jolokia-json`.

Parameters:

* `bufferSize` - `256`, `512`, `1024`, `2048`, `4096` or `16384`. Other sizes need to be added to `pom.xml` and to
  `JsonParserBenchmark`.
* `payload`:
  * `version` - the smallest request
  * `read` - a single request (~300 bytes)
  * `bulk` - a bulk request of 50 `read` requests (~15 KB)
  * `execLargeArgument` - a request with a 64K chars string argument, for which smaller buffers need to grow
  * `listResponse` - a large (~500 KB) response, as parsed by the Java client
  * `file:<path>` - any other document, e.g. a request body captured in the browser

The full run takes about 10 minutes. To limit it, e.g. to compare the default buffer and 1K for small requests:

```shell
java -jar tools/benchmarks/target/benchmarks.jar JsonParserBenchmark -prof gc -p bufferSize=1024,16384 -p payload=version,read,bulk
```

In the results, `Score` is the time to parse a document (ns/op) and `gc.alloc.rate.norm` the memory allocated
while parsing it (B/op).
