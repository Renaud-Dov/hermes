/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.repository;

import fr.bugbear.hermes.data.model.TicketModel;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;

import java.util.Optional;

@ApplicationScoped
public class TicketRepository implements PanacheRepositoryBase<TicketModel, Long> {

    public Optional<TicketModel> findByThread(ThreadChannel thread) {
        return find("threadId", thread.getIdLong()).firstResultOptional();
    }
}
