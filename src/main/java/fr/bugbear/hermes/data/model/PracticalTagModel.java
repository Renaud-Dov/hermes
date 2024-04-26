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

@Entity @Table(name = "practical_tag")
@AllArgsConstructor @NoArgsConstructor @With
public class PracticalTagModel {
    @Id
    public UUID id;

    public Long tagId;

    public ZonedDateTime fromDateTime;
    public ZonedDateTime endDateTime;

    @ManyToOne
    public ForumModel forum;
}