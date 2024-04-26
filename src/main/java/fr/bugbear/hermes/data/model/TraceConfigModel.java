/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

@Entity @Table(name = "trace_config")
@AllArgsConstructor @NoArgsConstructor @With
public class TraceConfigModel {
    @Id
    public UUID id;

    public String tag;
    public Long guildId;

    public ZonedDateTime fromDateTime;
    public ZonedDateTime endDateTime;

    public Long categoryChannelId;

    public Long webhookChannelId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trace_ticket_configuration_roles", joinColumns = @JoinColumn(name =
            "trace_ticket_configuration_id"))
    public Set<Long> rolesAllowed;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trace_ticket_configuration_users", joinColumns = @JoinColumn(name =
            "trace_ticket_configuration_id"))
    public Set<Long> usersAllowed;

    @ManyToOne
    public TeamModel team;

    @ManyToMany(cascade = {CascadeType.ALL})
    @JoinTable(name = "trace_config_has_manager",
            joinColumns = @JoinColumn(name = "trace_config_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "manager_id", referencedColumnName = "id")
    )
    public Set<ManagerModel> managers;

}