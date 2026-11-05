/*
 * Copyright 2009-2026 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.benchmarks.json;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Parses JSON documents with the JSON parser of jolokia-json, using lexers which differ only in the initial size of
 * their read buffer ({@code %buffer} in {@code jsonstrict.flex}). The lexers are generated from copies of the
 * current grammar for each size listed in {@code pom.xml}.
 *
 * <p>Like {@code org.jolokia.server.core.http.HttpRequestHandler}, the document is read with an
 * {@link InputStreamReader} from the bytes of a request body. Run with {@code -prof gc} to see the memory allocated
 * for each parsed document.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class JsonParserBenchmark {

    /** Initial size of the lexer's read buffer (chars). 16384 is the JFlex default. */
    @Param({ "256", "512", "1024", "2048", "4096", "16384" })
    public int bufferSize;

    /** A document from {@link Payloads} or {@code file:<path>} */
    @Param({ "version", "read", "bulk", "execLargeArgument", "listResponse" })
    public String payload;

    private byte[] body;
    private Parser parser;

    @FunctionalInterface
    private interface Parser {
        Object parse(Reader reader) throws Exception;
    }

    @Setup
    public void setup() throws Exception {
        body = Payloads.get(payload);
        parser = switch (bufferSize) {
            case 256 -> r -> new org.jolokia.benchmarks.json.buffer256.JSONParser().parse(r);
            case 512 -> r -> new org.jolokia.benchmarks.json.buffer512.JSONParser().parse(r);
            case 1024 -> r -> new org.jolokia.benchmarks.json.buffer1024.JSONParser().parse(r);
            case 2048 -> r -> new org.jolokia.benchmarks.json.buffer2048.JSONParser().parse(r);
            case 4096 -> r -> new org.jolokia.benchmarks.json.buffer4096.JSONParser().parse(r);
            case 16384 -> r -> new org.jolokia.benchmarks.json.buffer16384.JSONParser().parse(r);
            default -> throw new IllegalArgumentException("No lexer with a buffer of " + bufferSize + " chars, see pom.xml");
        };

        // the copied parser has to produce the same result as the one of jolokia-json
        Object expected = new org.jolokia.json.parser.JSONParser().parse(reader());
        if (!Objects.equals(parse(), expected)) {
            throw new IllegalStateException("Parser with a buffer of " + bufferSize + " chars parsed " + payload + " differently");
        }
    }

    @Benchmark
    public Object parse() throws Exception {
        return parser.parse(reader());
    }

    private Reader reader() {
        return new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8);
    }
}
