/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.repository;

import fr.bugbear.hermes.data.model.TraceConfigModel;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.val;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TraceConfigRepository implements PanacheRepositoryBase<TraceConfigModel, UUID> {

    public Optional<TraceConfigModel> findByTag(Long guildId, String tag) {
        // filter out the one that is active now
        val now = ZonedDateTime.now();
        return find("guildId = ?1 and tag = ?2", guildId, tag)
                .stream()
                .filter(c -> c.fromDateTime.isBefore(now) && c.endDateTime.isAfter(now))
                .findFirst();
    }
}
