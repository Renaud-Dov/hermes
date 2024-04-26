/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Logged {

    default Logger logger() {
        return LoggerFactory.getLogger(getClass());
    }
}
