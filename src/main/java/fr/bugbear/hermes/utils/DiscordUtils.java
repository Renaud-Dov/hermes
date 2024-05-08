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
import net.dv8tion.jda.api.utils.TimeFormat;

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
                // replace <@&ID> and <@ID> mentions by `<@&ID>` and `<@ID>` to avoid pinging
                var messageText = message.getContentRaw()
                                         .replaceAll("<@&([0-9]+)>", "`<@&$1>`")
                                         .replaceAll("<@([0-9]+)>", "`<@$1>`");
                messageText = messageText + messageText;
                val timestamp = TimeFormat.TIME_LONG.format(message.getTimeCreated());
                val sizeMessage = messageText.length();
                thread.sendMessageFormat("%s (%s): %s",
                                         message.getAuthor().getEffectiveName(),
                                         timestamp,
                                         // limit the message to 1900 characters to avoid Discord API limit
                                         messageText.substring(0, Math.min(sizeMessage, 1900))
                ).queue();
                if (sizeMessage > 1900) {
                    for (int i = 1900; i < sizeMessage; i += 1900) {
                        if (i + 1900 >= sizeMessage) {
                            thread.sendMessage(messageText.substring(i)).queue();
                        } else {
                            thread.sendMessage(messageText.substring(i, i + 1900)).queue();
                        }
                    }
                }

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

    public static String maxString(String text, int maxLength, boolean addEllipsis) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - (addEllipsis ? 3 : 0)) + (addEllipsis ? "..." : "");
    }

    public static String maxString(String text, int maxLength) {
        return maxString(text, maxLength, true);
    }

}
