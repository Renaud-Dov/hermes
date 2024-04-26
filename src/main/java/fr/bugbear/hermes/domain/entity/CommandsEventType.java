/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.domain.entity;

public interface CommandsEventType {

    String CLOSE = "close";

    String CLOSE_TRACE = "close_trace";

    String RENAME = "rename";

    String LINK = "link";

    String TRACE = "trace";

    String GOOGLE = "google";

    String ASK_TITLE = "ask_title";
}
