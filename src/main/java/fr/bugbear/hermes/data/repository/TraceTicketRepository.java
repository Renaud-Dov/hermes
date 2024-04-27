/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.repository;

import fr.bugbear.hermes.data.model.TraceTicketModel;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TraceTicketRepository implements PanacheRepositoryBase<TraceTicketModel, UUID> {

    public Optional<TraceTicketModel> findByChannel(TextChannel channel) {
        return find("channelId", channel.getIdLong()).firstResultOptional();
    }
}
