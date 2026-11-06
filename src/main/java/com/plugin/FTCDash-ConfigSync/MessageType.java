package com.plugin.config;

/** Dashboard message types. These values match the corresponding Redux actions in the frontend. */
public enum MessageType {
    RECEIVE_CONFIG(ReceiveConfig.class);
    final Class<? extends Message> msgClass;
    MessageType(Class<? extends Message> msgClass) {
        this.msgClass = msgClass;
    }
}