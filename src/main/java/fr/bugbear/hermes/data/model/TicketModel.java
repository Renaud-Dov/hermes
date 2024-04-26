/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@Entity @Table(name = "ticket")
@AllArgsConstructor @NoArgsConstructor @With
public class TicketModel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long guildId;
    public Long threadId;
    public String name;

    public @Enumerated(EnumType.STRING) Status status;

    public Long createdBy;
    public ZonedDateTime createdAt;
    public ZonedDateTime takenAt;
    public ZonedDateTime updatedAt;
    public ZonedDateTime closedAt;

    public Integer reopenedTimes;
    public String webhookMessageUrl;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
    public List<TicketParticipantModel> participants;

    @ManyToOne @JoinColumn(name = "forum_id")
    public ForumModel forum;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ticket_tags", joinColumns = @JoinColumn(name = "ticket_id"))
    public Set<String> tags;

    public enum Status {
        OPEN,
        IN_PROGRESS,
        CLOSED,
        DELETED
    }

}
