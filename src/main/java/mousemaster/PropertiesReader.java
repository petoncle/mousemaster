package mousemaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PropertiesReader {

    /**
     * Unlike {@link Properties}, this parser keeps properties with same key so an error
     * can be shown to the user.
     */
    public static List<String> readPropertiesFile(BufferedReader reader) throws
            IOException {
        List<String> properties = new ArrayList<>();
        String line;
        StringBuilder property = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            line = line.strip();
            if (line.isBlank())
                continue;
            if (property.isEmpty() && (line.startsWith("#") || line.startsWith("!")))
                continue;
            // Handle line continuation.
            if (line.endsWith("\\"))
                property.append(line, 0, line.length() - 1);
            else {
                property.append(line);
                String fullLine = property.toString();
                property.setLength(0);
                properties.add(fullLine);
            }
        }
        return properties;
    }
}
