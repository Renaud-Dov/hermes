/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

alter table team
    add column owner_id bigint;

alter table team
    add column name varchar(255);

alter table forum
    add column name varchar(255);