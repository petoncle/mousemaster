package jmouseable.jmouseable;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JmouseableApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(JmouseableApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        new WindowsHook(new MouseMover(10, 100)).installHooks();
    }

}
