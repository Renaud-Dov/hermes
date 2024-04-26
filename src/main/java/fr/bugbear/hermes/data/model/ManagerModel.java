/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity @Table(name = "manager")
@AllArgsConstructor @NoArgsConstructor @With
public class ManagerModel {
    @Id
    public UUID id;

    public String name;
    public String customMessage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "manager_roles", joinColumns = @JoinColumn(name = "manager_id"))
    public List<Long> roles;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "manager_users", joinColumns = @JoinColumn(name = "manager_id"))
    public List<Long> users;

    @ManyToMany(mappedBy = "managers")
    public Set<ForumModel> forum;

    @ManyToMany(mappedBy = "managers")
    public Set<TraceConfigModel> traceConfig;

}