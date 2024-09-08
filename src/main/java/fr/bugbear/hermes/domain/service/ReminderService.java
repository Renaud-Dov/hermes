/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.domain.service;

import fr.bugbear.hermes.Logged;
import fr.bugbear.hermes.data.repository.TicketRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;
import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.*;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReminderService implements Logged {

    @Inject WebhookService webhookService;
    @Inject TicketRepository ticketRepository;

    static final List<String> GIFS = List.of("https://media1.tenor.com/m/qhjZGEW52PUAAAAC/error.gif",
                                             "https://media1.tenor.com/m/AjVIJpzQ1W4AAAAC/late-for-a-date-running.gif",
                                             "https://media1.tenor.com/m/gF6W6a7rwXIAAAAd/swgoh-daily-tickets.gif",
                                             "https://media1.tenor.com/m/rXEmKxjDaTwAAAAC/tickets-ticket.gif",
                                             "https://media1.tenor.com/m/_Q75r5uZzY8AAAAd/once-again.gif"
    );

    public static String getGif() {
        return GIFS.get((int) (Math.random() * GIFS.size()));
    }

    public static final Short DAYS_BEFORE_REMINDER = 2;

    @Scheduled(cron = "{reminder.cron}", timeZone = "Europe/Paris", delay = 5, delayUnit = TimeUnit.SECONDS)
    public void remindOpenTickets() {
        val tickets = ticketRepository.findOpenTickets();
        val now = ZonedDateTime.now();
        tickets.stream()
               .filter(t -> t.createdAt.plusDays(DAYS_BEFORE_REMINDER).isBefore(now))
               .collect(Collectors.groupingBy(t -> t.forum))
               .forEach((forum, ts) -> {
                   logger().info("Sending reminder for forum {} with {} open tickets", forum.name, ts.size());
                   val links = ts.stream()
                                 .map(t -> "Ticket %d : https://discord.com/channels/%d/%d (created %d days ago)"
                                         .formatted(t.id,
                                                    t.guildId,
                                                    t.threadId,
                                                    t.createdAt.until(now, ChronoUnit.DAYS)))
                                 .collect(Collectors.joining("\n"));

                   val message = ("Bonjour, il reste des tickets ouvert (toujours pas pris en charge) dans le forum %s"
                                  + " depuis plus de %d jours: \n%s")
                           .formatted(forum.name, DAYS_BEFORE_REMINDER, links);

                   val embed = new EmbedBuilder()
                           .setTitle("Tickets ouverts depuis un moment")
                           .setDescription(message)
                           .setColor(Color.ORANGE)
                           .addField("Forum", forum.name, false)
                           .setImage(getGif())
                           .setFooter("%d c'est toujours moins que %d".formatted(ts.size(), ts.size() + 1))
                           .build();
                   webhookService.sendEmbed(forum, embed).queue();
               });
    }
}
