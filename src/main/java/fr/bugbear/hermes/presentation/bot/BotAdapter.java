/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.presentation.bot;

import fr.bugbear.hermes.Logged;
import fr.bugbear.hermes.domain.service.DiscordService;
import io.quarkus.arc.Arc;
import jakarta.annotation.Nonnull;
import lombok.val;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.GenericChannelEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class BotAdapter extends ListenerAdapter implements Logged {
    private final DiscordService discordService;

    public BotAdapter(DiscordService discordService) {
        this.discordService = discordService;
    }

    private void actionOnForumThread(GenericChannelEvent event, Consumer<ThreadChannel> action) {
        if (event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            val threadChannel = event.getChannel().asThreadChannel();
            // check that the thread parent is a forum
            if (threadChannel.getParentChannel().getType() == ChannelType.FORUM) {
                action.accept(threadChannel);
            }
        }
    }

    private <T extends GenericChannelEvent> void actionOnForumThreadWithEvent(T event, Consumer<T> action) {
        if (event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            val threadChannel = event.getChannel().asThreadChannel();
            // check that the thread parent is a forum
            if (threadChannel.getParentChannel().getType() == ChannelType.FORUM) {
                action.accept(event);
            }
        }
    }

    private void logEvent(Event event) {
        logger().info("Event type triggered : {}", event.getClass().getSimpleName());
    }

    @Override public void onReady(@Nonnull ReadyEvent event) {
        Arc.container().requestContext().activate();
        logEvent(event);
        discordService.onReady(event);
        Arc.container().requestContext().deactivate();
    }

    @Override public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Arc.container().requestContext().activate();
        if (event.getAuthor().isBot())
            return;
        if (event.isFromThread()) {
            val threadChannel = event.getChannel().asThreadChannel();
            // check that the thread parent is a forum
            if (threadChannel.getParentChannel().getType() == ChannelType.FORUM) {
                logEvent(event);
                discordService.onThreadMessage(threadChannel, event.getMember());
            }
        }
        Arc.container().requestContext().deactivate();
    }

    @Override public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        Arc.container().requestContext().activate();
        logEvent(event);
        actionOnForumThread(event, discordService::onThreadCreate);
        Arc.container().requestContext().deactivate();
    }

    @Override public void onChannelUpdateArchived(@NotNull ChannelUpdateArchivedEvent event) {
        // NOTE: locking thread will automatically archive it
        Arc.container().requestContext().activate();
        logEvent(event);
        actionOnForumThreadWithEvent(event, discordService::onTicketArchivedOrLocked);
        Arc.container().requestContext().deactivate();
    }

    @Override public void onChannelUpdateAppliedTags(@NotNull ChannelUpdateAppliedTagsEvent event) {
        Arc.container().requestContext().activate();
        actionOnForumThreadWithEvent(event, discordService::onTagsChange);
        Arc.container().requestContext().deactivate();
    }

    @Override public void onChannelUpdateName(@NotNull ChannelUpdateNameEvent event) {
        Arc.container().requestContext().activate();
        logEvent(event);
        actionOnForumThreadWithEvent(event, discordService::onTicketRenamed);
        Arc.container().requestContext().deactivate();
    }

    @Override public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        Arc.container().requestContext().activate();
        logEvent(event);
        actionOnForumThread(event, discordService::onThreadDelete);
        Arc.container().requestContext().deactivate();

    }

    @Override public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        Arc.container().requestContext().activate();
        logEvent(event);
        discordService.onSlashCommand(event);
        Arc.container().requestContext().deactivate();
    }

    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        Arc.container().requestContext().activate();
        discordService.onModalInteraction(event);
        Arc.container().requestContext().deactivate();
    }

    @Override public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        Arc.container().requestContext().activate();
        logEvent(event);
        discordService.onButtonInteraction(event);
        Arc.container().requestContext().deactivate();
    }
}
