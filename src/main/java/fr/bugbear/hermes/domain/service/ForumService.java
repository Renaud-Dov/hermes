/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.domain.service;

import fr.bugbear.hermes.Logged;
import fr.bugbear.hermes.data.model.ManagerModel;
import fr.bugbear.hermes.data.model.PracticalTagModel;
import fr.bugbear.hermes.data.repository.ForumRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ForumService implements Logged {
    @Inject ForumRepository forumRepository;

    public Optional<ManagerModel> getManagerConfig(Member member, ForumChannel forumChannel) {
        val forum = forumRepository.findByForumChannel(forumChannel);
        if (forum.isEmpty()) return Optional.empty();
        val forumModel = forum.get();
        val userId = member.getIdLong();
        val userRoles = member.getRoles();

        return forumModel.managers.stream()
                                  .filter(m -> m.users.contains(userId) ||
                                               m.roles.stream()
                                                      .anyMatch(role -> userRoles.stream().anyMatch(r -> r.getIdLong()
                                                                                                         == role)))
                                  .findFirst();
    }

    public boolean isManager(Member member, ForumChannel forumChannel) {
        return getManagerConfig(member, forumChannel).isPresent();
    }

    public boolean isNotManager(Member member, ForumChannel forumChannel) {
        return getManagerConfig(member, forumChannel).isEmpty();
    }

    public Optional<ForumTag> getTraceTag(ForumChannel forumChannel) {
        val forum = forumRepository.findByForumChannel(forumChannel);
        if (forum.isEmpty()) return Optional.empty();
        val forumModel = forum.get();
        return forumChannel.getAvailableTagsByName(forumModel.traceTag, true).stream().findFirst();
    }

    public Set<PracticalTagModel> getCurrentPracticalTags(ForumChannel forumChannel) {
        val forum = forumRepository.findByForumChannel(forumChannel);
        if (forum.isEmpty()) return Set.of();
        val forumModel = forum.get();
        val now = ZonedDateTime.now();
        return forumModel.practicalTags.stream()
                                       .filter(t -> t.fromDateTime.isBefore(now) && t.endDateTime.isAfter(now))
                                       .collect(Collectors.toSet());
    }

    public void askForTitle(SlashCommandInteractionEvent event) {
        // check if the event was triggered in a thread channel (open and not locked)
        if (event.getChannel().getType() != ChannelType.GUILD_PUBLIC_THREAD) {
            event.reply("This command can only be used in a thread channel").setEphemeral(true).queue();
            return;
        }
        val threadChannel = event.getChannel().asThreadChannel();
        if (threadChannel.isLocked() || threadChannel.isArchived()) {
            event.reply("This command can only be used in an open thread channel").setEphemeral(true).queue();
            return;
        }

        val forumChannel = threadChannel.getParentChannel().asForumChannel();
        if (isNotManager(event.getMember(), forumChannel)) {
            event.reply("You are not allowed to ask for a change of ticket title").setEphemeral(true).queue();
            return;
        }

        val title = threadChannel.getName();

        val message = "The title of your ticket does not follow the rules. **Please give an explicit title to your "
                      + "ticket otherwise no help can be provided to you.**";

        threadChannel.sendMessageEmbeds(new EmbedBuilder()
                                                .setTitle("Invalid title!")
                                                .setDescription(message)
                                                .addField("Current title", title, false)
                                                .build())
                     .queue();
        logger().info("%s asked for a title change in %d".formatted(event.getUser().getAsTag(),
                                                                    threadChannel.getIdLong()));

    }

}
