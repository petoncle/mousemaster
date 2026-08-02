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
            // Handle line continuation. A trailing backslash continues the line,
            // unless that backslash is itself escaped.
            if (trailingBackslashCount(line) % 2 == 1)
                property.append(line, 0, line.length() - 1);
            else {
                property.append(line);
                String fullLine = property.toString();
                property.setLength(0);
                properties.add(fullLine.replace("\\\\", "\\"));
            }
        }
        return properties;
    }

    private static int trailingBackslashCount(String line) {
        int count = 0;
        for (int charIndex = line.length() - 1;
             charIndex >= 0 && line.charAt(charIndex) == '\\'; charIndex--)
            count++;
        return count;
    }
}
