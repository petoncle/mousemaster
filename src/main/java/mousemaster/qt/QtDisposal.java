package mousemaster.qt;

import io.qt.core.QObject;

import java.util.ArrayList;
import java.util.List;

public final class QtDisposal {

    private static final List<QObject> disposing = new ArrayList<>();

    private QtDisposal() {
    }

    /** Garbage collecting a wrapper before Qt has deleted its object makes QtJambi's cleanup
     *  thread delete that object a second time, concurrently with Qt, and crash. */
    public static void disposeLater(QObject object) {
        disposing.removeIf(QObject::isDisposed);
        disposing.add(object);
        object.disposeLater();
    }

}
