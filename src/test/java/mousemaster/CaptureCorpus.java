package mousemaster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Rewrites the screens under src/test/resources/vision by driving Chrome. Each page
 * reports the elements it considers clickable, so the targets need no labelling by hand.
 */
public class CaptureCorpus {

    private static final int viewportWidth = 1280, viewportHeight = 720, scale = 3;
    private static final int port = 9333;
    private static final long settleMillis = 3500;

    private static final String[][] pages = {
            {"excalidraw", "https://excalidraw.com/"},
            {"github", "https://github.com/microsoft/vscode/issues"},
            {"hn", "https://news.ycombinator.com/"},
            {"vscode", "https://vscode.dev/"},
            {"wikipedia", "https://en.wikipedia.org/wiki/Computer_vision"},
    };

    private static final String targetsScript = """
            (() => {
              const selector = 'a[href], button, input, select, textarea, summary,'
                + '[role="button"], [role="link"], [role="tab"], [role="menuitem"],'
                + '[role="checkbox"]';
              const rows = [];
              for (const element of document.querySelectorAll(selector)) {
                const box = element.getBoundingClientRect();
                if (box.width < 8 || box.height < 8) continue;
                if (box.bottom <= 0 || box.right <= 0) continue;
                if (box.top >= innerHeight || box.left >= innerWidth) continue;
                const style = getComputedStyle(element);
                if (style.visibility === 'hidden' || style.display === 'none') continue;
                if (parseFloat(style.opacity) < 0.1) continue;
                const x = box.left + box.width / 2, y = box.top + box.height / 2;
                if (x < 0 || y < 0 || x >= innerWidth || y >= innerHeight) continue;
                const topmost = document.elementFromPoint(x, y);
                if (!topmost) continue;
                if (element !== topmost && !element.contains(topmost)
                    && !topmost.contains(element)) continue;
                const label = (element.innerText || element.value
                  || element.getAttribute('aria-label') || '').trim();
                rows.push([box.left, box.top, box.width, box.height,
                  element.tagName.toLowerCase(),
                  label.slice(0, 40).replace(/\\s+/g, ' ')].join('\\t'));
              }
              return rows.join('\\n');
            })()
            """;

    private static final Gson gson = new Gson();

    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private WebSocket socket;
    private int lastId;

    public static void main(String[] args) throws Exception {
        Path directory = Path.of("src/test/resources/vision");
        if (!Files.isDirectory(directory))
            throw new IllegalStateException("run from the project root: " + directory);
        Process chrome = launchChrome();
        try {
            for (String[] page : pages) {
                CaptureCorpus capture = new CaptureCorpus();
                try {
                    capture.open(newTabSocketUrl());
                    capture.capture(directory, page[0], page[1]);
                }
                catch (Exception e) {
                    System.out.printf("%-12s failed: %s%n", page[0], e);
                }
                finally {
                    capture.close();
                }
            }
        }
        finally {
            chrome.destroy();
            chrome.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private static Process launchChrome() throws Exception {
        Path profile = Files.createTempDirectory("mousemaster-capture");
        Process chrome = new ProcessBuilder(chromeBinary(), "--headless=new",
                "--remote-debugging-port=" + port, "--user-data-dir=" + profile,
                "--no-first-run", "--no-default-browser-check", "--hide-scrollbars",
                "--disable-extensions", "about:blank")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                get("/json/version");
                return chrome;
            }
            catch (Exception e) {
                Thread.sleep(100);
            }
        }
        chrome.destroy();
        throw new IllegalStateException("Chrome did not open a debugging port");
    }

    private static String chromeBinary() {
        List<String> candidates = List.of(
                "C:/Program Files/Google/Chrome/Application/chrome.exe",
                "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
                "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/usr/bin/google-chrome");
        for (String candidate : candidates)
            if (Files.isExecutable(Path.of(candidate)))
                return candidate;
        throw new IllegalStateException("no Chrome found in " + candidates);
    }

    private static String get(String path) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path)).build(),
                        HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new IllegalStateException(path + " returned " + response.statusCode());
        return response.body();
    }

    private static String newTabSocketUrl() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(
                                "http://127.0.0.1:" + port + "/json/new?about:blank"))
                        .method("PUT", HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(response.body()).getAsJsonObject()
                         .get("webSocketDebuggerUrl").getAsString();
    }

    private void open(String socketUrl) throws Exception {
        StringBuilder partial = new StringBuilder();
        socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(socketUrl), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(Long.MAX_VALUE);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data,
                                                     boolean last) {
                        partial.append(data);
                        if (last) {
                            messages.add(partial.toString());
                            partial.setLength(0);
                        }
                        return null;
                    }
                }).get(30, TimeUnit.SECONDS);
    }

    private void close() {
        if (socket != null)
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }

    private JsonObject send(String method, JsonObject params) throws Exception {
        int id = ++lastId;
        JsonObject command = new JsonObject();
        command.addProperty("id", id);
        command.addProperty("method", method);
        command.add("params", params);
        socket.sendText(gson.toJson(command), true).get(30, TimeUnit.SECONDS);
        for (long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
             System.nanoTime() < deadline; ) {
            String message = messages.poll(1, TimeUnit.SECONDS);
            if (message == null)
                continue;
            JsonObject object = JsonParser.parseString(message).getAsJsonObject();
            if (object.has("id") && object.get("id").getAsInt() == id) {
                if (object.has("error"))
                    throw new IllegalStateException(method + ": " + object.get("error"));
                return object.getAsJsonObject("result");
            }
        }
        throw new IllegalStateException(method + " timed out");
    }

    private static JsonObject params(Object... keysAndValues) {
        JsonObject params = new JsonObject();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            String key = (String) keysAndValues[i];
            Object value = keysAndValues[i + 1];
            if (value instanceof Number number)
                params.addProperty(key, number);
            else if (value instanceof Boolean flag)
                params.addProperty(key, flag);
            else
                params.addProperty(key, (String) value);
        }
        return params;
    }

    private void capture(Path directory, String name, String url) throws Exception {
        send("Emulation.setDeviceMetricsOverride",
                params("width", viewportWidth, "height", viewportHeight,
                        "deviceScaleFactor", scale, "mobile", false));
        send("Page.enable", params());
        send("Page.navigate", params("url", url));
        Thread.sleep(settleMillis);
        String rows = send("Runtime.evaluate",
                params("expression", targetsScript, "returnByValue", true))
                .getAsJsonObject("result").get("value").getAsString();
        byte[] png = Base64.getDecoder().decode(
                send("Page.captureScreenshot", params("format", "png")).get("data")
                        .getAsString());
        Files.write(directory.resolve(name + ".png"), png);
        List<String> targets = new ArrayList<>();
        for (String row : rows.isEmpty() ? new String[0] : rows.split("\n")) {
            String[] parts = row.split("\t", -1);
            int x = (int) Math.round(Double.parseDouble(parts[0]) * scale);
            int y = (int) Math.round(Double.parseDouble(parts[1]) * scale);
            int right = x + (int) Math.round(Double.parseDouble(parts[2]) * scale);
            int bottom = y + (int) Math.round(Double.parseDouble(parts[3]) * scale);
            String target = x + "\t" + y + "\t" + right + "\t" + bottom + "\t" + parts[4]
                            + "\t" + parts[5];
            if (targets.stream().noneMatch(kept -> nearlySame(kept, target)))
                targets.add(target);
        }
        Files.write(directory.resolve(name + ".tsv"), targets);
        System.out.printf("%-12s %d targets, %d KB%n", name, targets.size(),
                png.length / 1024);
    }

    private static boolean nearlySame(String kept, String target) {
        String[] one = kept.split("\t"), other = target.split("\t");
        return Math.abs(Integer.parseInt(one[0]) - Integer.parseInt(other[0])) < 6
               && Math.abs(Integer.parseInt(one[1]) - Integer.parseInt(other[1])) < 6;
    }
}
