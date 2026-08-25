package mousemaster;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
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
                newPropertyValue, propertyPath.screenFilter());
    }

    @SuppressWarnings("unchecked")
    private static Object mutateModeProperty(Object obj, List<String> fieldNames,
                                             Object newPropertyValue,
                                             ScreenFilter targetScreenFilter) {
        if (fieldNames.isEmpty()) {
            if (newPropertyValue instanceof Function<?, ?> function)
                return ((Function<Object, Object>) function).apply(obj);
            return newPropertyValue;
        }
        String fieldName = fieldNames.getFirst();
        List<String> remaining = fieldNames.subList(1, fieldNames.size());
        if (!hasField(obj, fieldName))
            // A decoration is named by its depth (decoration0, decoration1, ...) but held in a list.
            return mutateDecoration(obj, fieldName, remaining, newPropertyValue,
                    targetScreenFilter);
        Object child = getField(obj, fieldName);
        // A null field can be replaced, since the path ends on it and the new value just takes its
        // place, but it cannot be descended into to reach a field deeper in the path.
        if (child == null && !remaining.isEmpty())
            return obj;
        Object mutatedChild;
        if (child instanceof ScreenFilterMap<?> screenFilterMap) {
            Map<ScreenFilter, Object> mutatedMap = new LinkedHashMap<>();
            for (var entry : screenFilterMap.map().entrySet()) {
                if (targetScreenFilter != null &&
                    !entry.getKey().equals(targetScreenFilter)) {
                    mutatedMap.put(entry.getKey(), entry.getValue());
                }
                else {
                    mutatedMap.put(entry.getKey(),
                            mutateModeProperty(entry.getValue(), remaining,
                                    newPropertyValue, null));
                }
            }
            mutatedChild = new ScreenFilterMap<>(mutatedMap);
        }
        else {
            mutatedChild = mutateModeProperty(child, remaining,
                    newPropertyValue, targetScreenFilter);
        }
        return createWithField(obj, fieldName, mutatedChild);
    }

    private static Object mutateDecoration(Object obj, String fieldName,
                                           List<String> remaining, Object newPropertyValue,
                                           ScreenFilter targetScreenFilter) {
        if (!fieldName.startsWith("decoration")
            || !(getField(obj, "decorations") instanceof List<?> decorations))
            return obj;
        int index = Integer.parseInt(fieldName.substring("decoration".length()));
        List<Object> mutatedDecorations = new ArrayList<>(decorations);
        mutatedDecorations.set(index, mutateModeProperty(decorations.get(index), remaining,
                newPropertyValue, targetScreenFilter));
        return createWithField(obj, "decorations", List.copyOf(mutatedDecorations));
    }

    static Object createWithField(Object record, String fieldName,
                                  Object newValue) {
        try {
            RecordComponent[] components = getComponents(record.getClass());
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                if (components[i].getName().equals(fieldName)) {
                    // The same component name holds a different type in another variant of a sealed
                    // type (GridHintMesh.area and UiAccessibilityHintMesh.area): that path is for the other variant.
                    // Primitive components are skipped: the value is boxed, so isInstance is false.
                    Class<?> componentType = components[i].getType();
                    if (newValue != null && !componentType.isPrimitive() &&
                        !componentType.isInstance(newValue))
                        return record;
                    args[i] = newValue;
                }
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

    private static boolean hasField(Object obj, String fieldName) {
        for (RecordComponent component : getComponents(obj.getClass()))
            if (component.getName().equals(fieldName))
                return true;
        return false;
    }

    /** Returns the value of the named field, or {@code null} if it is unset or not a field of this
     *  record type (a sealed type that does not declare it): ask {@link #hasField} to tell those
     *  apart. */
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

    private static final RecordComponent[] NO_COMPONENTS = new RecordComponent[0];

    /** A non-record has no component to descend into: a path reaching one does not apply. */
    private static RecordComponent[] getComponents(Class<?> clazz) {
        return componentCache.computeIfAbsent(clazz, clazz1 -> {
            RecordComponent[] components = clazz1.getRecordComponents();
            return components == null ? NO_COMPONENTS : components;
        });
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
