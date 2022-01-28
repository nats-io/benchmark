package io.nats.client.impl;

public class NatsJsBenchmarkMessage extends NatsMessage {

    public static final String HDR_PUB_TIME = "pt";

    public NatsJsBenchmarkMessage(String subject, byte[] data) {
        //noinspection ConstantConditions
        super(subject, null, new Headers(), data);
        headers.put(HDR_PUB_TIME, "" + System.currentTimeMillis());
    }

    @Override
    byte[] getProtocolBytes() {
        System.out.println("-p-p-p- " + subject + " " + headers);
        headers.put(HDR_PUB_TIME, "" + System.currentTimeMillis());
        dirty = true;
        return super.getProtocolBytes();
    }

    @Override
    protected boolean calculateIfDirty() {
        System.out.println("!x!x!x! " + subject + " " + headers);
        headers.put(HDR_PUB_TIME, "" + System.currentTimeMillis());
        dirty = true;
        return super.calculateIfDirty();
    }

/*
------------------------------------------------------------
THIS CODE IS FOR AN UNRELEASED VERSION OF THE JAVA CLIENT
------------------------------------------------------------
    public NatsJsBenchmarkMessage(String subject, byte[] data) {
        //noinspection ConstantConditions
        super(subject, null, new Headers(), data);
        headers.put(HDR_PUB_TIME, "" + System.currentTimeMillis());
    }

    @Override
    ByteArrayBuilder getProtocol() {
        headers.put(HDR_PUB_TIME, "" + System.currentTimeMillis());
        return super.getProtocol();
    }
------------------------------------------------------------
*/
}
