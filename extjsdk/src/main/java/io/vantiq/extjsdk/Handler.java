
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A class that handles messages.
 *
 * @param <T>   The type of the message that will be received.
 */
public abstract class Handler<T> {
    /**
     * Used to allow passing of arbitrary objects into an anonymous {@link Handler}. Suggested use: add an object to
     * {@code variable}, then in handleMessage do Type varName = (Type) variable['key']
     */
    protected Map<String, Object> variable;

    /**
     * Performs actions upon {@code message}.
     *
     * @param message   A message to be handled
     */
    public abstract void handleMessage(T message);

    /**
     * Creates a {@link Handler} with an empty {@link Map} for {@link #variable}
     */
    public Handler() {
        this.variable = new LinkedHashMap<>();
    }

    /**
     * Creates a {@link Handler} with {@link #variable} equal to {@code config}
     *
     * @param variable    The Map that will be the initial value of {@link #variable}
     */
    public Handler(Map variable) {
        this.variable = variable;
    }

    /**
     * Adds {@code val} to {@link #variable} with key {@code key}
     *
     * @param key   The key at which to place {@code val}
     * @param val   The value to be added to {@link #variable}
     */
    public void addVariable(String key, Object val) {
        variable.put(key, val);
    }
    /**
     * Removes {@code val} from {@link #variable} if it is at key {@code key}. See {@link Map#remove(Object, Object)} for more
     * detailed information.
     *
     * @param key   The key which {@code val} will be removed from
     * @param val   The value to be removed from {@link #variable}
     * @return      Returns the Object removed
     */
    public Object removeVariable(String key, Object val) {
        return variable.remove(key ,val);
    }
    /**
     * Removes the object at {@code key} in  {@link #variable}
     *
     * @param key   The key to remove the {@code Object} from
     * @return      The Object removed from {@link #variable}
     */
    public Object removeVariable(String key) {
        return variable.remove(key);
    }
    /**
     * Sets the {@link #variable} for the {@link Handler} to the argument {@code variable}
     *
     * @param variable  The new Map to be used by the Handler
     */
    public void setVariable(Map<String, Object> variable) {
        this.variable = variable;
    }

    /**
     * Obtain {@link #variable}
     *
     * @return  {@link #variable}
     */
    public Map getVariable() {
        return variable;
    }
}
