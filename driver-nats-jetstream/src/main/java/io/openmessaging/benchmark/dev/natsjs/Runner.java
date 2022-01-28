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
package io.openmessaging.benchmark.dev.natsjs;

import io.nats.client.Connection;
import io.nats.client.ErrorListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StorageType;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.natsjs.NatsJsBenchmarkDriver;
import io.openmessaging.benchmark.driver.natsjs.NatsJsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.nats.client.support.RandomUtils.PRAND;
import static java.lang.System.lineSeparator;

public class Runner {
    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    public static final String URL = Options.DEFAULT_URL;
    public static final StorageType STORAGE_TYPE = StorageType.Memory;

    public static final String OUTPUT_FILE = "C:\\temp\\JsTiming.csv";

    public static final int[] PAYLOAD_SIZES = new int[]{
        128,
        1024,
//        16 * 1024,
//        64 * 1024
    };

    public static final int[] MESSAGE_COUNTS = new int[] {
        10,
//        1000,
//        5000,
//        10_000,
//        100_000
    };

    public static void main(String[] args) {
        Options options = new Options.Builder().server(URL)
            .errorListener(new ErrorListener() {}).build();

        try (FileOutputStream out = new FileOutputStream(OUTPUT_FILE)) {
            // JUST SOME INFO
            try (Connection conn = Nats.connect(options)) {
                String text = "Server Version," + conn.getServerInfo().getVersion() + lineSeparator() + "Client Version," + Nats.CLIENT_VERSION + lineSeparator();
                System.out.print(text);
                out.write(text.getBytes());
            }

            Stats.writeHeader(out);
            Stats combinedStats = new Stats(-1, -1);
            List<Stats> statsList = new ArrayList<>();
            for (int messageCount : MESSAGE_COUNTS) {
                for (int payloadSize : PAYLOAD_SIZES) {

                    List<Result> results = runDriver(messageCount, payloadSize);
                    if (results != null) {
                        Stats s = new Stats(payloadSize, messageCount);
                        for (Result r : results) {
                            s.update(r);
                        }
                        s.writeData(out);
                        statsList.add(s);
                        combinedStats.update(s);
                    }
                }
            }
            combinedStats.writeData(out);

            Stats.writeHeader(System.out);
            for (Stats st : statsList) {
                st.writeData(System.out);
            }
            combinedStats.writeData(System.out);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<Result> runDriver(int messageCount, int payloadSize) throws Exception {
        byte[] payload = new byte[payloadSize];
        PRAND.nextBytes(payload);

        // I'm doing this here so we can vary some config
        NatsJsConfig natsJsConfig = new NatsJsConfig();
        natsJsConfig.natsHostUrl = URL;
        natsJsConfig.jsStorageType = STORAGE_TYPE.toString();
        natsJsConfig.jsConsumerAckInterval = messageCount / 100;
        natsJsConfig.jsConsumerMaxAckPending = natsJsConfig.jsConsumerAckInterval * 3;

        try (NatsJsBenchmarkDriver driver = new NatsJsBenchmarkDriver()) {
            driver.initialize(natsJsConfig);

            String subname = messageCount + "x" + payloadSize;
            String topic = driver.getTopicNamePrefix() + subname;

            driver.createTopic(topic, 1).get();

            RunnerConsumerCallback callback = new RunnerConsumerCallback(messageCount);

            // start the consumer and producer futures
            CompletableFuture<BenchmarkConsumer> conFut = driver.createConsumer(topic, subname, callback);
            CompletableFuture<BenchmarkProducer> proFut = driver.createProducer(topic);

            // get consumer future. 10 seconds is more than we need, but is safe
            BenchmarkConsumer consumer = conFut.get(10, TimeUnit.SECONDS);

            // get the producer future
            BenchmarkProducer producer = proFut.get(10, TimeUnit.SECONDS);

            // publish messages. TODO add pauses / jitter for large data sets
            for (int x = 0; x < messageCount; x++) {
                producer.sendAsync(Optional.empty(), payload);
            }

            // give the whole system time to work, but not forever
            long timeout = Math.max(10, messageCount / 200);
            if ( callback.latch.await(timeout, TimeUnit.SECONDS) ) {
                return callback.results;
            }
        }

        return null;
    }

    static class RunnerConsumerCallback implements ConsumerCallback {
        List<Result> results = new ArrayList<>();
        CountDownLatch latch;

        public RunnerConsumerCallback(int messageCount) {
            latch = new CountDownLatch(messageCount);
        }

        @Override
        public void messageReceived(byte[] payload, long publishTimestamp) {
            results.add(new Result(publishTimestamp, System.currentTimeMillis()));
            latch.countDown();
        }

        @Override
        public void messageReceived(ByteBuffer payload, long publishTimestamp) {
            // don't care, our consumer calls the other version
        }
    }
}
