/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.domain.entity;

import fr.bugbear.hermes.data.model.TicketModel;

public enum CloseType {
    RESOLVE,
    DELETE,
    DUPLICATE,
    FORCE_CLOSE;

    public TicketModel.Status toStatus() {
        return switch (this) {
            case DELETE -> TicketModel.Status.DELETED;
            default -> TicketModel.Status.CLOSED;
        };
    }
}
