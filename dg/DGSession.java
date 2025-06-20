package dg;

import dg.enums.DGChannel;
import dg.enums.DGState;
import nano.http.d2.console.Logger;
import nano.http.d2.json.NanoJSON;
import nano.http.d2.qr.QrCode;
import nano.http.d2.utils.WebSocketClient;

import java.io.IOException;

public class DGSession implements Runnable {
    public static String endpoint = "FIXME";

    private static final String wave = "[\"0A0A0A0A00000000\",\"0A0A0A0A0A0A0A0A\",\"0A0A0A0A14141414\",\"0A0A0A0A1E1E1E1E\",\"0A0A0A0A28282828\",\"0A0A0A0A32323232\",\"0A0A0A0A3C3C3C3C\",\"0A0A0A0A46464646\",\"0A0A0A0A50505050\",\"0A0A0A0A5A5A5A5A\",\"0A0A0A0A64646464\"]";
    private final int minA;
    private final int minB;
    private DGState state = DGState.WAITING_SERVER;
    private String serverId;
    private String clientId;
    private QrCode qrCode;
    private int limitA;
    private int limitB;
    private WebSocketClient client;
    private boolean fin = false;
    private int strengthA = 0;
    private int strengthB = 0;

    public int getStrengthA() {
        return strengthA;
    }

    public int getStrengthB() {
        return strengthB;
    }

    public DGSession(int minA, int minB) {
        this.minA = minA;
        this.minB = minB;
        DGSharedPool.executorService.submit(this);
    }

    public DGState getState() {
        return state;
    }

    public QrCode getQrCode() {
        expectState(DGState.WAITING_CLIENT, "get QR code");
        return qrCode;
    }

    public int getLimitA() {
        expectState(DGState.PLAYING, "get limit (A)");
        return limitA;
    }

    public int getLimitB() {
        expectState(DGState.PLAYING, "get limit (B)");
        return limitB;
    }

    private void _sendRaw(String message) {
        expectState(DGState.PLAYING, "send message");
        if (client == null) {
            throw new IllegalStateException("WebSocket client is not connected.");
        }
        client.send(message);
    }

    public void _sendJson(NanoJSON message) {
        message.put("clientId", serverId);
        message.put("targetId", clientId);
        _sendRaw(message.toString());
    }

    public int getMaxStrength(DGChannel channel) {
        expectState(DGState.PLAYING, "get max strength");
        switch (channel) {
            case A:
                return limitA;
            case B:
                return limitB;
            default:
                throw new IllegalArgumentException("Invalid channel: " + channel);
        }
    }

    private void _sendStrength(int strength, boolean A) {
        expectState(DGState.PLAYING, "send strength");
        if (strength < 0) {
            throw new IllegalArgumentException("Strength cannot be negative: " + strength);
        }
        if (strength > getMaxStrength(A ? DGChannel.A : DGChannel.B)) {
            throw new IllegalArgumentException("Strength exceeds maximum limit: " + strength + " > " + getMaxStrength(A ? DGChannel.A : DGChannel.B));
        }
        if (A) {
            if (strengthA == strength) {
                return; // No change in strength, no need to send
            }
            strengthA = strength;
        } else {
            if (strengthB == strength) {
                return; // No change in strength, no need to send
            }
            strengthB = strength;
        }
        NanoJSON message = new NanoJSON();
        message.put("type", 4);
        message.put("message", "strength-" + (A ? 1 : 2) + "+2+" + strength);
        _sendJson(message);
    }

    public void sendStrength(int strength, DGChannel channel) {
        if (channel.contains(DGChannel.A)) {
            _sendStrength(strength, true);
        }
        if (channel.contains(DGChannel.B)) {
            _sendStrength(strength, false);
        }
    }

    public void sendWave(int duration, DGChannel channel) {
        expectState(DGState.PLAYING, "send wave");
        if (duration <= 0) {
            throw new IllegalArgumentException("Wave duration must be positive: " + duration);
        }
        fin = false;

        NanoJSON message = new NanoJSON();
        message.put("type", "clientMsg");
        message.put("message", channel.contains(DGChannel.A) ? ("A:" + wave) : "PASS");
        message.put("message2", channel.contains(DGChannel.B) ? ("B:" + wave) : "PASS");
        message.put("time1", duration);
        message.put("time2", duration);
        _sendJson(message);
    }

    public boolean isPlaying() {
        expectState(DGState.PLAYING, "check if playing");
        return fin;
    }

    public void waitTillStopped() {
        expectState(DGState.PLAYING, "wait till stopped");
        try {
            while (!fin) {
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void expectState(DGState expected, String operation) {
        if (state != expected) {
            throw new IllegalStateException("Cannot " + operation + " in state " + state + ", expected " + expected);
        }
        if (state == DGState.PLAYING && client == null) {
            throw new IllegalStateException("WebSocket client is null but state is PLAYING, in operation: " + operation);
        }
    }

    private void stateChange(DGState expected, DGState newState) {
        if (state != expected) {
            throw new IllegalStateException("Could not change state from " + state + " to " + newState + ", expected " + expected);
        }
        state = newState;
    }

    public void awaitState(DGState expected) {
        try {
            while (state != expected && state != DGState.CLOSED) {
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void close() {
        if (state == DGState.CLOSED) {
            return;
        }
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception ignored) {
        }
        state = DGState.CLOSED;
    }

    @Override
    public void run() {
        try {
            client = new WebSocketClient(endpoint);
            while (true) {
                String _message = client.read();
                NanoJSON jsonMessage;
                try {
                    jsonMessage = new NanoJSON(_message);
                } catch (Exception ex) {
                    if (_message.contains("完毕")) {
                        fin = true;
                    }
                    continue;
                }
                if (jsonMessage.has("type")) {
                    String type = jsonMessage.getString("type");
                    switch (type) {
                        case "bind":
                            if (jsonMessage.has("clientId")) {
                                String cid = jsonMessage.getString("clientId");
                                if (cid.isEmpty()) {
                                    throw new IOException("clientId field exists but is empty.");
                                }
                                if (serverId != null && !serverId.equals(cid)) {
                                    throw new IOException("Already bound to a client: " + serverId + ",but received: " + cid);
                                }
                                serverId = cid;
                                qrCode = QrCode.encodeText("https://www.dungeon-lab.com/app-download.php#DGLAB-SOCKET#" + endpoint + serverId, QrCode.Ecc.LOW);
                                if (state != DGState.WAITING_CLIENT) {
                                    stateChange(DGState.WAITING_SERVER, DGState.WAITING_CLIENT);
                                }
                            } else {
                                throw new IOException("Message has type 'bind' but no 'clientId' field.");
                            }

                            if (jsonMessage.has("targetId")) {
                                String targetId = jsonMessage.getString("targetId");
                                if (!targetId.isEmpty()) {
                                    if (clientId != null && !clientId.equals(targetId)) {
                                        throw new IOException("Already bound to a target: " + clientId + ", but received: " + targetId);
                                    }
                                    clientId = targetId;
                                    stateChange(DGState.WAITING_CLIENT, DGState.CONFIGURE);
                                }
                            } else {
                                throw new IOException("Message has type 'bind' but no 'targetId' field.");
                            }
                            break;
                        case "msg":
                            if (jsonMessage.toMap().containsKey("message")) {
                                String msg = jsonMessage.getString("message");
                                if (msg.startsWith("strength-")) {
                                    if (state == DGState.CONFIGURE) {
                                        state = DGState.PLAYING;
                                    } else if (state != DGState.PLAYING) {
                                        throw new IOException("Unexpected message in state " + state + ": " + msg);
                                    }

                                    String[] subStr = msg.substring(9).split("\\+");
                                    if (subStr.length != 4) {
                                        throw new IOException("Invalid strength message format: " + msg);
                                    }

                                    limitA = Integer.parseInt(subStr[2]);
                                    limitB = Integer.parseInt(subStr[3]);
                                    if (limitA < minA || limitB < minB) {
                                        throw new IOException("Strength limits are below minimum: A=" + limitA + ", B=" + limitB + " (min: A=" + minA + ", B=" + minB + ")");
                                    }
                                }
                            } else {
                                throw new IOException("Message has type 'msg' but no 'message' field.");
                            }
                            break;
                        case "break":
                        case "error":
                            throw new IOException("Connection reset by peer: " + type);
                        default:
                            break;
                    }
                }
            }
        } catch (Exception ex) {
            Logger.info("Connection closed: " + ex.getMessage() + " (" + ex.getClass().getSimpleName() + ")");
        }

        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception ignored) {
        }
        state = DGState.CLOSED;
    }
}
