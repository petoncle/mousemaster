# Third-party notices

**mousemaster.exe** embeds the components below, and their use in it is covered by the licenses
shown.

| Component | Version | Copyright | License |
| --- | --- | --- | --- |
| [Qt](https://www.qt.io) (Qt6Core, Qt6Gui, Qt6Widgets and the Qt platform plugins) | 6.8.2 | (C) The Qt Company Ltd. and other contributors | LGPL-3.0-only |
| [QtJambi](https://github.com/OmixVisualization/qtjambi) | 6.8.2 | (C) OmixVisualization and other contributors | LGPL-2.1 (elected) or GPL-3.0 |
| [JNA](https://github.com/java-native-access/jna) | 5.13.0 | (C) Timothy Wall and other contributors | Apache-2.0 (elected) or LGPL-2.1-or-later |
| [logback](https://logback.qos.ch) | 1.5.15 | (C) QOS.ch Sarl | EPL-1.0 (elected) or LGPL-2.1 |
| [SLF4J](https://www.slf4j.org) | 2.0.15 | (C) QOS.ch Sarl | MIT |
| [Gson](https://github.com/google/gson) | 2.12.1 | (C) Google Inc. | Apache-2.0 |
| [jsoup](https://jsoup.org) | 1.19.1 | (C) Jonathan Hedley | MIT |

The text of every license shown accompanies this file, SLF4J's and jsoup's MIT texts as
MIT-SLF4J.txt and MIT-jsoup.txt.

Qt and QtJambi are embedded unmodified. Their source is available from
https://code.qt.io/cgit/qt/qtbase.git and https://github.com/OmixVisualization/qtjambi.

`src/main/java/mousemaster/qt/ExpBlur.java` and `QtDropShadowEffect.java` are ports of Qt source,
licensed LGPL-3.0-only.

## Relinking

As required by LGPL-3.0 section 4(d)(0) and LGPL-2.1 section 6(a): you may modify Qt or QtJambi,
rebuild mousemaster from https://github.com/petoncle/mousemaster against your modified version, and
use the result; and you may reverse engineer **mousemaster.exe** as far as needed to debug such
modifications. The README's "Building from source" section has the build steps.
