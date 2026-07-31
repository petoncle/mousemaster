package mousemaster;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Licenses {

    private static final String[] documents =
            {"THIRD-PARTY-NOTICES.md", "LGPL-3.0.txt", "GPL-3.0.txt", "LGPL-2.1.txt",
             "Apache-2.0.txt", "EPL-1.0.txt", "MIT-SLF4J.txt", "MIT-jsoup.txt"};

    public static void print() throws IOException {
        for (String document : documents) {
            System.out.println("========== " + document + " ==========");
            System.out.println();
            try (InputStream inputStream = Licenses.class.getResourceAsStream(
                    "/licenses/" + document)) {
                System.out.println(
                        new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

}
