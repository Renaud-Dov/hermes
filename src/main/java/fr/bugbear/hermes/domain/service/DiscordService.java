/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.domain.service;

import fr.bugbear.hermes.Logged;
import fr.bugbear.hermes.domain.entity.CloseType;
import fr.bugbear.hermes.presentation.bot.BotAdapterStarter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.val;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static fr.bugbear.hermes.domain.entity.ButtonEventType.REOPEN_TICKET;
import static fr.bugbear.hermes.domain.entity.CommandsEventType.ASK_TITLE;
import static fr.bugbear.hermes.domain.entity.CommandsEventType.CLOSE;
import static fr.bugbear.hermes.domain.entity.CommandsEventType.CLOSE_TRACE;
import static fr.bugbear.hermes.domain.entity.CommandsEventType.GOOGLE;
import static fr.bugbear.hermes.domain.entity.CommandsEventType.LINK;
import static fr.bugbear.hermes.domain.entity.CommandsEventType.RENAME;
import static fr.bugbear.hermes.domain.entity.CommandsEventType.TRACE;
import static fr.bugbear.hermes.domain.entity.CommandsEventType.TRACE_VOCAL;
import static fr.bugbear.hermes.domain.entity.ModalEventType.NEW_TRACE_TICKET;

@ApplicationScoped @AllArgsConstructor(onConstructor_ = {@Inject})
public class DiscordService implements Logged {

    @Inject TicketService ticketService;
    @Inject TraceTicketService traceTicketService;
    @Inject ForumService forumService;

    public void onReady(ReadyEvent event) {
        logger().info("Bot is ready : {}", event.getJDA().getSelfUser());
        val closeTicket = Commands.slash(CLOSE, "Close ticket")
                                  .setGuildOnly(true)
                                  .addOptions(
                                          new OptionData(OptionType.STRING, "type", "The type of close", false)
                                                  .addChoices(Arrays.stream(CloseType.values())
                                                                    .map(name -> new Command.Choice(name.toString(),
                                                                                                    name.name()))
                                                                    .toList()),
                                          new OptionData(OptionType.STRING, "reason", "The reason of close", false)
                                  );

        val closeTraceTicket = Commands.slash(CLOSE_TRACE, "Close trace ticket")
                                       .setGuildOnly(true);

        val renameTicket = Commands.slash(RENAME, "Rename ticket")
                                   .setGuildOnly(true)
                                   .addOption(OptionType.STRING, "name", "The new name of the ticket", true);

        val linkTicket = Commands.slash(LINK, "Link ticket")
                                 .setGuildOnly(true)
                                 .addOption(OptionType.INTEGER, "id", "The ID of the ticket to link", true);

        val traceTicket = Commands.slash(TRACE, "Trace ticket")
                                  .setGuildOnly(true)
                                  .addOption(OptionType.STRING, "tag", "Tag category", true, true);

        val associateVocalToTrace = Commands.slash(TRACE_VOCAL, "Associate a vocal channel to a trace ticket")
                                            .setGuildOnly(true);

        val googleCommand = Commands.slash(GOOGLE, "What do you know about `Let me google that for you`?")
                                    .setGuildOnly(true)
                                    .addOption(OptionType.STRING, "query", "The query to search", true)
                                    .addOption(OptionType.STRING, "message", "The message to send", false);

        val askTitle = Commands.slash(ASK_TITLE, "Ask for a title")
                               .setGuildOnly(true);

        // global commands
        BotAdapterStarter.client.updateCommands()
                                .addCommands(closeTicket,
                                             closeTraceTicket,
                                             renameTicket,
                                             linkTicket,
                                             traceTicket,
                                             associateVocalToTrace,
                                             googleCommand,
                                             askTitle)
                                .queue();

        logger().info("Global commands registered");

    }

    public void onThreadCreate(ThreadChannel threadChannel) {
        ticketService.createTicket(threadChannel);
    }

    public void onThreadDelete(ThreadChannel threadChannel) {
        ticketService.deleteTicket(threadChannel);
    }

    public void onThreadMessage(ThreadChannel threadChannel, Member member) {
        ticketService.registerParticipation(threadChannel, member);
    }

    public void onSlashCommand(SlashCommandInteractionEvent event) {
        val commandName = event.getName();
        logger().debug("Slash command : {}", commandName);
        try {
            switch (commandName) {
                case CLOSE -> ticketService.closeTicket(event);
                case RENAME -> ticketService.renameTicket(event);
                case TRACE -> traceTicketService.traceTicket(event);
                case TRACE_VOCAL -> traceTicketService.associateVocalChannel(event);
                case CLOSE_TRACE -> traceTicketService.closeTraceTicket(event);
                case ASK_TITLE -> forumService.askForTitle(event);
                case LINK -> ticketService.linkTicket(event);
                default -> {
                    logger().warn("Unknown command : {}", commandName);
                    event.reply("Unknown command, please contact an admin if the issue persists")
                         .setEphemeral(true)
                         .queue();
                }
            }
        } catch (Exception e) {
            UUID errorId = UUID.randomUUID();
            logger().error("Error ID : {}", errorId, e);
            event.getHook()
                 .editOriginal(("An error occurred during the command execution, please contact an admin with the "
                                + "error ID : "
                                + "%s").formatted(errorId))
                 .queue();
        }
    }

    public void onCommandAutoComplete(CommandAutoCompleteInteractionEvent event) {
        val commandName = event.getName();
        logger().info("Command autocomplete : {}", commandName);
        try {
            switch (commandName) {
                case TRACE -> traceTicketService.traceAutoComplete(event);
                default -> {
                    logger().warn("Unknown command : {}", commandName);
                    event.replyChoices(List.of()).queue();
                }
            }
        } catch (Exception e) {
            UUID errorId = UUID.randomUUID();
            logger().error("Error ID : {}", errorId, e);
            event.replyChoices(List.of()).queue();
        }
    }

    public void onModalInteraction(ModalInteractionEvent event) {
        try {
            val modalId = event.getModalId();

            if (modalId.startsWith(NEW_TRACE_TICKET)) {
                traceTicketService.onModalTraceTicket(event);
            } else {
                logger().warn("Unknown modal : {}", modalId);
                event.reply("Unknown modal, please contact an admin if the issue persists").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            UUID errorId = UUID.randomUUID();
            logger().error("Error ID : %s".formatted(errorId), e);
            event.getHook()
                 .editOriginal(("An error occurred during the modal execution, please contact an admin with the error"
                                + " ID : "
                                + "%s").formatted(errorId))
                 .queue();
        }
    }

    public void onButtonInteraction(ButtonInteractionEvent event) {
        try {
            val buttonId = event.getComponentId();
            if (buttonId.startsWith(REOPEN_TICKET)) {
                ticketService.reopenTicket(event);
            } else {
                logger().warn("Unknown button : {}", buttonId);
                event.reply("Unknown button, please contact an admin if the issue persists").queue();
            }
        } catch (Exception e) {
            UUID errorId = UUID.randomUUID();
            logger().error("Error ID : %s".formatted(errorId), e);
            event.getHook()
                 .editOriginal("An error occurred during the button execution, please contact an admin with"
                               + " the error ID : %s".formatted(errorId)).queue();
        }
    }

    public void onTagsChange(ChannelUpdateAppliedTagsEvent event) {
        ticketService.onTagsChange(event);
    }

    public void onTicketRenamed(ChannelUpdateNameEvent event) {
        ticketService.onTicketRename(event);
    }

    public void onTicketArchivedOrLocked(GenericChannelUpdateEvent<?> channelUpdateArchivedEvent) {
        // get the user who archived the ticket
        val guild = channelUpdateArchivedEvent.getGuild();

        // read the last audit log entries to get the user who archived the ticket
        guild.retrieveAuditLogs().type(ActionType.THREAD_UPDATE).limit(10).complete()
             .stream()
             .map(log -> log.getTargetIdLong() == channelUpdateArchivedEvent.getChannel().getIdLong()
                         ? log.getUser() : null)
             .filter(Objects::nonNull)
             .findFirst()
             .ifPresentOrElse(user -> ticketService
                                      .onTicketArchivedOrLocked(channelUpdateArchivedEvent.getChannel().asThreadChannel(), user),
                              () -> logger().warn("No user found for the thread update event : {}",
                                                  channelUpdateArchivedEvent.getEntity().getIdLong()));

    }
}
