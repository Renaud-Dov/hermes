/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.presentation.bot;

import fr.bugbear.hermes.Logged;
import fr.bugbear.hermes.domain.service.DiscordService;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Startup
public class BotAdapterStarter implements Logged {

    public static JDA client;
    @Inject DiscordService discordService;
    @ConfigProperty(name = "discord.client.token") String token;

    @SneakyThrows @PostConstruct void postConstruct() {
        var botAdapter = new BotAdapter(discordService);
        var builder = JDABuilder.createDefault(token)
                                .setMemberCachePolicy(MemberCachePolicy.ALL)
                                .enableIntents(GatewayIntent.GUILD_MESSAGES,
                                               GatewayIntent.GUILD_MEMBERS,
                                               GatewayIntent.DIRECT_MESSAGES,
                                               GatewayIntent.MESSAGE_CONTENT)
                                .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.FORUM_TAGS)
                                .addEventListeners(botAdapter);

        BotAdapterStarter.client = builder.build();

    }
}