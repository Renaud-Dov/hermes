/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.domain.service;

import fr.bugbear.hermes.Logged;
import fr.bugbear.hermes.data.model.TicketModel;
import fr.bugbear.hermes.data.model.TicketParticipantModel;
import fr.bugbear.hermes.data.repository.ForumRepository;
import fr.bugbear.hermes.data.repository.TicketParticipantRepository;
import fr.bugbear.hermes.data.repository.TicketRepository;
import fr.bugbear.hermes.domain.entity.CloseType;
import fr.bugbear.hermes.utils.EmbedUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.val;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.internal.entities.GuildImpl;
import net.dv8tion.jda.internal.entities.channel.concrete.ForumChannelImpl;
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl;

import java.awt.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static fr.bugbear.hermes.domain.entity.ButtonEventType.REOPEN_TICKET;
import static fr.bugbear.hermes.utils.DiscordUtils.copyMessagesToLogChannel;
import static fr.bugbear.hermes.utils.DiscordUtils.getAllMessages;
import static fr.bugbear.hermes.utils.DiscordUtils.getOptionAsEnum;
import static fr.bugbear.hermes.utils.DiscordUtils.getOptionAsString;
import static fr.bugbear.hermes.utils.DiscordUtils.isChannelForumThread;
import static java.util.Objects.requireNonNull;

@ApplicationScoped
public class TicketService implements Logged {

    @Inject TicketRepository ticketRepository;
    @Inject TicketParticipantRepository ticketParticipantRepository;
    @Inject ForumRepository forumRepository;
    @Inject ForumService forumService;
    @Inject WebhookService webhookService;

    @Inject EmbedUtils embedUtils;

    private String getTicketName(Long ticketId, String ticketName) {
        // remove "[ID] - " from the name
        ticketName = ticketName.replaceFirst("^\\[\\d+\\] - ", "");
        // [ID] - Name
        // the new string must be less than 100 characters,
        // so we need to truncate the name and add ... at the end if necessary
        var name = "[" + ticketId + "] - " + ticketName;
        if (name.length() > 100) {
            name = name.substring(0, 97) + "...";
        }
        return name;
    }

    private void analyzeTags(ThreadChannel threadChannel, List<ForumTag> appliedTags) {
        val forumChannel = threadChannel.getParentChannel().asForumChannel();
        val traceTag = forumService.getTraceTag(forumChannel);
        if (traceTag.isPresent() && appliedTags.contains(traceTag.get())) {
            var ticketOwner = threadChannel.retrieveThreadMemberById(threadChannel.getOwnerIdLong()).complete();
            // send the message with a mention to the owner
            threadChannel.sendMessageFormat(
                                 "Merci de préciser votre login et le tag de votre trace ci dessous./Please specify "
                                 + "your login and the tag of your trace below. %s", ticketOwner.getAsMention())
                         .queue();
        }

        // check for practical Tags
        val practicalTags = forumService.getCurrentPracticalTags(forumChannel);

        // add all tags to the thread if they are not already applied
        // TODO: add practicals tags to the thread

    }

    @Transactional
    public void createTicket(ThreadChannel threadChannel) {

        val forumModel = forumRepository.findByForumChannel(threadChannel.getParentChannel().asForumChannel());
        if (forumModel.isEmpty()) { // ticket is not linked to a forum
            return;
        }
        val forum = forumModel.get();

        // create a new ticket
        val ticket = new TicketModel()
                .withCreatedBy(threadChannel.getOwnerIdLong())
                .withCreatedAt(ZonedDateTime.now())
                .withForum(forum)
                .withGuildId(threadChannel.getGuild().getIdLong())
                .withThreadId(threadChannel.getIdLong())
                .withReopenedTimes(0)
                .withStatus(TicketModel.Status.OPEN)
                .withTags(threadChannel.getAppliedTags()
                                       .stream()
                                       .map(BaseForumTag::getName)
                                       .collect(Collectors.toSet()));

        ticketRepository.persist(ticket);
        val ticketName = getTicketName(ticket.id, threadChannel.getName());
        ticket.name = ticketName;
        ticketRepository.persist(ticket); // update the ticket with the name (will trigger the update event, but it's
        // ok)
        val ticketOwner = threadChannel.retrieveThreadMemberById(threadChannel.getOwnerIdLong()).complete().getMember();
        threadChannel.getManager()
                     .setName(ticketName)
                     .setArchived(false)
                     .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                     .and(threadChannel.join())
                     .queue();

        // analyze tags
        analyzeTags(threadChannel, threadChannel.getAppliedTags());

        val hookMessage = webhookService.sendEmbed(forum, getTicketWebhookEmbed(ticket, ticketOwner))
                                        // add link button
                                        .addActionRow(Button.link(threadChannel.getJumpUrl(), "Go to"))
                                        .complete();
        ticket.webhookMessageUrl = hookMessage.getJumpUrl();

    }

    private MessageEmbed getTicketWebhookEmbed(TicketModel ticket, Member author) {
        val status = switch (ticket.status) {
            case OPEN -> ":green_circle: Open";
            case IN_PROGRESS -> ":yellow_circle: In progress";
            case CLOSED, DELETED -> ":red_circle: Closed";
        };
        final Color color = switch (ticket.status) {
            case OPEN -> Color.GREEN;
            case IN_PROGRESS -> Color.ORANGE;
            case CLOSED, DELETED -> Color.RED;
        };
        String tags = ticket.tags.isEmpty() ? "No tags applied" :
                      String.join("\n", ticket.tags.stream().map("**%s**"::formatted).toList());
        return new EmbedBuilder()
                .setTitle(ticket.name)
                .setColor(color)
                .setAuthor(author.getEffectiveName(), null, author.getEffectiveAvatarUrl())
                .addField("Status", status, true)
                .addField("Tags", tags, true)
                .setTimestamp(ticket.createdAt.toInstant())
                .setFooter("Thread ID: " + ticket.threadId)
                .build();
    }

    @Transactional
    public void renameTicket(SlashCommandInteractionEvent event) {
        val nameOption = requireNonNull(event.getOption("name")).getAsString();
        // check that the event was triggered in a thread channel
        if (!isChannelForumThread(event.getChannel())) {
            logger().error("Couldn't rename ticket, event not triggered in a thread channel");
            return;
        }
        val threadChannel = event.getChannel().asThreadChannel();
        // check that thread is not archived or locked
        if (threadChannel.isArchived() || threadChannel.isLocked()) {
            logger().error("Couldn't rename ticket, thread is archived or locked");
            return;
        }
        event.deferReply().setEphemeral(true).queue();

        // rename the ticket
        val ticket = ticketRepository.findByThread(threadChannel).orElseThrow();
        if (forumService.isNotManager(event.getMember(), threadChannel.getParentChannel().asForumChannel())) {
            logger().error("User is not a manager of the forum");
            event.reply("You are not allowed to rename the ticket").setEphemeral(true).queue();
            return;
        }

        val newTicketName = getTicketName(ticket.id, nameOption);
        ticket.name = newTicketName;
        threadChannel.getManager().setName(newTicketName).queue();

        event.reply("Ticket renamed from `%s` to `%s`".formatted(threadChannel.getName(), newTicketName))
             .setEphemeral(true)
             .queue();

        webhookService.editEmbed(ticket, getTicketWebhookEmbed(ticket, threadChannel.getOwner())).queue();
    }

    @Transactional
    public void closeTicket(SlashCommandInteractionEvent event) {
        val typeOption = getOptionAsEnum(event, "type", CloseType.class).orElse(CloseType.RESOLVE);
        val reasonOption = getOptionAsString(event, "reason").orElse("");

        // check that the event was triggered in a thread channel
        if (!isChannelForumThread(event.getChannel())) {
            val member = requireNonNull(event.getMember());

            event.reply("This command can only be used in a thread channel" + member.getAsMention())
                 .setEphemeral(true)
                 .queue();
            return;
        }
        event.deferReply().setEphemeral(true).queue();

        val member = requireNonNull(event.getMember());

        val threadChannel = event.getChannel().asThreadChannel();
        val forumChannel = threadChannel.getParentChannel().asForumChannel();
        val ticket = ticketRepository.findByThread(threadChannel).orElseThrow();
        val webhookChannel = requireNonNull(threadChannel.getJDA()
                                                         .getTextChannelById(ticket.forum.webhookChannelId));

        if (forumService.isNotManager(member, forumChannel)) {
            event.getHook().editOriginal("You are not allowed to close the ticket").queue();
            return;
        }
        event.getHook().editOriginal("Ticket closed").queue();
        // TODO: log to webhook channel
        if (typeOption == CloseType.DELETE) {
            // copy all the messages to the webhook channel and delete the ticket
            copyMessagesToLogChannel(getAllMessages(threadChannel), webhookChannel, threadChannel.getName());
            threadChannel.delete().reason("Ticket closed").queue();
        } else {
            // archive the ticket
            threadChannel.sendMessageEmbeds(embedUtils.getCloseTicketMessage(typeOption,
                                                                             requireNonNull(event.getMember()),
                                                                             reasonOption)
            ).queue();
            threadChannel.getManager().setArchived(true).setLocked(true).reason("Ticket closed").queue();

        }

        // close the ticket
        ticket.status = typeOption.toStatus();
        ticket.closedAt = ZonedDateTime.now();
        ticket.updatedAt = ZonedDateTime.now();

        // send message to user that ticket is closed
        val ticketOwner = requireNonNull(threadChannel.getOwner());
        ticketOwner.getUser().openPrivateChannel().queue(channel -> {
            val actionRow = new ArrayList<ItemComponent>() {{
                add(Button.link(threadChannel.getJumpUrl(), "Go to"));
                if (typeOption == CloseType.RESOLVE)
                    add(Button.primary("%s-%d".formatted(REOPEN_TICKET, ticket.id), "Reopen"));
            }};
            channel.sendMessageEmbeds(embedUtils.getPrivateCloseTicketMessage(ticket, threadChannel,
                                                                              typeOption,
                                                                              member,
                                                                              reasonOption))
                   .addActionRow(actionRow)
                   .queue();
        });
        webhookService.editEmbed(ticket, getTicketWebhookEmbed(ticket, ticketOwner)).queue();
    }

    @Transactional
    public void registerParticipation(ThreadChannel threadChannel, Member member) {
        val ticketModel = ticketRepository.findByThread(threadChannel);
        if (ticketModel.isEmpty())  // the thread is not related to a ticket
            return;
        val ticket = ticketModel.get();
        if (ticket.status != TicketModel.Status.OPEN && ticket.status != TicketModel.Status.IN_PROGRESS) {
            logger().debug("Couldn't register participation, ticket is not open or in progress");
            return;
        }

        if (forumService.isNotManager(member, threadChannel.getParentChannel().asForumChannel())) {
            return;
        }
        val now = ZonedDateTime.now();
        if (ticket.takenAt == null) {
            ticket.takenAt = now;
            ticket.status = TicketModel.Status.IN_PROGRESS;
        }
        if (ticket.participants.stream().noneMatch(p -> p.userId == member.getIdLong())) {
            val participant = new TicketParticipantModel()
                    .withId(UUID.randomUUID())
                    .withUserId(member.getIdLong())
                    .withTakenAt(now)
                    .withTicket(ticket);

            ticketParticipantRepository.persist(participant);

        }

        ticket.updatedAt = now;
    }

    public void reopenTicket(ButtonInteractionEvent event) {
        // "reopen_ticket"-<ticketId>
        event.deferReply(true).queue();
        val ticketId = Long.parseLong(event.getComponentId().split("-")[1]);
        logger().info("Reopening ticket {}", ticketId);
        val ticket = ticketRepository.findByIdOptional(ticketId).orElseThrow();
        val guild = requireNonNull(event.getJDA().getGuildById(ticket.guildId));
        // retrieve the thread channel

        val threadChannel = new ThreadChannelImpl(ticket.threadId, (GuildImpl) guild, ChannelType.GUILD_PUBLIC_THREAD)
                .setParentChannel(new ForumChannelImpl(ticket.forum.channelId, (GuildImpl) guild));
        val author = guild.retrieveMember(event.getUser()).complete();

        threadChannel.getManager().setLocked(false).setArchived(false).queue();
        ticket.status = TicketModel.Status.IN_PROGRESS;
        ticket.reopenedTimes++;
        ticket.updatedAt = ZonedDateTime.now();

        val user = requireNonNull(event.getUser());
        event.getHook().editOriginal("Ticket reopened in %s".formatted(threadChannel.getAsMention())).queue();
        threadChannel.sendMessageFormat("Ticket reopened by %s", user.getAsMention()).queue();

        // remove the button from the message
        event.getMessage().editMessageComponents().queue();

        webhookService.editEmbed(ticket, getTicketWebhookEmbed(ticket, author)).queue();
    }

    @Transactional
    public void deleteTicket(ThreadChannel threadChannel) {
        val ticketModel = ticketRepository.findByThread(threadChannel);
        if (ticketModel.isEmpty())
            return;
        val ticket = ticketModel.get();
        // if the ticket is already closed, we don't need to execute the event again
        if (ticket.status == TicketModel.Status.CLOSED)
            return;
        val now = ZonedDateTime.now();
        ticket.status = TicketModel.Status.DELETED;
        ticket.closedAt = now;
        ticket.updatedAt = now;
        if (ticket.takenAt != null)
            ticket.takenAt = now;
    }

    public void linkTicket(SlashCommandInteractionEvent event) {
        val ticketId = requireNonNull(event.getOption("id")).getAsLong();
        val ticketModel = ticketRepository.findByIdOptional(ticketId);
        if (ticketModel.isEmpty()) {
            event.reply("Ticket not found").setEphemeral(true).queue();
            return;
        }
        val ticket = ticketModel.get();
        event.reply("Ticket %d : https://discord.com/channels/%d/%d".formatted(ticket.id,
                                                                               ticket.guildId,
                                                                               ticket.threadId))
             .setEphemeral(false)
             .queue();
    }

    public void onTicketRename(ChannelUpdateNameEvent event) {
        val threadChannel = event.getChannel().asThreadChannel();
        val ticketModel = ticketRepository.findByThread(threadChannel);
        if (ticketModel.isEmpty())
            return;
        val ticket = ticketModel.get();
        val newName = event.getNewValue();
        if (!Objects.equals("[%d] - %s".formatted(ticket.id, ticket.name), newName)) {
            val newTicketName = getTicketName(ticket.id, threadChannel.getName());
            ticket.name = newTicketName;
            threadChannel.getManager().setName(newTicketName).queue();
            webhookService.editEmbed(ticket, getTicketWebhookEmbed(ticket, threadChannel.getOwner())).queue();
        }
    }

    @Transactional
    public void onTagsChange(ChannelUpdateAppliedTagsEvent event) {
        val threadChannel = event.getChannel().asThreadChannel();
        val forumChannel = threadChannel.getParentChannel().asForumChannel();
        val ticketModel = ticketRepository.findByThread(threadChannel);
        if (ticketModel.isEmpty())
            return;
        val ticket = ticketModel.get();
        val tags = threadChannel.getAppliedTags();
        analyzeTags(threadChannel, event.getAddedTags());

        ticket.tags = tags.stream().map(ForumTag::getName).collect(Collectors.toSet());

        // TODO: check for practical tags
        val threadOwner = event.getGuild().retrieveMemberById(threadChannel.getOwnerIdLong()).complete();
        webhookService.editEmbed(ticket, getTicketWebhookEmbed(ticket, threadOwner)).queue();
    }

    public void onTicketArchivedOrLocked(ThreadChannel threadChannel, User user) {
        // check if the event author is the bot itself
        if (user.getIdLong() == threadChannel.getJDA().getSelfUser().getIdLong())
            return;

        if (ticketRepository.findByThread(threadChannel).isEmpty())
            return;

        val member = threadChannel.getGuild().retrieveMember(user).complete();
        if (forumService.isManager(member, threadChannel.getParentChannel().asForumChannel()))
            return;

        if (threadChannel.isArchived() || threadChannel.isLocked()) {
            threadChannel.getManager().setLocked(false).setArchived(false).queue();
            threadChannel.sendMessage(
                                 "Please don't archive or lock the ticket thread manually. It will be done "
                                 + "automatically when the ticket is closed.")
                         .queue();
        }

    }
}
