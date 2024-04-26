/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.domain.service;

import fr.bugbear.hermes.Logged;
import fr.bugbear.hermes.data.model.ForumModel;
import fr.bugbear.hermes.data.model.TicketModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.val;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.Objects;

import static fr.bugbear.hermes.presentation.bot.BotAdapterStarter.client;

@ApplicationScoped @AllArgsConstructor(onConstructor_ = {@Inject})
public class WebhookService implements Logged {

    public MessageCreateAction sendEmbed(ForumModel forum, MessageEmbed embed) {

        val webhook = forum.webhookChannelId;
        return Objects.requireNonNull(client.getTextChannelById(webhook)).sendMessageEmbeds(embed);
    }

    public MessageEditAction editEmbed(TicketModel ticket, MessageEmbed embed) {
        // url follow format https://discord.com/channels/guildId/channelId/messageId
        val split = ticket.webhookMessageUrl.split("/");
        val channelId = Long.parseLong(split[5]);
        val messageId = Long.parseLong(split[6]);
        val channel = Objects.requireNonNull(client.getTextChannelById(channelId));

        return channel.editMessageById(messageId, MessageEditData.fromEmbeds(embed));
    }
}
