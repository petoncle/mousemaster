package mousemaster;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.sun.jna.Native;
import io.qt.core.Qt;
import io.qt.gui.*;
import io.qt.widgets.*;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class MousemasterApplication {

    private static final Logger logger;

    static {
        System.setProperty("slf4j.internal.verbosity", "WARN");
        logger = (Logger) LoggerFactory.getLogger(MousemasterApplication.class);
    }

    public static class TransparentWindow extends QWidget {
        public TransparentWindow() {
            // WindowDoesNotAcceptFocus is not implemented for Windows.
            setWindowFlags(Qt.WindowType.FramelessWindowHint);
            setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);

            // Create a QLabel with styled text
            QLabel label = new CachedLabel("Hellop World!", "Consolas", 72);

            // Enable drop shadow effect
            QGraphicsDropShadowEffect shadow = new QGraphicsDropShadowEffect();
            shadow.setBlurRadius(30);
            shadow.setOffset(5, 5);
            shadow.setColor(new QColor(Qt.GlobalColor.red));
            label.setGraphicsEffect(shadow);

            // Set layout
            QVBoxLayout layout = new QVBoxLayout();
            layout.addWidget(label);
            setLayout(layout);
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);

            // Draw a semi-transparent black rectangle
            painter.setRenderHint(QPainter.RenderHint.Antialiasing);
            painter.setBrush(
                    new QColor(0, 0, 0, 76)); // Black with 30% opacity (76 = 255 * 0.3)
            painter.setPen(Qt.PenStyle.NoPen);
            painter.drawRect(0, 0, width(), height());

            painter.end();
        }
    }

    public static class CachedLabel extends QLabel {

        private int textWidth;
        private int textHeight;
        private QStaticText cachedStaticText;
        private QPainterPath cachedOutlinePath;
        private int ascent;

        public CachedLabel(String text, String fontFamily, int fontSize) {
            super(text);
            QFont font = new QFont(fontFamily, fontSize);
//            font.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, 30);
            setFont(font);
            setAlignment(Qt.AlignmentFlag.AlignCenter);
            updateCache();
        }

        private void updateCache() {
            QFont font = this.font();
            QFontMetrics metrics = new QFontMetrics(font);
            textWidth = metrics.horizontalAdvance(this.text());
            textHeight = metrics.height();
            ascent = metrics.ascent();

            cachedStaticText = new QStaticText(text());
            cachedStaticText.setTextFormat(Qt.TextFormat.PlainText);
            cachedStaticText.setPerformanceHint(QStaticText.PerformanceHint.AggressiveCaching);

            cachedOutlinePath = new QPainterPath();
            cachedOutlinePath.addText(0, 0, font, text());
        }


        @Override
        protected void paintEvent(QPaintEvent event) {
            System.out.println("OutlinedLabel.paintEvent");

            QPainter painter = new QPainter(this);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.TextAntialiasing, true);
            int x = (width() - textWidth) / 2;
            int y = (height() - textHeight) / 2;

            QPen outlinePen = new QPen(new QColor(Qt.GlobalColor.black));
            outlinePen.setWidth(5);
            outlinePen.setJoinStyle(Qt.PenJoinStyle.RoundJoin);
            painter.setPen(outlinePen);
            painter.drawPath(cachedOutlinePath.translated(x, y + ascent));

            // Avoid blending the text with the outline. Text should override the outline.
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);

            painter.setPen(new QColor(255, 255, 255, 100));
            painter.drawStaticText(x, y, cachedStaticText);

            painter.end();

//            QPainter painter = new QPainter(this);
//            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
//
//            QFont font = this.font();
//            painter.setFont(font);
//
//            QFontMetrics metrics = new QFontMetrics(font);
//            int textWidth = metrics.horizontalAdvance(this.text());
//            int textHeight = metrics.height();
//
//            // Center text within QLabel
//            int x = (width() - textWidth) / 2;
//            int y = (height() + textHeight) / 2 - metrics.descent(); // Adjust for font descent
//
//            // Create a QPainterPath for the outlined text
//            QPainterPath path = new QPainterPath();
//            path.addText(x, y, font, this.text());
//
//            QPen outlinePen = new QPen(new QColor(0, 0, 0, 200));
//            outlinePen.setWidth(2); // Outline thickness
//            outlinePen.setJoinStyle(Qt.PenJoinStyle.RoundJoin);
//            painter.setPen(outlinePen);
////            painter.setBrush(Qt.BrushStyle.NoBrush);
//            painter.drawPath(path);
//
//            painter.setPen(new QColor(255, 255, 255, 120));
//            painter.drawText(x, y, this.text());
//
//            painter.end();
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        System.setProperty("io.qt.library-path-override", "C:\\Users\\timot\\IdeaProjects\\mousemaster\\qt\\bin");
        QApplication.initialize(new String[]{});
        TransparentWindow window = new TransparentWindow();
        window.resize(500, 200);
        window.show();

        while (true) {
            QApplication.processEvents();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        QApplication.shutdown();

        Stream.of(args)
              .filter(arg -> arg.startsWith("--log-level="))
              .map(arg -> arg.split("=")[1])
              .findFirst()
              .ifPresent(MousemasterApplication::setLogLevel);
        String version;
        try (InputStream versionInputStream = MousemasterApplication.class.getClassLoader().getResourceAsStream("application.properties")) {
            Properties versionProp = new Properties();
            versionProp.load(versionInputStream);
            version = versionProp.getProperty("version");
        }
        Path configurationPath = Stream.of(args)
                                       .filter(arg -> arg.startsWith(
                                               "--configuration-file="))
                                       .map(arg -> arg.split("=")[1])
                                       .findFirst()
                                       .map(Paths::get)
                                       .orElse(Paths.get("mousemaster.properties"));
        boolean keyRegurgitationEnabled = // Feature flag.
                Stream.of(args)
                      .filter(arg -> arg.startsWith("--key-regurgitation-enabled="))
                      .map(arg -> arg.split("=")[1])
                      .findFirst()
                      .map(Boolean::parseBoolean)
                      .orElse(true); // Remove this feature flag if confirmed working
        if (Stream.of(args).anyMatch(Predicate.isEqual(("--graalvm-agent-run")))) {
            logger.info("--graalvm-agent-run flag found, exiting in 20s");
            new Thread(() -> {
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.exit(0);
            }).start();
        }
        WindowsPlatform platform = platform(keyRegurgitationEnabled);
        logger.info("mousemaster v" + version);
        if (platform == null)
            return;
        try {
            Native.setCallbackExceptionHandler((c, e) -> shutdownAfterException(e, platform, true));
            new Mousemaster(configurationPath, platform).run();
        } catch (Throwable e) {
            shutdownAfterException(e, platform, false);
        }
    }

    private static WindowsPlatform platform(boolean keyRegurgitationEnabled) {
        try {
            return new WindowsPlatform(keyRegurgitationEnabled);
        } catch (Exception e) {
            shutdownAfterException(e, null, false);
        }
        return null;
    }

    private static void shutdownAfterException(Throwable e, Platform platform, boolean jnaCallback) {
        if (platform != null)
            platform.shutdown();
        logger.error(jnaCallback ? "Error in JNA callback" : "", e);
        logger.info(
                "An error has occurred. The details of the error should be right above this message. Press Enter in this window to close mousemaster.");
        new Scanner(System.in).nextLine();
        System.exit(1);
    }

    public static void setLogLevel(String level) {
       Logger logger = (Logger) LoggerFactory.getLogger("mousemaster");
       logger.setLevel(Level.valueOf(level));
    }

}
