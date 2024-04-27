/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.utils;

import lombok.val;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class DiscordUtils {

    public static boolean isChannelForumThread(MessageChannelUnion channel) {
        if (channel.getType() != ChannelType.GUILD_PUBLIC_THREAD)
            return false;

        ThreadChannel threadChannel = channel.asThreadChannel();
        return threadChannel.getParentChannel().getType() == ChannelType.FORUM;
    }

    public static Optional<OptionMapping> getOption(SlashCommandInteractionEvent event, String optionName) {
        return Optional.ofNullable(event.getOption(optionName));
    }

    public static Optional<String> getOptionAsString(SlashCommandInteractionEvent event, String optionName) {
        return getOption(event, optionName).map(OptionMapping::getAsString);
    }

    public static <T extends Enum<T>> Optional<T> getOptionAsEnum(SlashCommandInteractionEvent event,
                                                                  String optionName,
                                                                  Class<T> enumClass) {
        return getOption(event, optionName).map(option -> Enum.valueOf(enumClass, option.getAsString().toUpperCase()));
    }

    public static List<Message> getAllMessages(MessageChannel channel) {
        val messages = channel.getIterableHistory().cache(false).complete();
        return messages.stream().toList();
    }

    public static void copyMessagesToLogChannelThenDelete(List<Message> messages,
                                                          TextChannel logChannel,
                                                          String threadName,
                                                          GuildMessageChannel channel) {
        // create a new thread to avoid blocking the event loop
        logChannel.createThreadChannel(threadName).queue(thread -> {
            messages.reversed().forEach(message -> {
                // TODO: check size does not exceed the limit of 2000 characters
                thread.sendMessageFormat("%s: %s", message.getAuthor().getName(), message.getContentRaw()).queue();
            });
            channel.delete().reason("Ticket closed").queue();
        });
    }

    public static Optional<UUID> extractUUID(String prefix, String text) {
        val pattern = Pattern.compile("%s-(.+)".formatted(prefix));
        val matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Optional.of(UUID.fromString(matcher.group(1)));
        }
        return Optional.empty();
    }

    public static Optional<Long> extractID(String prefix, String text) {
        val pattern = Pattern.compile("%s-(.+)".formatted(prefix));
        val matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Optional.of(Long.parseLong(matcher.group(1)));
        }
        return Optional.empty();
    }


}
