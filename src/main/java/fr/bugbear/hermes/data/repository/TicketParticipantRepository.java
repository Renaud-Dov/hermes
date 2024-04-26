/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.repository;

import fr.bugbear.hermes.data.model.TicketParticipantModel;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class TicketParticipantRepository implements PanacheRepositoryBase<TicketParticipantModel, UUID> {
}
