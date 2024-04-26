/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity @Table(name = "forum")
@AllArgsConstructor @NoArgsConstructor @With
public class ForumModel {
    @Id
    public UUID id;
    public Long channelId;

    public Long webhookChannelId;

    public String traceTag;

    @OneToMany(mappedBy = "forum")
    public List<TicketModel> tickets;

    @OneToMany(mappedBy = "forum")
    public List<PracticalTagModel> practicalTags;

    @ManyToMany(cascade = {CascadeType.ALL})
    @JoinTable(name = "forum_has_manager",
            joinColumns = @JoinColumn(name = "forum_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "manager_id", referencedColumnName = "id")
    )
    public Set<ManagerModel> managers;

    @ManyToOne
    public TeamModel team;

}