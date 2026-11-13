package com.mjolnir5273.config;

public class ReceiveConfig extends Message {
    public CustomVariable configRoot;

    public ReceiveConfig(CustomVariable configRoot) {
        super(MessageType.RECEIVE_CONFIG);

        this.configRoot = configRoot;
    }
}