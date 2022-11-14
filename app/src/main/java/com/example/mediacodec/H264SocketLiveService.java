package com.example.mediacodec;

import android.media.projection.MediaProjection;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class H264SocketLiveService {
    private WebSocket webSocket;
    H264Encoder h264Encoder;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void start(MediaProjection mediaProjection) {
        webSocketServer.start();
        h264Encoder = new H264Encoder(this, mediaProjection);
        h264Encoder.start();
    }
    public void sendData(byte[] bytes) {
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.send(bytes);
        }
    }

    // 服务端，负责把A设备上偷屏的数据发到b设备上。B设备可以看到A设备的投屏画面
    private WebSocketServer webSocketServer = new WebSocketServer(new InetSocketAddress(9007)) {
        @Override
        public void onOpen(WebSocket webSocket, ClientHandshake handshake) {
            H264SocketLiveService.this.webSocket = webSocket;
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {

        }

        @Override
        public void onMessage(WebSocket conn, String message) {

        }

        @Override
        public void onError(WebSocket conn, Exception ex) {

        }

        @Override
        public void onStart() {

        }
    };
}
