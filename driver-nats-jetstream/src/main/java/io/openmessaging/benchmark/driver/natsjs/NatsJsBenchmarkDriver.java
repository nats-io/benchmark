/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.openmessaging.benchmark.driver.natsjs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.impl.ErrorListenerLoggerImpl;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NatsJsBenchmarkDriver implements BenchmarkDriver {
    private static final String STREAM_NAME_PREFIX = "strm-";
    private static final String TOPIC_NAME_PREFIX = "njsb-";

    private NatsJsConfig config;
    private List<Connection> connections;
    private List<NatsJsBenchmarkConsumer> consumers;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        initialize( readConfig(configurationFile) );
    }

    public void initialize(NatsJsConfig config) throws IOException {
        this.config = config;
        log.info("Initializing Nats JetStream Driver with configuration: {}", writeableConfig());
        connections = Collections.synchronizedList(new ArrayList<>());
        consumers = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public String getTopicNamePrefix() {
        return TOPIC_NAME_PREFIX;
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = Nats.connect(normalizedOptions())) {
                String stream = safeName(STREAM_NAME_PREFIX, topic);
                String subject = safeName(topic);

                StorageType storageType = "file".equalsIgnoreCase(config.jsStorageType)
                    ? StorageType.File
                    : StorageType.Memory;
                int replicas = config.jsReplicas < 2 ? 1 : Math.min(config.jsReplicas, 5);

                JetStreamManagement jsm = conn.jetStreamManagement();

                try { conn.jetStreamManagement().deleteStream(stream); } catch (Exception e) { /* don't care if it doesn't exist */ }

                StreamConfiguration streamConfig = StreamConfiguration.builder()
                    .name(stream)
                    .subjects(subject)
                    .storageType(storageType)
                    .replicas(replicas)
                    .build();
                jsm.addStream(streamConfig);
            }
            catch (RuntimeException re) { throw re; }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String subject = safeName(topic);
                return new NatsJsBenchmarkProducer(config, connection(), subject);
            }
            catch (RuntimeException re) { throw re; }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String subject = safeName(topic);
                NatsJsBenchmarkConsumer consumer =
                    new NatsJsBenchmarkConsumer(config, connection(), subject, subscriptionName, consumerCallback);
                consumers.add(consumer);
                return consumer;
            }
            catch (RuntimeException re) { throw re; }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Override
    public void close() throws Exception {
        consumers.forEach(consumer -> {
            try {
                consumer.close();
            }
            catch (Exception ignore) {}
        });
        connections.forEach(connection -> {
            try {
                connection.close();
            }
            catch (Exception ignore) {}
        });
    }

    private Connection connection() throws IOException, InterruptedException {
        Connection conn = Nats.connect(normalizedOptions());
        connections.add(conn);
        return conn;
    }

    private String patchUserPass(String baseUrl) {
        // monkey patch this for a benchmark
        // assumes nats://
        int index = 7;
        String pre = baseUrl.substring(0,index);
        String post = baseUrl.substring(index);
        return pre + "UserA:s3cr3t@" + post;
    }

    private Options normalizedOptions() {
        Options.Builder builder = new Options.Builder();
        builder.server(config.natsHostUrl);
        builder.maxReconnects(5);
        builder.errorListener(new ErrorListenerLoggerImpl());

        // TODO Todd what is this for?
//        for(int i=0; i < config.workers.length; i++) {
//            builder.server(patchUserPass(config.workers[i]));
//        }
        return builder.build();
    }

    // Don't want any control characters in stream or subject names
    private String safeName(String unsafe) {
        return safeName(null, unsafe);
    }

    private String safeName(String prefix, String unsafe) {
        StringBuilder sb = new StringBuilder(prefix == null ? "" : prefix);
        for (int x = 0; x < unsafe.length(); x++) {
            char c = unsafe.charAt(x);
            if (c == '.') {
                sb.append("_dot_");
            }
            else if (c == '>') {
                sb.append("_gt_");
            }
            else if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-' || c == '_') {
                sb.append(c);
            }
            else {
                sb.append('_');
                sb.append((char)('a' + (c % 26)));
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static NatsJsConfig readConfig(File configurationFile) throws IOException {
        return new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(configurationFile, NatsJsConfig.class);
    }

    private String writeableConfig() throws IOException {
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(config);
    }
    private static final Logger log = LoggerFactory.getLogger(NatsJsBenchmarkDriver.class);
}
