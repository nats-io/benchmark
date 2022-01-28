package io.nats.client.impl;

public class NatsJsBenchmarkMessage extends NatsMessage {

    public static final String HDR_PUB_TIME = "pt";

    public NatsJsBenchmarkMessage(String subject, byte[] data) {
        //noinspection ConstantConditions
        super(subject, null, new Headers(), data);
        headers.put(HDR_PUB_TIME, "" + System.currentTimeMillis());
    }

/*
------------------------------------------------------------
THIS CODE IS FOR AN UNRELEASED VERSION OF THE JAVA CLIENT
IT GETS THE TIMESTAMP AS CLOSE TO THE PUBLISH TIME AS POSSIBLE
------------------------------------------------------------
    @Override
    ByteArrayBuilder getProtocol() {
        headers.put(HDR_PUB_TIME, "" + System.currentTimeMillis());
        return super.getProtocol();
    }
------------------------------------------------------------
*/
}
