package jmouseable.jmouseable;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class JmouseableApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(JmouseableApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        ModeMap modeMap = new ModeMap(Map.of(Mode.defaultMode(), new ComboMap(Map.of())));
        new WindowsHook(new MouseMover(10, 100), new ComboWatcher(modeMap)).installHooks();
    }

}
