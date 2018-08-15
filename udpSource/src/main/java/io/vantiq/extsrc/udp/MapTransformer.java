
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.udp;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A class dedicated to translating keys in a {@link Map} or a nested {@link Map}. It can transform
 * one map to another based on "transformations", two element {@link String} arrays of the form
 * {@code ["<location in input>", "<location in output>"]} where a location for the element "Hello World" in the nested Map
 * <pre>Map map = {
 *     level1: {
 *         level2: {
 *             level3: {
 *                 "Hello World"
 *             }
 *         }
 *     }
 * }</pre>
 * is written as "level1.level2.level3". It can also obtain or add values to a map by targeting
 * a location using the same format. This class is intended for situations in which you may desire to safely access elements
 * from a nested {@link Map} where the location is not known at compile time or who may not exist.
 * It is possible to transform a single location to several others, or several locations to a single one. In the latter case
 * the last valid location in the transforms array will be the value placed in the output location.
 */
public class MapTransformer {
    /**
     * The transformations used by a particular instance of {@link MapTransformer}.
     */
    private String[][] transforms;

    /**
     * Create a new {@link MapTransformer} with the specified transformations. A "transformation" is a 2 element String array, where
     * the first String is the location of the {@link Object} to be obtained from any input {@link Map}, the second is
     * the location in the second {@link Map} where the value will be placed.
     *
     * @param transforms    An array of transformations of the form ["&lt;location in {@code input}&gt;", "&lt;location in {@code out}&gt;"].
     */
    public MapTransformer(String[][] transforms) {
        List<String[]> trueTransforms = new ArrayList<>();
        for (String[] transform : transforms)
        {
            if (isValidTransform(transform)) {
                trueTransforms.add(transform);
            }
        }
        this.transforms = (String[][]) trueTransforms.toArray();
    }

    /**
     * Create a new {@link MapTransformer} with the specified transformations. A "transformation" is a 2 element String array, where
     * the first String is the location of the {@link Object} to be obtained from any input {@link Map}, the second is
     * the location in the second {@link Map} where the value will be placed.
     *
     * @param transforms    An array of transformations of the form ["&lt;location in {@code input}&gt;", "&lt;location in {@code out}&gt;"].
     */
    public MapTransformer(List<List> transforms) {
        List<String[]> trueTransforms = new ArrayList<>();
        for (List transform : transforms)
        {
            if (isValidTransform(transform)) {
                String[] trans = (String[]) transform.toArray(new String[0]);
                trueTransforms.add(trans);
            }
        }
        this.transforms = trueTransforms.toArray(new String[0][0]);
    }

    /**
     * Checks to see if the given transform is valid.
     *
     * @param obj   The object under test
     * @return      true if list is a {@link List} of size 2 with both elements Strings, false otherwise
     */
    private static boolean isValidTransform(Object obj) {
        List list = null;
        if (obj instanceof List) {
            list = (List) obj;
        }
        else if (obj instanceof String[]) {
            return isValidTransform((String[]) obj);
        }
        return list != null && list.size() == 2 && list.get(0) instanceof String && list.get(1) instanceof String;
    }
    /**
     * Checks to see if the given transform is valid.
     *
     * @param arr   An array containing a possible transform
     * @return      true if the array is size 2, false otherwise
     */
    private static boolean isValidTransform(String[] arr) {
        return arr.length == 2;
    }

    /**
     * Obtains all valid transforms from a {@link List} and returns them.
     *
     * @param transforms    The {@link List} containing potential transformations
     * @return              A {@link List List&lt;List&gt;} containing all valid transformations from the given {@link List}
     */
    public static List<List> getValidTransforms(List transforms) {
        List<List> trueTransforms = new ArrayList<>();
        for (Object transform : transforms)
        {
            if (isValidTransform(transform)) {
                trueTransforms.add((List) transform);
            }
        }
        return trueTransforms;
    }

    /**
     * Copy the values specified in the constructor from {@code input} to {@code out}. Any values on the path of a
     * transform will be overwritten in the process, as noted in {@link #createTransformVal}.
     *
     * @param input     {@link Map} from which the values will be taken
     * @param out       {@link Map} to which the values will be copied
     * @param destroy   Whether or not to remove the copied values from {@code input}
     */
    public void transform(Map input, Map out, boolean destroy) {
        transform(input, out, destroy, transforms);
    }

    /**
     * Copy the values specified in the constructor from {@code input} to {@code out}. Any values on the path of a
     * transform will be overwritten in the process, as noted in {@link #createTransformVal}
     *
     * @param input         {@link Map} from which the values will be taken
     * @param out           {@link Map} to which the values will be copied
     * @param destroy       Whether or not to remove the copied values from {@code input}
     * @param transforms    An array of transformations of the form ["&lt;location in {@code input}&gt;", "&lt;location in {@code out}&gt;"]
     */
    public static void transform(Map input, Map out, boolean destroy, String[][] transforms) {
        for (String[] trans : transforms) {
            Object o = getTransformVal(input, trans[0], false);
            if (o != null) {
                createTransformVal(out, trans[1], o);
            }
        }

        // Only delete after all transformations are done, so copies can be created
        if (destroy) {
            for (String[] transform : transforms) {
                getTransformVal(input, transform[0], true);
            }
        }
    }

    /**
     * Gets the object from a map at a specified location.
     * <br>
     * For example, if map = {@code {
     *     "first": {
     *         "second": {
     *             "third": "Hello World"
     *         }
     *     }
     * }
     * } and loc = "first.second.third", then getTransformVal(map, loc) would return the String "Hello World"
     *
     * @param map           The map to retrieve the Object from
     * @param loc           The string representation of the location to remove.
     * @return              The object at the specified loc, or null if that object does not exist
     */
    public static Object getTransformVal(Map map, String loc) {
        Object result;
        Map currentLvl = map;
        String[] levelNames = loc.split("\\.");

        int level;
        for (level = 0; level < levelNames.length - 1; level++) {
            if (!(currentLvl.get(levelNames[level]) instanceof Map)) {
                return null;
            }
            currentLvl = (Map) currentLvl.get(levelNames[level]);
        }
        result = currentLvl.get(levelNames[level]);
        return result;
    }

    /**
     * Gets the object from a map at a specified location.
     * <br>
     * For example, if map = {@code {
     *     "first": {
     *         "second": {
     *             "third": "Hello World"
     *         }
     *     }
     * }
     * } and loc = "first.second.third", then getTransformVal(map, loc, false) would return the String "Hello World"
     *
     * @param map           The map to retrieve the Object from
     * @param loc           The string representation of the location to remove.
     * @param destructive   Whether the method should remove the returned value from the map. Default is false
     * @return              The object at the specified loc, or null if that object does not exist
     */
    public static Object getTransformVal(Map map, String loc, boolean destructive) {
        Object result;
        Map currentLvl = map;
        String[] levelNames = loc.split("\\.");

        int level;
        for (level = 0; level < levelNames.length - 1; level++) {
            if (!(currentLvl.get(levelNames[level]) instanceof Map)) {
                return null;
            }
            currentLvl = (Map) currentLvl.get(levelNames[level]);
        }
        result = currentLvl.get(levelNames[level]);
        if (destructive) {
            currentLvl.put(levelNames[level], null);
            removeEmpty(map, loc);
        }

        return result;
    }

    /**
     * Deletes levels of a {@link Map} on the path {@code path} that contain nothing of value, namely only null values or
     * any {@link Map} whose only values are empty Maps along the path or {@code null} along the path.
     * <p>
     * For example, if ourMap = {@code {
     *     "level1": {
     *         "str": "Hello World!",
     *         "level2": {
     *             "level3":{
     *                 "level4":{
     *                     "level5":null
     *                 }
     *             },
     *             "empty": null
     *         }
     *     }
     * }
     * } then after removeEmpty(ourMap, 'level1.level2.level3.level4.level5') ourMap would be {@code {
     *     "level1":{
     *         "str": "Hello World",
     *         "level2":{
     *             "empty": null
     *         }
     *     }
     * }
     * } Note that level1.level2 was not removed despite only having a null value, since level1.level2.empty was not
     * along the specified path. Additionally, 'level1.level2.level3.level4' would not have changed the original map, as
     * level4 contains level5, which is not in the specified path
     *
     * @param map   The {@link Map} to remove everything from
     * @param path   The {@link String} representation of the path which should be cleaned up
     */
    private static void removeEmpty(Map map, String path) {
        String[] levelNames = path.split("\\.", 2);
        String currentLevel = levelNames[0];
        if (levelNames.length == 2) {
            String remainingLevels = levelNames[1];
            removeEmpty((Map) map.get(currentLevel), remainingLevels);
        }
        if (map.get(currentLevel) == null || ((Map) map.get(currentLevel)).isEmpty()) {
            map.remove(currentLevel);
        }
    }

    /**
     * Places the given object at the specified location in the map.
     * <br>
     * For example, if ourMap is an empty {@link Map} then createTransformVal(ourMap, "level1.level2", "message") will make
     * ourMap = {@code {
     *      "level1": {
     *          "level2":"message"
     *      }
     * }}
     * <br>
     * Be aware that this will overwrite any {@link Object} found in the path, so if we call
     * createTransformVal(ourMap, "level1.level2.level3", "Hello World") where ourMap is the result of the previous
     * example, then ourMap will become
     * {@code {
     *      "level1": {
     *          "level2": {
     *              "level3":"Hello World"
     *          }
     *      }
     * }}
     *
     * @param map   The {@link Map} to which obj should be added
     * @param loc   The {@link String} representation of the location to remove.
     * @param obj   The {@link Object} to be added to map
     */
    public static void createTransformVal(Map map, String loc, Object obj) {
        Map currentLvl = map;
        String[] levelNames = loc.split("\\.");

        int level;
        for (level= 0; level < levelNames.length - 1; level++) {
            if (!(currentLvl.get(levelNames[level]) instanceof Map)) {
                currentLvl.put(levelNames[level], new LinkedHashMap());
            }
            currentLvl = (Map) currentLvl.get(levelNames[level]);
        }

        if (!(currentLvl.get(levelNames[level]) instanceof Map)) {
            currentLvl.put(levelNames[level], new LinkedHashMap());
        }
        currentLvl.put(levelNames[level], obj);
    }
}
