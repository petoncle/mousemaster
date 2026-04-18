package mousemaster;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ModePropertyMutator {

    private static final Map<Class<?>, RecordComponent[]> componentCache =
            new ConcurrentHashMap<>();

    public static Mode mutateModeProperty(Mode mode, ModePropertyPath propertyPath,
                                           Object newPropertyValue) {
        return (Mode) mutateModeProperty(mode, propertyPath.fieldNames(),
                newPropertyValue, propertyPath.viewportFilter());
    }

    @SuppressWarnings("unchecked")
    private static Object mutateModeProperty(Object obj, List<String> fieldNames,
                                             Object newPropertyValue,
                                             ViewportFilter targetViewportFilter) {
        if (fieldNames.isEmpty()) {
            if (newPropertyValue instanceof Function<?, ?> function)
                return ((Function<Object, Object>) function).apply(obj);
            return newPropertyValue;
        }
        String fieldName = fieldNames.getFirst();
        Object child = getField(obj, fieldName);
        if (child == null)
            return obj; // Field not found on this sealed type, skip mutation.
        List<String> remaining = fieldNames.subList(1, fieldNames.size());
        Object mutatedChild;
        if (child instanceof ViewportFilterMap<?> viewportFilterMap) {
            Map<ViewportFilter, Object> mutatedMap = new LinkedHashMap<>();
            for (var entry : viewportFilterMap.map().entrySet()) {
                if (targetViewportFilter != null &&
                    !entry.getKey().equals(targetViewportFilter)) {
                    mutatedMap.put(entry.getKey(), entry.getValue());
                }
                else {
                    mutatedMap.put(entry.getKey(),
                            mutateModeProperty(entry.getValue(), remaining,
                                    newPropertyValue, null));
                }
            }
            mutatedChild = new ViewportFilterMap<>(mutatedMap);
        }
        else {
            mutatedChild = mutateModeProperty(child, remaining,
                    newPropertyValue, targetViewportFilter);
        }
        return createWithField(obj, fieldName, mutatedChild);
    }

    static Object createWithField(Object record, String fieldName,
                                  Object newValue) {
        try {
            RecordComponent[] components = getComponents(record.getClass());
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                if (components[i].getName().equals(fieldName))
                    args[i] = newValue;
                else
                    args[i] = components[i].getAccessor().invoke(record);
            }
            return getCanonicalConstructor(record.getClass(), components)
                    .newInstance(args);
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Failed to create record with field " + fieldName +
                    " on " + record.getClass().getSimpleName(), e);
        }
    }

    /**
     * Returns the value of the named field, or {@code null} if the field does
     * not exist on this record type (a sealed type that does not declare this field).
     */
    private static Object getField(Object obj, String fieldName) {
        try {
            RecordComponent[] components = getComponents(obj.getClass());
            for (RecordComponent component : components) {
                if (component.getName().equals(fieldName))
                    return component.getAccessor().invoke(obj);
            }
            return null;
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Failed to get field " + fieldName + " on " +
                    obj.getClass().getSimpleName(), e);
        }
    }

    private static RecordComponent[] getComponents(Class<?> clazz) {
        return componentCache.computeIfAbsent(clazz, Class::getRecordComponents);
    }

    private static Constructor<?> getCanonicalConstructor(
            Class<?> clazz, RecordComponent[] components)
            throws NoSuchMethodException {
        Class<?>[] paramTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++)
            paramTypes[i] = components[i].getType();
        return clazz.getDeclaredConstructor(paramTypes);
    }

}
