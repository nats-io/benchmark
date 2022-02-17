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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static java.lang.System.lineSeparator;

public class Stats {

    long elapsed;
    long elapsedMin = Long.MAX_VALUE;
    long elapsedMax = Long.MIN_VALUE;


    int totalMessages;
    long totalBytes;

    int payloadSize;
    int messageCount;

    public Stats(int payloadSize, int messageCount) {
        this.payloadSize = payloadSize;
        this.messageCount = messageCount;
    }

    public void update(Result r) {
        ++totalMessages;
        totalBytes += payloadSize;

        elapsed += r.elapsed();
        elapsedMin = Math.min(elapsedMin, r.elapsed());
        elapsedMax = Math.max(elapsedMax, r.elapsed());
    }

    public Stats update(Stats s) {
        totalMessages += s.totalMessages;
        totalBytes += s.totalBytes;

        elapsed += s.elapsed;
        elapsedMin = Math.min(elapsedMin, s.elapsedMin);
        elapsedMax = Math.max(elapsedMax, s.elapsedMax);

        return this;
    }

    private void column(OutputStream out, long v) throws IOException {
        String text = "," + v;
        out.write(text.getBytes());
    }

    private void column(OutputStream out, double v) throws IOException {
        String text = "," + String.format("%6.3f", v).trim();
        out.write(text.getBytes());
    }

    static final byte[] HEADER = ("Size,Count,Total(ms),Average(ms),Min(ms),Max(ms),bytes/ms,bytes/sec,kb/sec,message/sec" + lineSeparator()).getBytes(StandardCharsets.UTF_8);

    public static void writeHeader(OutputStream out) throws Exception {
        out.write(HEADER);
    }

    public void writeData(OutputStream out) throws Exception {
        double dTotalMessages = totalMessages;
        if (messageCount == -1) {
            out.write("TOTALS,".getBytes());
        }
        else {
            out.write(("" + payloadSize + "," + messageCount).getBytes());
        }
        column(out, elapsed);
        column(out, elapsed / dTotalMessages);
        column(out, elapsedMin);
        column(out, elapsedMax);
        column(out, bytesPerMillis(elapsed));
        column(out, bytesPerSecond(elapsed));
        column(out, kbPerSecond(elapsed));
        column(out, messagesPerSecond(elapsed));
        out.write(lineSeparator().getBytes(StandardCharsets.US_ASCII));
    }

    private double bytesPerMillis(double elapsedMillis) {
        return (double)totalBytes / elapsedMillis;
    }

    private double bytesPerSecond(double elapsedMillis) {
        return (double)totalBytes * 1000d / elapsedMillis;
    }

    private double kbPerSecond(double elapsedMillis) {
        return (double)totalBytes * 1000d / 1024d / elapsedMillis;
    }

    private double messagesPerSecond(double elapsedMillis) {
        return (double)totalMessages * 1000d / elapsedMillis;
    }
}
