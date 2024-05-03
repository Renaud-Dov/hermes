/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

package fr.bugbear.hermes.data.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

import java.util.List;
import java.util.UUID;

@Entity @Table(name = "team")
@AllArgsConstructor @NoArgsConstructor @With
public class TeamModel {
    @Id
    public UUID id;

    public String name;

    public Long ownerId;

    @OneToMany(mappedBy = "team")
    public List<ForumModel> forums;

    @OneToMany(mappedBy = "team")
    public List<TraceConfigModel> traceConfigs;

}