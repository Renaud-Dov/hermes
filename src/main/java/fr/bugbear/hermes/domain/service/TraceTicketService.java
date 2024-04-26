/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.domain.service;

import fr.bugbear.hermes.Logged;
import fr.bugbear.hermes.data.repository.TraceConfigRepository;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.val;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static fr.bugbear.hermes.utils.DiscordUtils.getOptionAsString;
import static net.dv8tion.jda.api.Permission.MESSAGE_SEND;
import static net.dv8tion.jda.api.Permission.VIEW_CHANNEL;

@ApplicationScoped
public class TraceTicketService implements Logged {

    @Inject TraceConfigRepository traceConfigRepository;

    @Transactional
    public void traceTicket(SlashCommandInteractionEvent event) {
        val tagOption = getOptionAsString(event, "tag").orElseThrow();
        // check that the event was triggered in a thread channel
        event.deferReply().setEphemeral(true).queue();
        val tagConfig = traceConfigRepository.findByTag(event.getGuild().getIdLong(), tagOption).orElseThrow();

        if (!tagConfig.usersAllowed.contains(event.getUser().getIdLong()) &&
            tagConfig.rolesAllowed.stream()
                                  .noneMatch(role -> event.getMember()
                                                          .getRoles()
                                                          .stream()
                                                          .anyMatch(r -> r.getIdLong() == role))) {
            event.reply("You are not allowed to trace tickets with this tag").queue();
        }

        TextInput login = TextInput.create("login", "Login", TextInputStyle.SHORT)
                                   .setPlaceholder("xavier.login")
                                   .setRequired(true)
                                   .build();

        TextInput question = TextInput.create("question", "Question", TextInputStyle.PARAGRAPH)
                                      .setPlaceholder("What is the issue?")
                                      .setRequired(false)
                                      .build();

        // trace the ticket
        Modal modal = Modal.create("trace-create-modal-%s".formatted(tagConfig.id), "Trace ticket")
                           .addComponents(ActionRow.of(login), ActionRow.of(question)).build();

        event.replyModal(modal).queue();
    }

    public void onModalTraceTicket(@Nonnull ModalInteractionEvent event) {
        val tagId = UUID.fromString(event.getModalId().split("-")[1]);
        val login = Objects.requireNonNull(event.getValue("login")).getAsString();
        val question = event.getValue("question");

        val tagConfig = traceConfigRepository.findByIdOptional(tagId).orElseThrow();

        // create channel inside the category of the tag
        val category = Objects.requireNonNull(Objects.requireNonNull(event.getGuild())
                                                     .getCategoryById(tagConfig.categoryChannelId));
        val newChannel = event.getGuild()
                              .createTextChannel("trace-%s".formatted(login.replace(".", "_")), category)
                              .addMemberPermissionOverride(event.getUser().getIdLong(),
                                                           List.of(VIEW_CHANNEL, MESSAGE_SEND),
                                                           List.of())
                              .complete();
        event.reply("New channel created: %s".formatted(newChannel.getAsMention())).setEphemeral(true).queue();

        newChannel.sendMessageEmbeds(
                          new EmbedBuilder()
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
                                  .build())
                  .addContent(question != null ? "%s Question: %s".formatted(event.getUser().getAsMention(),
                                                                             question.getAsString())
                                               : event.getUser().getAsMention())
                  .queue();
    }

    public void closeTraceTicket(SlashCommandInteractionEvent event) {
        // check that the event was triggered in a thread channel
        event.deferReply().setEphemeral(true).queue();

        // close the trace ticket
        event.reply("Trace ticket closed").queue();

        // TODO: implement the closing of the trace ticket
    }
}
