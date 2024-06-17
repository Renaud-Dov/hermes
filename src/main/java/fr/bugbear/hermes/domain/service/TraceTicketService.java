/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.domain.service;

import fr.bugbear.hermes.Logged;
import fr.bugbear.hermes.data.model.ManagerModel;
import fr.bugbear.hermes.data.model.TraceConfigModel;
import fr.bugbear.hermes.data.model.TraceTicketModel;
import fr.bugbear.hermes.data.repository.TraceConfigRepository;
import fr.bugbear.hermes.data.repository.TraceTicketRepository;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.val;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static fr.bugbear.hermes.domain.entity.ModalEventType.NEW_TRACE_TICKET;
import static fr.bugbear.hermes.utils.DiscordUtils.copyMessagesToLogChannelThenDelete;
import static fr.bugbear.hermes.utils.DiscordUtils.extractUUID;
import static fr.bugbear.hermes.utils.DiscordUtils.getAllMessages;
import static fr.bugbear.hermes.utils.DiscordUtils.getOptionAsString;
import static fr.bugbear.hermes.utils.DiscordUtils.maxString;
import static fr.bugbear.hermes.utils.EmbedUtils.newTraceTicketLog;
import static fr.bugbear.hermes.utils.EmbedUtils.traceTicketRules;
import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.Permission.MANAGE_CHANNEL;
import static net.dv8tion.jda.api.Permission.MANAGE_THREADS;
import static net.dv8tion.jda.api.Permission.MESSAGE_HISTORY;
import static net.dv8tion.jda.api.Permission.MESSAGE_SEND;
import static net.dv8tion.jda.api.Permission.VIEW_CHANNEL;
import static net.dv8tion.jda.api.Permission.VOICE_CONNECT;
import static net.dv8tion.jda.api.Permission.VOICE_SPEAK;
import static net.dv8tion.jda.api.Permission.VOICE_STREAM;
import static net.dv8tion.jda.api.Permission.VOICE_USE_EXTERNAL_SOUNDS;
import static net.dv8tion.jda.api.Permission.VOICE_USE_SOUNDBOARD;
import static net.dv8tion.jda.api.interactions.commands.build.OptionData.MAX_CHOICES;

@ApplicationScoped
public class TraceTicketService implements Logged {

    @Inject TraceConfigRepository traceConfigRepository;
    @Inject TraceTicketRepository traceTicketRepository;

    @ConfigProperty(name = "discord.trace.ticket.category.name") String traceTicketCategoryName;

    /**
     * Maximum number of channels that can be created in a category
     */
    private static final Short MAX_CATEGORY_CHANNELS = 50;

    /**
     * Permissions for managers
     */
    private static final List<Permission> MANAGER_PERMISSIONS = List.of(VIEW_CHANNEL,
                                                                        MESSAGE_SEND,
                                                                        MESSAGE_HISTORY,
                                                                        MANAGE_CHANNEL,
                                                                        MANAGE_THREADS);

    /**
     * Permissions for ticket creator in text channel
     */
    private static final List<Permission> USER_TEXT_PERMISSIONS = List.of(VIEW_CHANNEL,
                                                                          MESSAGE_SEND,
                                                                          MESSAGE_HISTORY);
    /**
     * Permissions for ticket creator in vocal channel
     */
    private static final List<Permission> USER_VOICE_PERMISSIONS = List.of(VIEW_CHANNEL,
                                                                           VOICE_CONNECT,
                                                                           VOICE_SPEAK,
                                                                           VOICE_STREAM);

    /**
     * Prohibited permissions for ticket creator in vocal channel
     */
    private static final List<Permission> USER_VOICE_PROHIBITED_PERMISSIONS = List.of(VOICE_USE_SOUNDBOARD,
                                                                                      MESSAGE_SEND,
                                                                                      VOICE_USE_EXTERNAL_SOUNDS);

    /**
     * Get the manager configuration for a member
     *
     * @param member
     *         the member
     * @param traceTicket
     *         the trace ticket
     *
     * @return the manager configuration if the member is allowed to manage the ticket
     */
    public Optional<ManagerModel> getManagerConfig(Member member, TraceTicketModel traceTicket) {
        val traceConfig = traceTicket.traceConfig;

        return traceConfig.managers.stream()
                                   .filter(m -> m.users.contains(member.getIdLong()) ||
                                                m.roles.stream()
                                                       .anyMatch(role -> member.getRoles()
                                                                               .stream()
                                                                               .anyMatch(r -> r.getIdLong()
                                                                                              == role)))
                                   .findFirst();
    }

    /**
     * Check if a member can use a tag
     *
     * @param member
     *         the member
     * @param tagConfig
     *         the tag configuration
     *
     * @return true if the member can use the tag
     */
    public boolean canMemberUseTag(Member member, TraceConfigModel tagConfig) {
        return tagConfig.usersAllowed.contains(member.getIdLong()) ||
               tagConfig.rolesAllowed.stream()
                                     .anyMatch(role -> member.getRoles()
                                                             .stream()
                                                             .anyMatch(r -> r.getIdLong() == role));

    }

    /**
     * Get the available category for a guild or create a new one
     *
     * @param guild
     *         the guild
     * @param tagConfig
     *         the tag configuration
     *
     * @return the category
     */
    private Category getAvailableOrCreateCategory(Guild guild, TraceConfigModel tagConfig) {
        val categories = guild.getCategoriesByName(traceTicketCategoryName, true);

        // find a category with maximum of 50 channels
        return categories
                .stream()
                .filter(category -> category.getChannels().size() < MAX_CATEGORY_CHANNELS)
                .findFirst()
                .orElseGet(() -> {
                    logger().info("Creating a new category {} for guild {}", traceTicketCategoryName, guild.getId());
                    var category = guild.createCategory(traceTicketCategoryName)
                                        .setPosition(categories.stream().mapToInt(Category::getPosition).max()
                                                               .orElse(0) + 1)
                                        // make the category private
                                        .addPermissionOverride(guild.getPublicRole(), List.of(), List.of(VIEW_CHANNEL))
                                        .addPermissionOverride(guild.getBotRole(), List.of(VIEW_CHANNEL), List.of())
                                        .complete();

                    val categoryManager = category.getManager();

                    tagConfig.managers.forEach(manager -> {
                        //noinspection ResultOfMethodCallIgnored
                        manager.roles.stream()
                                     .map(guild::getRoleById)
                                     .filter(Objects::nonNull)
                                     .forEach(r -> categoryManager.putRolePermissionOverride(r.getIdLong(),
                                                                                             MANAGER_PERMISSIONS,
                                                                                             List.of()));
                        //noinspection ResultOfMethodCallIgnored
                        manager.users.stream()
                                     .map(guild::getMemberById)
                                     .filter(Objects::nonNull)
                                     .forEach(u -> categoryManager
                                             .putMemberPermissionOverride(u.getIdLong(),
                                                                          MANAGER_PERMISSIONS,
                                                                          List.of()));
                    });
                    categoryManager.queue();
                    return category;
                });
    }

    /**
     * Create a trace ticket
     *
     * @param event
     *         the event
     */
    @Transactional
    public void traceTicket(SlashCommandInteractionEvent event) {
        val tagOption = getOptionAsString(event, "tag").orElseThrow();
        // check that the event was triggered in a thread channel
        val tagConfigModel = traceConfigRepository.findByTag(requireNonNull(event.getGuild()).getIdLong(),
                                                             tagOption);
        if (tagConfigModel.isEmpty()) {
            event.reply("This tag does not exist or is not open").setEphemeral(true).queue();
            return;
        }
        val tagConfig = tagConfigModel.get();
        val member = requireNonNull(event.getMember());
        if (!canMemberUseTag(member, tagConfig)) {
            event.reply("You are not allowed to create a trace ticket with this tag.").setEphemeral(true).queue();
            return;
        }

        logger().info("User {} is creating a trace ticket with tag {}. Sending modal", member.getId(), tagConfig.id);

        TextInput login = TextInput.create("login", "Login", TextInputStyle.SHORT)
                                   .setPlaceholder("xavier.login")
                                   .setRequired(true)
                                   .build();

        TextInput question = TextInput.create("question", "Question", TextInputStyle.PARAGRAPH)
                                      .setPlaceholder("What is the issue?")
                                      .setRequired(false)
                                      .build();

        // trace the ticket
        Modal modal = Modal.create("%s-%s".formatted(NEW_TRACE_TICKET, tagConfig.id), "Trace ticket")
                           .addComponents(ActionRow.of(login), ActionRow.of(question)).build();
        event.replyModal(modal).queue();
    }

    /**
     * Modal interaction event for trace ticket
     *
     * @param event
     *         the event
     */
    @Transactional
    public void onModalTraceTicket(@Nonnull ModalInteractionEvent event) {
        // get uuid from the modal id
        val hasTagId = extractUUID(NEW_TRACE_TICKET, event.getModalId());
        if (hasTagId.isEmpty()) {
            logger().error("Could not match the modal id : {}", event.getModalId());
            event.reply("An error occurred").setEphemeral(true).queue();
            return;
        }
        val tagId = hasTagId.get();

        logger().info("Modal interaction event for trace ticket with tag {}", tagId);

        val login = requireNonNull(event.getValue("login")).getAsString();
        val tagConfig = traceConfigRepository.findByIdOptional(tagId).orElseThrow();

        val question = event.getValue("question");
        val guild = requireNonNull(event.getGuild());
        val member = requireNonNull(event.getMember());
        val category = getAvailableOrCreateCategory(guild, tagConfig);
        val webhookChannel = requireNonNull(event.getJDA().getTextChannelById(tagConfig.webhookChannelId));

        val newChannelName = maxString("trace-%s".formatted(login.replace(".", "_")), 100, false);
        val newChannel = category.createTextChannel(newChannelName).complete();

        newChannel.getManager()
                  .putMemberPermissionOverride(member.getIdLong(), USER_TEXT_PERMISSIONS, List.of())
                  .complete();
        event.reply("New channel created: %s".formatted(newChannel.getAsMention())).setEphemeral(true).queue();

        val traceTicket = new TraceTicketModel()
                .withId(UUID.randomUUID())
                .withTraceConfig(tagConfig)
                .withGuildId(event.getGuild().getIdLong())
                .withChannelId(newChannel.getIdLong())
                .withCreatedAt(ZonedDateTime.now())
                .withCreatedBy(event.getUser().getIdLong());

        traceTicketRepository.persist(traceTicket);

        newChannel.sendMessageEmbeds(traceTicketRules())
                  .addContent("%s (login: %s)".formatted(event.getUser().getAsMention(), login))
                  .queue();
        if (question != null && !question.getAsString().isEmpty())
            newChannel.sendMessage(question.getAsString()).queue();

        webhookChannel.sendMessageEmbeds(newTraceTicketLog(traceTicket, newChannel, member, login, question))
                      .addActionRow(Button.link(newChannel.getJumpUrl(), "Go to"))
                      .queue();
    }

    /**
     * Associate a vocal channel to a trace ticket
     *
     * @param event
     *         the event
     */
    @Transactional
    public void associateVocalChannel(SlashCommandInteractionEvent event) {
        // check that the event was triggered in a text channel
        if (event.getChannel().getType() != ChannelType.TEXT) {
            event.reply("This command must be used in a text channel").setEphemeral(true).queue();
            return;
        }
        val guild = requireNonNull(event.getGuild());
        val channel = event.getChannel().asTextChannel();

        event.deferReply().setEphemeral(true).queue();

        val traceTicketModel = traceTicketRepository.findByChannel(channel);
        if (traceTicketModel.isEmpty()) {
            event.getHook().editOriginal("This channel is not a trace ticket").queue();
            return;
        }
        val traceTicket = traceTicketModel.get();

        // check if manager
        if (getManagerConfig(event.getMember(), traceTicket).isEmpty()) {
            event.getHook().editOriginal("You are not allowed to associate a vocal channel to this trace ticket")
                 .queue();
            return;
        }

        if (traceTicket.vocalChannelId != null) {
            event.getHook().editOriginal("This trace ticket is already associated with the vocal channel <#%s>"
                                                 .formatted(traceTicket.vocalChannelId)).queue();
            return;
        }
        logger().info("User {} created a vocal channel for trace ticket {}",
                      event.getUser().getId(),
                      traceTicket.id);
        val vocalChannel = guild.createVoiceChannel("vocal-%s".formatted(channel.getName()),
                                                    channel.getParentCategory()).complete();
        vocalChannel.getManager()
                    .putMemberPermissionOverride(traceTicket.createdBy,
                                                 USER_VOICE_PERMISSIONS,
                                                 USER_VOICE_PROHIBITED_PERMISSIONS)
                    .complete();
        traceTicket.updatedAt = ZonedDateTime.now();
        traceTicket.vocalChannelId = vocalChannel.getIdLong();
        channel.sendMessage("Vocal channel created %s <@%s>".formatted(vocalChannel.getAsMention(),
                                                                       traceTicket.createdBy)).queue();

        event.getHook().editOriginal("Vocal channel created %s".formatted(vocalChannel.getAsMention())).queue();
        vocalChannel.sendMessage("If you want to write something, please use the text channel %s"
                                         .formatted(channel.getAsMention())).queue();
    }

    /**
     * Close a trace ticket and copy messages to the log channel
     *
     * @param event
     *         the event
     */
    @Transactional
    public void closeTraceTicket(SlashCommandInteractionEvent event) {
        if (event.getChannel().getType() != ChannelType.TEXT) {
            event.reply("This command must be used in a text channel").setEphemeral(true).queue();
            return;
        }
        val guild = requireNonNull(event.getGuild());
        val channel = event.getChannel().asTextChannel();

        event.deferReply().setEphemeral(true).queue();

        val traceTicketModel = traceTicketRepository.findByChannel(channel);
        if (traceTicketModel.isEmpty()) {
            event.getHook().editOriginal("This channel is not a trace ticket").queue();
            return;
        }
        val traceTicket = traceTicketModel.get();

        if (getManagerConfig(event.getMember(), traceTicket).isEmpty()) {
            event.getHook().editOriginal("You are not allowed to close this trace ticket").queue();
            return;
        }

        val webhookChannel = requireNonNull(event.getJDA()
                                                 .getTextChannelById(traceTicket.traceConfig.webhookChannelId));
        copyMessagesToLogChannelThenDelete(getAllMessages(channel),
                                           webhookChannel,
                                           maxString("log-%s".formatted(channel.getName()), 100, false),
                                           channel);

        traceTicket.updatedAt = ZonedDateTime.now();
        traceTicket.closedAt = ZonedDateTime.now();

        if (traceTicket.vocalChannelId != null) {
            val vocalChannel = guild.getVoiceChannelById(traceTicket.vocalChannelId);
            if (vocalChannel == null)
                logger().warn("Vocal channel %s not found, skipping deletion".formatted(traceTicket.vocalChannelId));
            else
                vocalChannel.delete().queue();
        }
    }

    /**
     * Auto complete for trace ticket command
     *
     * @param event
     *         the event
     */
    public void traceAutoComplete(CommandAutoCompleteInteractionEvent event) {
        val member = requireNonNull(event.getMember());
        val guild = requireNonNull(event.getGuild());
        if (!event.getFocusedOption().getName().equals("tag")) {
            logger().error("Unknown option name : {}", event.getFocusedOption().getName());
            event.replyChoices(List.of()).queue();
            return;
        }
        val focusedOptionValue = event.getFocusedOption().getValue();

        val now = ZonedDateTime.now();
        val availableTags = traceConfigRepository.findTagsByGuild(guild)
                                                 .stream()
                                                 .filter(c -> c.fromDateTime.isBefore(now)
                                                              && c.endDateTime.isAfter(now))
                                                 .toList();

        val tags = availableTags.stream()
                                .filter(tag -> canMemberUseTag(member, tag))
                                .map(tag -> tag.tag)
                                .toList();

        if (focusedOptionValue.isBlank()) {
            event.replyChoiceStrings(tags.stream().limit(MAX_CHOICES).toList()).queue();
            return;
        }
        event.replyChoiceStrings(tags.stream()
                                     .filter(tag -> tag.toLowerCase().contains(focusedOptionValue))
                                     .limit(MAX_CHOICES)
                                     .toList())
             .queue();
    }
}
