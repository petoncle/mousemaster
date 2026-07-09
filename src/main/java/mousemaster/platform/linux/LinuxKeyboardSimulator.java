package mousemaster.platform.linux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Temporary keyboard input simulator for testing on Wayland.
 * Reads keyboard input from stdin in a separate thread.
 * This is a workaround until proper evdev support is implemented.
 */
public class LinuxKeyboardSimulator {
    private static final Logger logger = LoggerFactory.getLogger(LinuxKeyboardSimulator.class);

    private final BlockingQueue<String> keyQueue = new LinkedBlockingQueue<>();
    private Thread inputThread;
    private volatile boolean running = false;

    public void start() {
        if (running) return;

        running = true;
        inputThread = new Thread(this::readInput, "KeyboardSimulator");
        inputThread.setDaemon(true);
        inputThread.start();

        logger.info("Keyboard simulator started (reading from stdin)");
        logger.info("Type single letters and press Enter to simulate keypresses");
    }

    public void stop() {
        running = false;
        if (inputThread != null) {
            inputThread.interrupt();
        }
    }

    private void readInput() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (running) {
                if (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (!line.isEmpty()) {
                        for (char c : line.toCharArray()) {
                            String key = String.valueOf(c);
                            keyQueue.offer(key.toLowerCase());
                            logger.debug("Queued key: {}", key);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error reading keyboard input", e);
        }
    }

    public String pollKey() {
        return keyQueue.poll();
    }

    public boolean hasKeys() {
        return !keyQueue.isEmpty();
    }
}
