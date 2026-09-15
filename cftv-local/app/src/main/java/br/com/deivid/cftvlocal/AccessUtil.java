package br.com.deivid.cftvlocal;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

final class AccessUtil {
    enum Mode { CHECKING, LOCAL, P2P, UNAVAILABLE }

    private AccessUtil() {}

    static boolean probeRtsp(String url, int timeoutMs) {
        HostPort endpoint = hostPort(url);
        if (endpoint == null) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host, endpoint.port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static String[] credentials(String url) {
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("rtsp://")) return null;
        try {
            String value = url.substring(7);
            int slash = value.indexOf('/');
            String authority = slash >= 0 ? value.substring(0, slash) : value;
            int at = authority.lastIndexOf('@');
            if (at <= 0) return null;
            String userInfo = authority.substring(0, at);
            int colon = userInfo.indexOf(':');
            if (colon <= 0) return null;
            return new String[]{userInfo.substring(0, colon), userInfo.substring(colon + 1)};
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HostPort hostPort(String url) {
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("rtsp://")) return null;
        try {
            String value = url.substring(7);
            int slash = value.indexOf('/');
            String authority = slash >= 0 ? value.substring(0, slash) : value;
            int at = authority.lastIndexOf('@');
            String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
            String host = hostPort;
            int port = 554;
            int colon = hostPort.lastIndexOf(':');
            if (colon > 0 && colon < hostPort.length() - 1) {
                host = hostPort.substring(0, colon);
                port = Integer.parseInt(hostPort.substring(colon + 1));
            }
            return host.isEmpty() ? null : new HostPort(host, port);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class HostPort {
        final String host;
        final int port;
        HostPort(String host, int port) { this.host = host; this.port = port; }
    }
}
