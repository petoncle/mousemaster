package mousemaster.qt;

import io.qt.gui.QFont;
import io.qt.gui.QFontMetrics;
import io.qt.widgets.QApplication;
import mousemaster.FontStyle;
import mousemaster.FontWeight;
import mousemaster.HintFontStyle;
import mousemaster.HintMeshConfiguration;
import mousemaster.HintMeshStyle;
import mousemaster.Shadow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds Qt font styles from the cross-platform hint font model.
 */
public final class QtHintFont {

    private static final Logger logger = LoggerFactory.getLogger(QtHintFont.class);

    private QtHintFont() {
    }

    public static QFont qFont(String fontName, double fontSize, FontWeight fontWeight) {
        QFont font = new QFont(fontName, (int) Math.round(fontSize),
                fontWeight.qtWeight().value());
        font.setStyleStrategy(QFont.StyleStrategy.PreferAntialias);
        font.setHintingPreference(QFont.HintingPreference.PreferFullHinting);
        return font;
    }

    /** One Qt font style (font + DPI-corrected metrics + colors) for a single FontStyle,
     *  e.g. a decoration label. */
    public static QtFontStyle qtFontStyle(FontStyle fontStyle, double screenScale) {
        QFont font = qFont(fontStyle.name(), fontStyle.size(), fontStyle.weight());
        QFontMetrics metrics = correctedFontMetricsForScreenDpi(font, fontStyle.size(), screenScale);
        return qtFontStyle(fontStyle, font, metrics, screenScale);
    }

    public static QtHintFontStyle qtHintFontStyle(HintFontStyle hintFontStyle,
                                                  HintFontStyle prefixHintFontStyle,
                                                  double screenScale,
                                                  boolean hasSelectedKeys) {
        FontStyle defaultFontStyle = hintFontStyle.defaultFontStyle();
        FontStyle selectedFontStyle = hintFontStyle.selectedFontStyle();
        FontStyle focusedFontStyle = hintFontStyle.focusedFontStyle();
        boolean perKeyFont = (hasSelectedKeys && !fontShapeEquals(defaultFontStyle, selectedFontStyle)) ||
                             !fontShapeEquals(defaultFontStyle, focusedFontStyle);
        if (prefixHintFontStyle != null) {
            perKeyFont = perKeyFont ||
                         !fontShapeEquals(defaultFontStyle, prefixHintFontStyle.defaultFontStyle()) ||
                         (hasSelectedKeys && !fontShapeEquals(defaultFontStyle, prefixHintFontStyle.selectedFontStyle())) ||
                         !fontShapeEquals(defaultFontStyle, prefixHintFontStyle.focusedFontStyle());
        }
        Shadow defaultShadow = defaultFontStyle.shadow();
        boolean perKeyShadow =
                (hasSelectedKeys && shadowsNeedPerKeyProcessing(defaultShadow, selectedFontStyle.shadow())) ||
                shadowsNeedPerKeyProcessing(defaultShadow, focusedFontStyle.shadow());
        if (prefixHintFontStyle != null) {
            perKeyShadow = perKeyShadow ||
                           shadowsNeedPerKeyProcessing(defaultShadow, prefixHintFontStyle.defaultFontStyle().shadow()) ||
                           (hasSelectedKeys && shadowsNeedPerKeyProcessing(defaultShadow, prefixHintFontStyle.selectedFontStyle().shadow())) ||
                           shadowsNeedPerKeyProcessing(defaultShadow, prefixHintFontStyle.focusedFontStyle().shadow());
        }
        QFont defaultFont = qFont(defaultFontStyle.name(), defaultFontStyle.size(), defaultFontStyle.weight());
        QFontMetrics defaultMetrics = correctedFontMetricsForScreenDpi(defaultFont, defaultFontStyle.size(), screenScale);
        QtFontStyle defaultQtFontStyle = qtFontStyle(defaultFontStyle, defaultFont, defaultMetrics, screenScale);
        QtFontStyle selectedQtFontStyle;
        QtFontStyle focusedQtFontStyle;
        if (perKeyFont) {
            QFont selectedFont = qFont(selectedFontStyle.name(), selectedFontStyle.size(), selectedFontStyle.weight());
            QFontMetrics selectedMetrics = correctedFontMetricsForScreenDpi(selectedFont, selectedFontStyle.size(), screenScale);
            selectedQtFontStyle = qtFontStyle(selectedFontStyle, selectedFont, selectedMetrics, screenScale);
            QFont focusedFont = qFont(focusedFontStyle.name(), focusedFontStyle.size(), focusedFontStyle.weight());
            QFontMetrics focusedMetrics = correctedFontMetricsForScreenDpi(focusedFont, focusedFontStyle.size(), screenScale);
            focusedQtFontStyle = qtFontStyle(focusedFontStyle, focusedFont, focusedMetrics, screenScale);
        }
        else {
            selectedQtFontStyle = qtFontStyle(selectedFontStyle, defaultFont, defaultMetrics, screenScale);
            focusedQtFontStyle = qtFontStyle(focusedFontStyle, defaultFont, defaultMetrics, screenScale);
        }
        QtFontStyle prefixDefaultQtFontStyle = null;
        QtFontStyle prefixSelectedQtFontStyle = null;
        QtFontStyle prefixFocusedQtFontStyle = null;
        if (prefixHintFontStyle != null) {
            FontStyle prefixDefaultFs = prefixHintFontStyle.defaultFontStyle();
            FontStyle prefixSelectedFs = prefixHintFontStyle.selectedFontStyle();
            FontStyle prefixFocusedFs = prefixHintFontStyle.focusedFontStyle();
            if (perKeyFont) {
                QFont prefixDefaultFont = qFont(prefixDefaultFs.name(), prefixDefaultFs.size(), prefixDefaultFs.weight());
                QFontMetrics prefixDefaultMetrics = correctedFontMetricsForScreenDpi(prefixDefaultFont, prefixDefaultFs.size(), screenScale);
                prefixDefaultQtFontStyle = qtFontStyle(prefixDefaultFs, prefixDefaultFont, prefixDefaultMetrics, screenScale);
                QFont prefixSelectedFont = qFont(prefixSelectedFs.name(), prefixSelectedFs.size(), prefixSelectedFs.weight());
                QFontMetrics prefixSelectedMetrics = correctedFontMetricsForScreenDpi(prefixSelectedFont, prefixSelectedFs.size(), screenScale);
                prefixSelectedQtFontStyle = qtFontStyle(prefixSelectedFs, prefixSelectedFont, prefixSelectedMetrics, screenScale);
                QFont prefixFocusedFont = qFont(prefixFocusedFs.name(), prefixFocusedFs.size(), prefixFocusedFs.weight());
                QFontMetrics prefixFocusedMetrics = correctedFontMetricsForScreenDpi(prefixFocusedFont, prefixFocusedFs.size(), screenScale);
                prefixFocusedQtFontStyle = qtFontStyle(prefixFocusedFs, prefixFocusedFont, prefixFocusedMetrics, screenScale);
            }
            else {
                prefixDefaultQtFontStyle = qtFontStyle(prefixDefaultFs, defaultFont, defaultMetrics, screenScale);
                prefixSelectedQtFontStyle = qtFontStyle(prefixSelectedFs, defaultFont, defaultMetrics, screenScale);
                prefixFocusedQtFontStyle = qtFontStyle(prefixFocusedFs, defaultFont, defaultMetrics, screenScale);
            }
        }
        return new QtHintFontStyle(defaultQtFontStyle, selectedQtFontStyle, focusedQtFontStyle,
                prefixDefaultQtFontStyle, prefixSelectedQtFontStyle, prefixFocusedQtFontStyle,
                perKeyFont, perKeyShadow, hintFontStyle.spacingPercent());
    }

    private static QtFontStyle qtFontStyle(FontStyle fs, QFont font,
                                           QFontMetrics metrics,
                                           double screenScale) {
        return new QtFontStyle(
                font, metrics,
                QtColorUtil.qColor(fs.hexColor(), fs.opacity()),
                QtColorUtil.qColor(fs.outlineHexColor(), fs.outlineOpacity()),
                (int) Math.round(fs.outlineThickness() * screenScale),
                QtColorUtil.shadow(fs.shadow()),
                fs.shadow().stackCount(),
                fs.shadow().blurRadius() * screenScale,
                fs.shadow().horizontalOffset() * screenScale,
                fs.shadow().verticalOffset() * screenScale
        );
    }

    /**
     * Returns true if the two shadows differ and at least one has non-zero opacity.
     * When both are zero-opacity neither renders, so no per-key processing is needed.
     * When one is zero and the other is not, per-key processing is needed to exclude
     * the zero-opacity keys from the other's shadow.
     */
    private static boolean shadowsNeedPerKeyProcessing(Shadow a, Shadow b) {
        return (a.opacity() != 0 || b.opacity() != 0) && !a.equals(b);
    }

    private static boolean fontShapeEquals(FontStyle a, FontStyle b) {
        return a.name().equals(b.name()) &&
               a.weight() == b.weight() &&
               Double.compare(a.size(), b.size()) == 0;
    }

    /**
     * GDI renders text at the monitor's actual DPI, but QFontMetrics uses Qt's
     * logical DPI (the primary screen's DPI when QT_ENABLE_HIGHDPI_SCALING=0).
     * On multi-monitor setups with different scales, create a pixel-size font
     * for metrics so they match the actual GDI rendering on the target screen.
     *
     * Note: we use the primary screen's DPI rather than window.logicalDpiX()
     * because QFontMetrics always resolves point-size fonts using the primary
     * screen's DPI, but window.logicalDpiX() can change after the window is
     * shown on a non-primary screen (Qt detects the monitor DPI), creating a
     * false "no correction needed" match on subsequent calls.
     */
    private static QFontMetrics correctedFontMetricsForScreenDpi(QFont renderFont,
                                                                 double fontSizePoints,
                                                                 double screenScale) {
        double primaryScreenDpi = QApplication.primaryScreen().logicalDotsPerInchX();
        double targetDpi = screenScale * 96.0;
        if (Math.abs(primaryScreenDpi - targetDpi) < 1) {
            // QFontMetrics already matches the target DPI, no correction needed.
            return new QFontMetrics(renderFont);
        }
        // Create a metrics-only font with the pixel size that GDI will use.
        int correctedPixelSize = (int) Math.round(fontSizePoints * targetDpi / 72.0);
        QFont metricsFont = new QFont(renderFont.family());
        metricsFont.setPixelSize(correctedPixelSize);
        metricsFont.setWeight(renderFont.weight());
        metricsFont.setStyleStrategy(QFont.StyleStrategy.PreferAntialias);
        metricsFont.setHintingPreference(QFont.HintingPreference.PreferFullHinting);
        QFontMetrics metrics = new QFontMetrics(metricsFont);
        metricsFont.dispose();
        return metrics;
    }

    public static void preWarm(Set<HintMeshConfiguration> hintMeshConfigurations) {
        long before = System.nanoTime();
        Set<HintFontStyle> fontStyles = new HashSet<>();
        for (HintMeshConfiguration hintMeshConfiguration : hintMeshConfigurations) {
            for (HintMeshStyle style : hintMeshConfiguration.styleByFilter().map().values()) {
                fontStyles.add(style.fontStyle());
                fontStyles.add(style.prefixFontStyle());
            }
        }
        for (HintFontStyle hintFontStyle : fontStyles) {
            QFont font = qFont(hintFontStyle.defaultFontStyle().name(), hintFontStyle.defaultFontStyle().size(), hintFontStyle.defaultFontStyle().weight());
            QFontMetrics fontMetrics = new QFontMetrics(font);
            fontMetrics.horizontalAdvance("x");
            fontMetrics.dispose();
            font.dispose();
            if (!fontShapeEquals(hintFontStyle.defaultFontStyle(), hintFontStyle.selectedFontStyle())) {
                QFont selectedFont = qFont(hintFontStyle.selectedFontStyle().name(), hintFontStyle.selectedFontStyle().size(), hintFontStyle.selectedFontStyle().weight());
                QFontMetrics selectedFontMetrics = new QFontMetrics(selectedFont);
                selectedFontMetrics.horizontalAdvance("x");
                selectedFontMetrics.dispose();
                selectedFont.dispose();
            }
            if (!fontShapeEquals(hintFontStyle.defaultFontStyle(), hintFontStyle.focusedFontStyle())) {
                QFont focusedFont = qFont(hintFontStyle.focusedFontStyle().name(), hintFontStyle.focusedFontStyle().size(), hintFontStyle.focusedFontStyle().weight());
                QFontMetrics focusedFontMetrics = new QFontMetrics(focusedFont);
                focusedFontMetrics.horizontalAdvance("x");
                focusedFontMetrics.dispose();
                focusedFont.dispose();
            }
        }
        logger.debug("Pre-warmed " + fontStyles.size() + " hint font styles in " +
                (long) ((System.nanoTime() - before) / 1e6) + "ms");
    }
}
