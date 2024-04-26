/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.utils;

import fr.bugbear.hermes.data.model.TicketModel;
import fr.bugbear.hermes.domain.entity.CloseType;
import jakarta.enterprise.context.ApplicationScoped;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.awt.*;
import java.time.ZonedDateTime;

@ApplicationScoped
public class EmbedUtils {

    public MessageEmbed getCloseTicketMessage(CloseType type, Member member, String reason) {
        var embedBuilder = new EmbedBuilder()
                .setColor(Color.BLUE)
                .setDescription(reason)
                .setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl())
                .setFooter("If you have any further questions, please create a new ticket.")
                .setTimestamp(ZonedDateTime.now().toInstant());

        switch (type) {
            case RESOLVE:
            case FORCE_CLOSE:
                embedBuilder = embedBuilder.setTitle("Ticket has been closed by an assistant.")
                                           .setColor(Color.BLUE);
                break;
            case DUPLICATE:
                embedBuilder = embedBuilder.setTitle("This question has already been answered. Please check if your "
                                                     + "question is already answered before creating a new ticket.")
                                           .setColor(Color.RED);
                break;
            default:
                break;
        }
        return embedBuilder.build();

    }

    public MessageEmbed getPrivateCloseTicketMessage(TicketModel ticket,
                                                     ThreadChannel thread,
                                                     CloseType type,
                                                     Member member,
                                                     String reason) {
        var embedBuilder = new EmbedBuilder()
                .setTitle(String.format(
                        "Your ticket %s has been closed by %s. "
                        + "If you want to reopen it, please click on the button below.",
                        thread.getJumpUrl(),
                        member.getAsMention()))
                .setDescription(reason)
                .setColor(Color.BLUE)
                .setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl())
                .addField("Ticket Name", thread.getName(), true)
                .addField("Ticket ID", ticket.id.toString(), true)

                .setTimestamp(ZonedDateTime.now().toInstant());

        switch (type) {
            case RESOLVE:
                embedBuilder = embedBuilder.addField("Time to reopen the ticket",
                                                     TimeFormat.RELATIVE.format(ticket.closedAt.plusHours(8)
                                                                                               .toInstant()),
                                                     true);
            case FORCE_CLOSE:
                embedBuilder = embedBuilder.setTitle("Ticket has been closed by an assistant.");
                break;
            case DUPLICATE:
                embedBuilder = embedBuilder.setTitle("This question has already been answered. Please check if your "
                                                     + "question is already answered before creating a new ticket.")
                                           .setColor(Color.RED);
                break;
            case DELETE:
                embedBuilder = embedBuilder.setTitle("Ticket has been deleted.")
                                           .setColor(Color.RED);
                break;
            default:
                break;
        }
        return embedBuilder.build();
    }
}
