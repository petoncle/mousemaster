package mousemaster;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KbdlayoutinfoParser {

    private static final Logger logger = LoggerFactory.getLogger(KbdlayoutinfoParser.class);

    private static final Key notImplementedKey = new Key(null, null, null);

    private static final Map<String, String> keyboardLayoutShortNameByIdentifier = Map.ofEntries(
            Map.entry("00000804", "zh-qwerty-pinyin"),
            Map.entry("0000040C", "fr-azerty"),
            Map.entry("00000407", "de-qwertz"),
            Map.entry("00000410", "it-qwerty"),
            Map.entry("00000411", "jp-kana"),
            Map.entry("00010416", "pt-qwerty-abnt2"),
            Map.entry("00000419", "ru-jcuken"),
            Map.entry("0000040A", "es-qwerty"),
            Map.entry("00000409", "us-qwerty"),
            Map.entry("00000809", "uk-qwerty"),
            Map.entry("00010409", "us-dvorak"),
            Map.entry("0000041D", "sv-qwerty")
    );

    private static final Map<WindowsVirtualKey, Key> keyboardLayoutIndependentWithTextVirtualKeys = Map.ofEntries(
            Map.entry(WindowsVirtualKey.VK_SPACE, Key.space)
    );

    private static final Map<WindowsVirtualKey, Key> noTextVirtualKeys = Map.ofEntries(
            Map.entry(WindowsVirtualKey.VK_APPS, Key.menu),
            Map.entry(WindowsVirtualKey.VK_BACK, Key.backspace),
            Map.entry(WindowsVirtualKey.VK_BROWSER_BACK, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_BROWSER_FAVORITES, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_BROWSER_FORWARD, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_BROWSER_HOME, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_BROWSER_REFRESH, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_BROWSER_SEARCH, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_BROWSER_STOP, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_CANCEL, Key.break_),
            Map.entry(WindowsVirtualKey.VK_CAPITAL, Key.capslock),
            Map.entry(WindowsVirtualKey.VK_CLEAR, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_CONTROL, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_DELETE, Key.del),
            Map.entry(WindowsVirtualKey.VK_DOWN, Key.downarrow),
            Map.entry(WindowsVirtualKey.VK_END, Key.end),
            Map.entry(WindowsVirtualKey.VK_ESCAPE, Key.esc),
            Map.entry(WindowsVirtualKey.VK_F1, Key.f1),
            Map.entry(WindowsVirtualKey.VK_F2, Key.f2),
            Map.entry(WindowsVirtualKey.VK_F3, Key.f3),
            Map.entry(WindowsVirtualKey.VK_F4, Key.f4),
            Map.entry(WindowsVirtualKey.VK_F5, Key.f5),
            Map.entry(WindowsVirtualKey.VK_F6, Key.f6),
            Map.entry(WindowsVirtualKey.VK_F7, Key.f7),
            Map.entry(WindowsVirtualKey.VK_F8, Key.f8),
            Map.entry(WindowsVirtualKey.VK_F9, Key.f9),
            Map.entry(WindowsVirtualKey.VK_F10, Key.f10),
            Map.entry(WindowsVirtualKey.VK_F11, Key.f11),
            Map.entry(WindowsVirtualKey.VK_F12, Key.f12),
            Map.entry(WindowsVirtualKey.VK_F13, Key.f13),
            Map.entry(WindowsVirtualKey.VK_F14, Key.f14),
            Map.entry(WindowsVirtualKey.VK_F15, Key.f15),
            Map.entry(WindowsVirtualKey.VK_F16, Key.f16),
            Map.entry(WindowsVirtualKey.VK_F17, Key.f17),
            Map.entry(WindowsVirtualKey.VK_F18, Key.f18),
            Map.entry(WindowsVirtualKey.VK_F19, Key.f19),
            Map.entry(WindowsVirtualKey.VK_F20, Key.f20),
            Map.entry(WindowsVirtualKey.VK_F21, Key.f21),
            Map.entry(WindowsVirtualKey.VK_F22, Key.f22),
            Map.entry(WindowsVirtualKey.VK_F23, Key.f23),
            Map.entry(WindowsVirtualKey.VK_F24, Key.f24),
            Map.entry(WindowsVirtualKey.VK_HELP, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_HOME, Key.home),
            Map.entry(WindowsVirtualKey.VK_INSERT, Key.insert),
            Map.entry(WindowsVirtualKey.VK_LAUNCH_APP1, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_LAUNCH_APP2, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_LAUNCH_MAIL, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_LAUNCH_MEDIA_SELECT, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_LCONTROL, Key.leftctrl),
            Map.entry(WindowsVirtualKey.VK_LEFT, Key.leftarrow),
            Map.entry(WindowsVirtualKey.VK_LMENU, Key.leftalt),
            Map.entry(WindowsVirtualKey.VK_LSHIFT, Key.leftshift),
            Map.entry(WindowsVirtualKey.VK_LWIN, Key.leftwin),
            Map.entry(WindowsVirtualKey.VK_MEDIA_NEXT_TRACK, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_MEDIA_PLAY_PAUSE, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_MEDIA_PREV_TRACK, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_MEDIA_STOP, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_NEXT, Key.pagedown),
            Map.entry(WindowsVirtualKey.VK_NUMPAD0, Key.numpad0),
            Map.entry(WindowsVirtualKey.VK_NUMPAD1, Key.numpad1),
            Map.entry(WindowsVirtualKey.VK_NUMPAD2, Key.numpad2),
            Map.entry(WindowsVirtualKey.VK_NUMPAD3, Key.numpad3),
            Map.entry(WindowsVirtualKey.VK_NUMPAD4, Key.numpad4),
            Map.entry(WindowsVirtualKey.VK_NUMPAD5, Key.numpad5),
            Map.entry(WindowsVirtualKey.VK_NUMPAD6, Key.numpad6),
            Map.entry(WindowsVirtualKey.VK_NUMPAD7, Key.numpad7),
            Map.entry(WindowsVirtualKey.VK_NUMPAD8, Key.numpad8),
            Map.entry(WindowsVirtualKey.VK_NUMPAD9, Key.numpad9),
            Map.entry(WindowsVirtualKey.VK_MULTIPLY, Key.numpadmultiply),
            Map.entry(WindowsVirtualKey.VK_ADD, Key.numpadadd),
            Map.entry(WindowsVirtualKey.VK_SUBTRACT, Key.numpadsubtract),
            Map.entry(WindowsVirtualKey.VK_DECIMAL, Key.numpaddecimal),
            Map.entry(WindowsVirtualKey.VK_DIVIDE, Key.numpaddivide),
            Map.entry(WindowsVirtualKey.VK_NUMLOCK, Key.numlock),
            Map.entry(WindowsVirtualKey.VK_PAUSE, Key.pause),
            Map.entry(WindowsVirtualKey.VK_PRIOR, Key.pageup),
            Map.entry(WindowsVirtualKey.VK_RCONTROL, Key.rightctrl),
            Map.entry(WindowsVirtualKey.VK_RETURN, Key.enter),
            Map.entry(WindowsVirtualKey.VK_RIGHT, Key.rightarrow),
            Map.entry(WindowsVirtualKey.VK_RMENU, Key.rightalt),
            Map.entry(WindowsVirtualKey.VK_RSHIFT, Key.rightshift),
            Map.entry(WindowsVirtualKey.VK_RWIN, Key.rightwin),
            Map.entry(WindowsVirtualKey.VK_SCROLL, Key.scrolllock),
            Map.entry(WindowsVirtualKey.VK_SHIFT, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_SLEEP, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_SNAPSHOT, Key.printscreen),
            Map.entry(WindowsVirtualKey.VK_TAB, Key.tab),
            Map.entry(WindowsVirtualKey.VK_UP, Key.uparrow),
            Map.entry(WindowsVirtualKey.VK_VOLUME_DOWN, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_VOLUME_MUTE, notImplementedKey),
            Map.entry(WindowsVirtualKey.VK_VOLUME_UP, notImplementedKey)
    );

    public static void main(String[] args)
            throws IOException, ParserConfigurationException, SAXException {
        List<String> keyboardLayoutIdentifiers = parseRootPage();
//        keyboardLayoutIdentifiers.removeIf(Predicate.not(Predicate.isEqual("0000040C")));
        List<KeyboardLayout> keyboardLayouts = new ArrayList<>();
        for (String keyboardLayoutIdentifier : keyboardLayoutIdentifiers) {
            logger.info("Parsing keyboard layout page " + keyboardLayoutIdentifier);
            keyboardLayouts.add(parseKeyboardLayoutPage(keyboardLayoutIdentifier));
        }
        for (KeyboardLayout keyboardLayout : keyboardLayouts) {
            logger.info("Parsing keyboard layout XML for processing " + keyboardLayout);
            parseKeyboardLayoutXmlForProcessing(keyboardLayout);
        }
        addCustomKeyboardLayouts(keyboardLayouts);
        String filePath = Paths.get("src", "main", "resources", "keyboard-layouts.json").toString();
        Gson gson = new GsonBuilder().create();
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(keyboardLayouts, writer);
            logger.info("Save keyboard layouts: " + filePath);
        }
    }

    private static List<String> parseRootPage() throws IOException {
        List<String> keyboardLayoutIdentifiers = new ArrayList<>();
        org.jsoup.nodes.Document document =
                Jsoup.connect("https://kbdlayout.info/").get();
        Elements links = document.select("tr td a:not([href^=/kbd])");
        for (org.jsoup.nodes.Element link : links) {
            String href = link.attr("href");
            keyboardLayoutIdentifiers.add(href.replaceAll("/", "").toUpperCase());
        }
        return keyboardLayoutIdentifiers;
    }

    private static KeyboardLayout parseKeyboardLayoutPage(String keyboardLayoutIdentifier)
            throws IOException {
        org.jsoup.nodes.Document document =
                Jsoup.connect("https://kbdlayout.info/" + keyboardLayoutIdentifier).get();
        String displayName = null;
        String driverName = null;
        for (org.jsoup.nodes.Element th : document.select("th")) {
            if (th.text().equals("KLID")) {
                if (!th.nextElementSibling().text().replaceFirst(" .*", "").toUpperCase().equals(keyboardLayoutIdentifier))
                    // 00000813	Belgian (Period) and 0000080c Belgian French both link to https://kbdlayout.info/kbdbe
                    ;
//                    throw new IllegalStateException(th.nextElementSibling().text());
            }
            if (th.text().equals("Layout Display Name")) {
                displayName = th.nextElementSibling().text();
            }
            if (th.text().equals("Layout File")) {
                driverName = th.nextElementSibling().text().toLowerCase().replaceFirst("\\.dll", "");
            }
        }
        if (displayName == null || driverName == null)
            throw new IllegalStateException();
        return new KeyboardLayout(keyboardLayoutIdentifier, displayName, driverName,
                keyboardLayoutShortNameByIdentifier.get(keyboardLayoutIdentifier),
                new ArrayList<>());
    }

    private static void parseKeyboardLayoutXmlForProcessing(
            KeyboardLayout keyboardLayout)
            throws IOException, ParserConfigurationException, SAXException {
        String urlString = "https://kbdlayout.info/" + keyboardLayout.identifier() + "/download/xml";
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        InputStream inputStream = conn.getInputStream();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputStream);
        doc.getDocumentElement().normalize();
        NodeList physicalKeysList = doc.getElementsByTagName("PhysicalKeys");
        Element physicalKeys = (Element) physicalKeysList.item(0);
        NodeList pkList = physicalKeys.getElementsByTagName("PK");
        List<WindowsVirtualKey> noTextVirtualKeys = new ArrayList<>();
        for (int i = 0; i < pkList.getLength(); i++) {
            Element pk = (Element) pkList.item(i);
            WindowsVirtualKey vk = WindowsVirtualKey.valueOf(pk.getAttribute("VK"));
            int sc = Integer.parseUnsignedInt(pk.getAttribute("SC"), 16);
            String name = pk.getAttribute("Name");
            log("Key: VK=" + vk + ", SC=" + Integer.toHexString(sc) + ", Name=" + name);
            NodeList children = pk.getChildNodes();
            boolean noText = children.getLength() == 0;
            String text = null;
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals("Result")) {
                    Element result = (Element) child;
                    String with = result.getAttribute("With");
//                    if (true)
//                        continue;
                    log("  Result With: " + (with.isEmpty() ? "(none)" : with));

                    // Check if it has a DeadKeyTable
                    NodeList innerNodes = result.getChildNodes();
                    boolean hasDeadKey = false;
                    for (int k = 0; k < innerNodes.getLength(); k++) {
                        Node inner = innerNodes.item(k);
                        if (inner.getNodeType() == Node.ELEMENT_NODE && inner.getNodeName().equals("DeadKeyTable")) {
                            hasDeadKey = true;
                            Element deadKeyTable = (Element) inner;
                            String accent = deadKeyTable.getAttribute("Accent");
                            String dkName = deadKeyTable.getAttribute("Name");
                            log("    DeadKeyTable: Accent=" + accent + ", Name=" +
                                dkName);

                            NodeList dkResults = deadKeyTable.getElementsByTagName("Result");
                            for (int m = 0; m < dkResults.getLength(); m++) {
                                Element dkResult = (Element) dkResults.item(m);
                                String dkText = dkResult.getAttribute("Text");
                                String dkWith = dkResult.getAttribute("With");
                                log("      DeadKey Result: " + dkWith + " → " +
                                    dkText);
                                if (dkWith.isBlank() && with.isEmpty())
                                    text = dkText;
                            }
                        }
                    }

                    if (!hasDeadKey) {
                        // Just a regular Result (not a dead key).
                        String resultText = result.getAttribute("Text");
                        String codepoints = result.getAttribute("TextCodepoints");
                        if (with.isEmpty()) {
                            if (resultText.isEmpty())
                                noText = true;
                            text = resultText;
                        }
                        log(
                                "    Text=" + resultText + ", Codepoints=" + codepoints);
                    }
                }
            }
            if (noText) {
                if (!vk.name().contains("_OEM_") && !vk.name().contains("_DBE_") &&
                    !vk.name().contains("_ABNT"))
                    noTextVirtualKeys.add(vk);
            }
            Key key = null;
            if (KbdlayoutinfoParser.noTextVirtualKeys.containsKey(vk)) {
                key = KbdlayoutinfoParser.noTextVirtualKeys.get(vk);
                if (key == notImplementedKey)
                    key = null;
            }
            else if (KbdlayoutinfoParser.keyboardLayoutIndependentWithTextVirtualKeys.containsKey(vk))
                key = KbdlayoutinfoParser.keyboardLayoutIndependentWithTextVirtualKeys.get(vk);
            else if (text != null)
                key = Key.ofCharacter(text);
            if (key != null)
                keyboardLayout.keys()
                              .add(new KeyboardLayout.KeyboardLayoutKey(sc, vk, key,
                                      text == null || text.isBlank() ? null : text,
                                      name.isBlank() ? null : name));
        }
        noTextVirtualKeys.addAll(
                List.of(WindowsVirtualKey.VK_CONTROL, WindowsVirtualKey.VK_SHIFT));
//        log("noTextVirtualKeys = " + noTextVirtualKeys.stream().sorted(
//                Comparator.comparing(Enum::name)).distinct().toList());
//        log("keyboardLayout.keys() = " + keyboardLayout.keys());
        log(new GsonBuilder().create().toJson(keyboardLayout));
        inputStream.close();
        conn.disconnect();
    }

    private static void addCustomKeyboardLayouts(List<KeyboardLayout> keyboardLayouts) {
        String usQwertyCharacterKeys = """
                `1234567890-=
                qwertyuiop[]
                asdfghjkl;'\\
                zxcvbnm,./
                """.replaceAll("\\s", "");
        String usHalmakCharacterKeys = """
                `1234567890-=
                wlrbz;qudj[]
                shnt,.aeoi'\\
                fmvc/gpxky
                """.replaceAll("\\s", "");
        KeyboardLayout usQwertyLayout = keyboardLayouts.stream()
                                                       .filter(keyboardLayout -> "us-qwerty".equals(
                                                               keyboardLayout.shortName()))
                                                       .findFirst()
                                                       .orElseThrow();
        List<KeyboardLayout.KeyboardLayoutKey> usHalmakKeys = new ArrayList<>();
        for (KeyboardLayout.KeyboardLayoutKey usQwertyKey : usQwertyLayout.keys()) {
            if (usQwertyKey.key().character() == null) {
                usHalmakKeys.add(usQwertyKey);
                continue;
            }
            int characterKeyIndex = usQwertyCharacterKeys.indexOf(usQwertyKey.key().character());
            if (characterKeyIndex == -1) {
                usHalmakKeys.add(usQwertyKey);
                continue;
            }
            String usHalmakKeyCharacter = "" + usHalmakCharacterKeys.charAt(characterKeyIndex);
            KeyboardLayout.KeyboardLayoutKey sameCharacterUsQwertyKey =
                    usQwertyLayout.keys()
                                  .stream()
                                  .filter(keyboardLayoutKey -> usHalmakKeyCharacter.equals(
                                          keyboardLayoutKey.key()
                                                           .character()))
                                  .findFirst()
                                  .orElseThrow();
            KeyboardLayout.KeyboardLayoutKey usHalmakKey = new KeyboardLayout.KeyboardLayoutKey(
                    usQwertyKey.scanCode(),
                    sameCharacterUsQwertyKey.virtualKey(),
                    sameCharacterUsQwertyKey.key(),
                    sameCharacterUsQwertyKey.text(),
                    sameCharacterUsQwertyKey.name()
            );
            usHalmakKeys.add(usHalmakKey);
        }
        keyboardLayouts.add(
                new KeyboardLayout(null, null, null, "us-halmak", usHalmakKeys));
    }

    private static void log(String string) {
        System.out.println(string);
    }

}
