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

import io.nats.client.*;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nats.client.impl.NatsJsBenchmarkMessage.HDR_PUB_TIME;

public class NatsJsBenchmarkConsumer implements BenchmarkConsumer, MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(NatsJsBenchmarkConsumer.class);

    // internal
    private final Connection conn;
    private final String subject;
    private final ConsumerCallback consumerCallback;
    private final AtomicInteger received;
    private final JetStream js;

    private final int ackInterval;
    private final long maxAckPending;
    private final long fcHeartbeat;
    private final String deliverGroup;
    private final AckPolicy ackPolicy;

    private final Dispatcher dispatcher;
    private final JetStreamSubscription sub;

    @Override
    public String toString() {
        return "NatsJsBenchmarkConsumer{" +
            "conn=" + conn +
            ", subject='" + subject + '\'' +
            ", consumerCallback=" + consumerCallback +
            ", received=" + received +
            ", js=" + js +
            ", ackInterval=" + ackInterval +
            ", maxAckPending=" + maxAckPending +
            ", fcHeartbeat=" + fcHeartbeat +
            ", deliverGroup='" + deliverGroup + '\'' +
            ", dispatcher=" + dispatcher +
            ", sub=" + sub +
            '}';
    }

    public NatsJsBenchmarkConsumer(NatsJsConfig config, Connection conn, String subject, String subscriptionName,
                                   ConsumerCallback consumerCallback) throws IOException, JetStreamApiException {

        this.conn = conn;
        this.subject = subject;
        this.consumerCallback = consumerCallback;

        received = new AtomicInteger();
        js = conn.jetStream();

        // TODO Tune consumer
        ackInterval = config.jsConsumerAckInterval == 0 ? 0 : (config.jsConsumerAckInterval < 1 ? 100 : config.jsConsumerAckInterval);

        ackPolicy = ackInterval == 1 ? AckPolicy.Explicit : (ackInterval == 0 ? AckPolicy.None : AckPolicy.All);
        if (ackInterval == 0) {
            maxAckPending = -1;
        }
        else {
            maxAckPending = config.jsConsumerMaxAckPending < 1 ? -1 : config.jsConsumerMaxAckPending;
        }
        fcHeartbeat = config.jsConsumerFlowControlHeartbeatTime < 1 ? 5000 : config.jsConsumerFlowControlHeartbeatTime;
        deliverGroup = config.jsDeliverGroup;

//        log.debug("ackInterval {}", ackInterval);
//        log.debug("AckPolicy {}", ackPolicy);
//        log.debug("maxAckPending {}", maxAckPending);
//        log.debug("fcHeartbeat {}", fcHeartbeat);
//        log.debug("deliverGroup {}", deliverGroup);

        PushSubscribeOptions pso = ConsumerConfiguration.builder()
            .ackPolicy(ackPolicy)
            .maxDeliver(1) // won't redeliver messages. helps to optimize acks, wont' redeliver if we miss an ack at the end
            .maxAckPending(maxAckPending)
            .flowControl(fcHeartbeat)
            .deliverGroup(deliverGroup)
            .buildPushSubscribeOptions();

        dispatcher = conn.createDispatcher();
        sub = js.subscribe(subject, dispatcher, this, false, pso);
    }

    @Override
    public void onMessage(Message msg) throws InterruptedException {
        try {
            long publishTimestamp = Long.parseLong(msg.getHeaders().getFirst(HDR_PUB_TIME));
            consumerCallback.messageReceived(msg.getData(), publishTimestamp);

            int count = received.incrementAndGet();
            if (ackInterval > 0 && count % ackInterval == 0) {
                msg.ack();
            }

        } catch (Exception e) {
            log.error("Consumer failed while processing message.", e);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (sub != null) {
                if (dispatcher == null) {
                    sub.unsubscribe();
                }
                else {
                    dispatcher.unsubscribe(sub);
                }
            }
        }
        catch (Exception ignore) {}
    }
}
