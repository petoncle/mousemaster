package jmouseable.jmouseable;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JmouseableApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(JmouseableApplication.class, args);
    }

    private final ModeMapParser modeMapParser;

    public JmouseableApplication(ModeMapParser modeMapParser) {
        this.modeMapParser = modeMapParser;
    }

    @Override
    public void run(String... args) throws InterruptedException {
        ModeMap modeMap = modeMapParser.parse();
        new WindowsHook(new MouseMover(modeMap.get(Mode.DEFAULT_MODE_NAME).mouse()),
                new ComboWatcher(modeMap)).installHooks();
    }

}
