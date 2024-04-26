/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity @Table(name = "trace_ticket")
@AllArgsConstructor @NoArgsConstructor @With
public class TraceTicketModel {
    @Id
    public UUID id;
    public Long channelId;
    public Long guildId;

    public ZonedDateTime createdAt;
    public ZonedDateTime updatedAt;
    public ZonedDateTime closedAt;
    public ZonedDateTime takenAt;

    public Long vocalChannelId;

    @ManyToOne
    public TraceConfigModel traceConfig;

}