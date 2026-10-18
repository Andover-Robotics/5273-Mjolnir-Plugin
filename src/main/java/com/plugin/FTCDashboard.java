package com.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.plugin.config.*;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class FTCDashboard {
    private final CompletableFuture<CustomVariable> result = new CompletableFuture<>();
    private @Nullable WebSocket socket;
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Message.class, new MessageDeserializer())
            .registerTypeAdapter(BasicVariable.class, new ConfigVariableDeserializer())
            .registerTypeAdapter(CustomVariable.class, new ConfigVariableDeserializer())
            .serializeNulls()
            .create();
    private static CustomVariable parse(StringBuffer buffer) {
        try {
            Message msg = gson.fromJson(buffer.toString(), Message.class);
            if (msg.getType() != MessageType.RECEIVE_CONFIG) return null;
            return ((ReceiveConfig) msg).configRoot;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
    public enum Progress {
        SOCKET_OPEN,
    }
    public CompletableFuture<CustomVariable> getResult() {
        return result;
    }
    public void cancel() {
        if (socket != null) socket.abort();
    }
    public FTCDashboard(Consumer<Progress> progress) {
        HttpClient.newHttpClient().newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .buildAsync(URI.create("ws://192.168.49.1:8000"), new WebSocket.Listener() {
                    private final StringBuffer current = new StringBuffer();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        progress.accept(Progress.SOCKET_OPEN);
                        socket = webSocket;
                        webSocket.request(1);
                        webSocket.sendText("{ \"type\": \"GET_CONFIG\"}", true);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        current.append(data);
                        if (last) {
                            // Parse
                            CustomVariable out = parse(current);
                            current.setLength(0);
                            if (out != null) {
                                cancel();
                                result.complete(out);
                            }
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        cancel();
                        result.completeExceptionally(new Throwable(reason));
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        cancel();
                        result.completeExceptionally(error);
                    }
                }).exceptionallyAsync(err -> {
                    cancel();
                    result.completeExceptionally(err);
                    return null;
                });
    }
}