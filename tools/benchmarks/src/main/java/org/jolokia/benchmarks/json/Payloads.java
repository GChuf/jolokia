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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON documents to parse in the benchmarks. Requests resemble those which Hawtio sends to an Apache ActiveMQ
 * Artemis broker. Any other document can be parsed with {@code file:<path>}.
 */
final class Payloads {

    // processing parameters, which Hawtio sends with each request
    private static final String CONFIG = "\"config\":{\"maxDepth\":7,\"maxCollectionSize\":50000,\"ignoreErrors\":true,\"canonicalNaming\":false}";

    private Payloads() {
    }

    static byte[] get(String name) throws IOException {
        if (name.startsWith("file:")) {
            return Files.readAllBytes(Path.of(name.substring("file:".length())));
        }
        String json = switch (name) {
            // the smallest request
            case "version" -> "{\"type\":\"version\"}";
            // a single request (~300 bytes)
            case "read" -> read(0);
            // a bulk request, as sent by Hawtio to update a table of queues (~15 KB)
            case "bulk" -> bulk(50);
            // a request with a string longer than the smaller buffers, which then need to grow (~64 KB)
            case "execLargeArgument" -> execLargeArgument(64 * 1024);
            // a large response (as parsed by the Java client), similar to a "list" response (~500 KB)
            case "listResponse" -> listResponse(200, 25);
            default -> throw new IllegalArgumentException("Unknown payload " + name);
        };
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String queue(int i) {
        return "org.apache.activemq.artemis:broker=\\\"0.0.0.0\\\",component=addresses,address=\\\"queue." + i
            + "\\\",subcomponent=queues,routing-type=\\\"anycast\\\",queue=\\\"queue." + i + "\\\"";
    }

    private static String read(int i) {
        return "{\"type\":\"read\",\"mbean\":\"" + queue(i) + "\",\"attribute\":\"MessageCount\"," + CONFIG + "}";
    }

    private static String bulk(int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(read(i));
        }
        return sb.append(']').toString();
    }

    private static String execLargeArgument(int length) {
        return "{\"type\":\"exec\",\"mbean\":\"" + queue(0) + "\","
            + "\"operation\":\"sendMessage(java.util.Map,int,java.lang.String,boolean,java.lang.String,java.lang.String)\","
            + "\"arguments\":[{},3,\"" + "x".repeat(length) + "\",true,null,null]," + CONFIG + "}";
    }

    private static String listResponse(int mbeans, int attributes) {
        StringBuilder sb = new StringBuilder("{\"request\":{\"type\":\"list\"},\"value\":{\"org.apache.activemq.artemis\":{");
        for (int m = 0; m < mbeans; m++) {
            if (m > 0) {
                sb.append(',');
            }
            sb.append("\"broker=\\\"0.0.0.0\\\",component=addresses,address=\\\"queue.").append(m).append("\\\"\":{")
                .append("\"desc\":\"Information on the management interface of the MBean\",\"class\":\"org.apache.activemq.artemis.core.management.impl.AddressControlImpl\",\"attr\":{");
            for (int a = 0; a < attributes; a++) {
                if (a > 0) {
                    sb.append(',');
                }
                sb.append("\"Attribute").append(a).append("\":{\"rw\":false,\"type\":\"long\",\"desc\":\"Description of attribute ")
                    .append(a).append("\",\"value\":").append(a * 1000L + m).append('}');
            }
            sb.append("}}");
        }
        return sb.append("}},\"status\":200,\"timestamp\":1762300000}").toString();
    }
}
