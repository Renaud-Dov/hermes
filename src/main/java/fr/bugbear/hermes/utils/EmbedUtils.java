/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.utils;

import fr.bugbear.hermes.data.model.TicketModel;
import fr.bugbear.hermes.data.model.TraceTicketModel;
import fr.bugbear.hermes.domain.entity.CloseType;
import jakarta.annotation.Nullable;
import lombok.val;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.awt.*;
import java.time.ZonedDateTime;

public class EmbedUtils {

    public static MessageEmbed getCloseTicketMessage(CloseType type,
                                                     Member member,
                                                     String reason,
                                                     String customMessage) {
        if (customMessage == null || customMessage.isEmpty()) {
            customMessage = "Ticket has been closed by an assistant.";
        }
        var embedBuilder = new EmbedBuilder()
                .setTitle(customMessage)
                .setColor(Color.BLUE)
                .setDescription(reason)
                .setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl())
                .setFooter("If you have any further questions, please create a new ticket.")
                .setTimestamp(ZonedDateTime.now().toInstant());

        if (type == CloseType.DUPLICATE) {
            embedBuilder.setTitle("This question has already been answered. Please check if your "
                                  + "question is already answered before creating a new ticket.")
                        .setColor(Color.RED);
        }
        return embedBuilder.build();

    }

    public static MessageEmbed getTicketWebhookEmbed(TicketModel ticket, Member author) {
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

    public static MessageEmbed getPrivateCloseTicketMessage(TicketModel ticket,
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

    public static MessageEmbed newTraceTicketLog(TraceTicketModel traceTicket,
                                                 TextChannel channel,
                                                 Member member,
                                                 String login,
                                                 @Nullable ModalMapping reason) {
        return new EmbedBuilder()
                .setTitle("New trace ticket")
                .setDescription(reason == null ? "No reason provided" : reason.getAsString())
                .setColor(Color.BLUE)
                .setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl())
                .addField("Tag", traceTicket.traceConfig.tag, true)
                .addField("Login", login, true)
                .addField("Channel", channel.getAsMention(), true)
                .setTimestamp(ZonedDateTime.now().toInstant())
                .build();
    }

    public static MessageEmbed traceTicketRules() {
        return new EmbedBuilder()
                .setTitle("Trace ticket rules")
                .setDescription(
                        "Tout ce qui est écrit dans ce channel est visible par les assistants, "
                        + "ainsi que les \n"
                        + "    modérateurs du serveur. Si vous souhaitez que votre question reste "
                        + "privée, "
                        + "merci de ne pas la poser ici.\n"
                        + "    Le partage de code est autorisé, uniquement sur ce channel. Si vous "
                        + "souhaitez "
                        + "partager du code, merci de le mettre \n"
                        + "    dans un [code block](https://support.discord"
                        + ".com/hc/fr/articles/210298617) ou "
                        + "par fichier.\n"
                        + "    \n"
                        + "    Cordialement,\n"
                        + "    L'équipe assistante.")
                .setColor(Color.GREEN)
                .build();
    }
}
