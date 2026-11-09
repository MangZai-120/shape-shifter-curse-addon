package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/** Serves synchronized tutorial assets to the system browser, including seekable videos. */
final class FormTutorialLocalServer {
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");
    private static HttpServer server;
    private static ExecutorService workers;
    private static Path root;
    private static String prefix;
    private static boolean stoppingRegistered;

    private FormTutorialLocalServer() {}

    static synchronized URI page(Path directory, String form) throws IOException {
        Path requested = directory.toRealPath();
        if (server == null || !requested.equals(root)) {
            stop();
            var next = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var executor = Executors.newFixedThreadPool(3, task -> {
                Thread thread = new Thread(task, "SSCA-Tutorial");
                thread.setDaemon(true);
                return thread;
            });
            root = requested;
            prefix = "/" + UUID.randomUUID() + "/";
            next.createContext("/", FormTutorialLocalServer::serve);
            next.setExecutor(executor);
            workers = executor;
            server = next;
            server.start();
            if (!stoppingRegistered) {
                stoppingRegistered = true;
                ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stop());
            }
        }
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + prefix + form + "/index.html");
    }

    private static void serve(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            Path file;
            try {
                if (!path.startsWith(prefix)) throw new IOException("Unknown tutorial path");
                file = root.resolve(path.substring(prefix.length())).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IOException("Not a tutorial file");
                file = file.toRealPath();
                if (!file.startsWith(root)) throw new IOException("Not a tutorial file");
            } catch (IOException | RuntimeException invalid) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            long size = Files.size(file), start = 0, end = size - 1;
            int status = 200;
            var headers = exchange.getResponseHeaders();
            headers.set("Accept-Ranges", "bytes");
            headers.set("Content-Type", contentType(file));
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Cache-Control", "no-cache");
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (range != null) {
                try {
                    var match = RANGE.matcher(range);
                    if (!match.matches() || size == 0 || match.group(1).isEmpty() && match.group(2).isEmpty())
                        throw new IllegalArgumentException("Invalid byte range");
                    if (match.group(1).isEmpty()) {
                        long length = Long.parseLong(match.group(2));
                        if (length <= 0) throw new IllegalArgumentException("Invalid byte range");
                        start = Math.max(0, size - length);
                    } else {
                        start = Long.parseLong(match.group(1));
                        if (!match.group(2).isEmpty()) end = Math.min(end, Long.parseLong(match.group(2)));
                    }
                    if (start > end || start >= size) throw new IllegalArgumentException("Invalid byte range");
                    status = 206;
                    headers.set("Content-Range", "bytes " + start + "-" + end + "/" + size);
                } catch (IllegalArgumentException invalid) {
                    headers.set("Content-Range", "bytes */" + size);
                    exchange.sendResponseHeaders(416, -1);
                    return;
                }
            }
            long length = Math.max(0, end - start + 1);
            headers.set("Content-Length", Long.toString(length));
            exchange.sendResponseHeaders(status, method.equals("HEAD") || length == 0 ? -1 : length);
            if (method.equals("HEAD") || length == 0) return;
            try (var input = Files.newInputStream(file); var output = exchange.getResponseBody()) {
                input.skipNBytes(start);
                byte[] buffer = new byte[65536];
                long remaining = length;
                while (remaining > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) break;
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        } finally {
            exchange.close();
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg")) return "image/jpeg";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".woff")) return "font/woff";
        if (name.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    private static synchronized void stop() {
        if (server != null) server.stop(0);
        if (workers != null) workers.shutdownNow();
        server = null;
        workers = null;
    }
}
