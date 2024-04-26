/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.repository;

import fr.bugbear.hermes.data.model.ForumModel;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ForumRepository implements PanacheRepositoryBase<ForumModel, UUID> {

    public Optional<ForumModel> findByForumChannel(ForumChannel forumChannel) {
        return find("channelId", forumChannel.getId()).firstResultOptional();
    }
}
