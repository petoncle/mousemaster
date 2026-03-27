package mousemaster;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModePropertyMutator {

    private static final Map<Class<?>, RecordComponent[]> componentCache =
            new ConcurrentHashMap<>();

    public static Mode mutateModeProperty(Mode mode, ModePropertyPath propertyPath,
                                           Object newPropertyValue) {
        return (Mode) mutateModeProperty(mode, propertyPath.fieldNames(),
                newPropertyValue);
    }

    private static Object mutateModeProperty(Object obj, List<String> fieldNames,
                                             Object newPropertyValue) {
        if (fieldNames.isEmpty())
            return newPropertyValue;
        String fieldName = fieldNames.getFirst();
        Object child = getField(obj, fieldName);
        List<String> remaining = fieldNames.subList(1, fieldNames.size());
        Object mutatedChild;
        if (child instanceof ViewportFilterMap<?> viewportFilterMap) {
            Map<ViewportFilter, Object> mutatedMap = new LinkedHashMap<>();
            for (var entry : viewportFilterMap.map().entrySet())
                mutatedMap.put(entry.getKey(),
                        mutateModeProperty(entry.getValue(), remaining, newPropertyValue));
            mutatedChild = new ViewportFilterMap<>(mutatedMap);
        }
        else {
            mutatedChild = mutateModeProperty(child, remaining, newPropertyValue);
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

    private static Object getField(Object obj, String fieldName) {
        try {
            RecordComponent[] components = getComponents(obj.getClass());
            for (RecordComponent component : components) {
                if (component.getName().equals(fieldName))
                    return component.getAccessor().invoke(obj);
            }
            throw new IllegalArgumentException(
                    "No field " + fieldName + " on " + obj.getClass().getSimpleName());
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
