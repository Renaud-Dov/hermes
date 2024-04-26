/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */

/*
 * Copyright (c) 2024.  Dov Devers <renaud-dov.devers@epita.fr>
 * All right reserved.
 */


create table manager
(
    id             uuid not null
        primary key,
    custom_message varchar(255),
    name           varchar(255)
);


create table manager_roles
(
    roles      bigint,
    manager_id uuid not null
        constraint fkso1dp4tmjqlsqq8jh0tgrcrf3
            references manager
);



create table manager_users
(
    users      bigint,
    manager_id uuid not null
        constraint fki136yguwg3acx2kwh3vldij32
            references manager
);



create table team
(
    id uuid not null primary key
);


create table forum
(
    id uuid not null primary key,
    channel_id         bigint,
    webhook_channel_id bigint,
    team_id            uuid
        constraint fkn91epmq59m0bmj8b4uvnc1sv2
            references team,
    trace_tag          varchar(255)
);



create table forum_has_manager
(
    forum_id   uuid not null
        constraint fkivop1wwr9dxm0ean7bli52pxx
            references forum,
    manager_id uuid not null
        constraint fkitl0is2177o0ehyffopyx9xi0
            references manager,
    primary key (forum_id, manager_id)
);



create table practical_tag
(
    id             uuid not null primary key,
    end_date_time  timestamp(6) with time zone,
    from_date_time timestamp(6) with time zone,
    tag_id         bigint,
    forum_id       uuid
        constraint fkahp6tf9ha2vpeb7w124j71vty references forum
);



create table ticket
(
    id                  bigserial primary key,
    reopened_times      integer,
    closed_at           timestamp(6) with time zone,
    created_at          timestamp(6) with time zone,
    created_by          bigint,
    guild_id            bigint,
    taken_at            timestamp(6) with time zone,
    thread_id           bigint,
    updated_at          timestamp(6) with time zone,
    forum_id            uuid
        constraint fkcyi2d2h2n7b7uux2ql9d0hces references forum,
    name                varchar(255),
    status              varchar(255),
    webhook_message_url varchar(255)
);



create table ticket_participant
(
    id        uuid not null primary key,
    taken_at  timestamp(6) with time zone,
    user_id   bigint,
    ticket_id bigint
        constraint fkq3qyhygt8yobbfjhhyu7hfxv
            references ticket
);


create table ticket_tags
(
    ticket_id bigint not null
        constraint fk7iugdprhys0j2ml5xekwm7uk6
            references ticket,
    tags      varchar(255)
);


create table trace_config
(
    id                  uuid not null primary key,
    category_channel_id bigint,
    end_date_time       timestamp(6) with time zone,
    from_date_time      timestamp(6) with time zone,
    guild_id            bigint,
    webhook_channel_id  bigint,
    team_id             uuid
        constraint fk90ghd52ambruk083gnwyy0aul
            references team,
    tag                 varchar(255)
);


create table trace_config_has_manager
(
    manager_id      uuid not null
        constraint fki70tr3kscckonirpg34c8f22w
            references manager,
    trace_config_id uuid not null
        constraint fksm43hnjdguma0ro99v1b6x9t9
            references trace_config,
    primary key (manager_id, trace_config_id)
);


create table trace_ticket
(
    id               uuid not null primary key,
    channel_id       bigint,
    closed_at        timestamp(6) with time zone,
    created_at       timestamp(6) with time zone,
    guild_id         bigint,
    taken_at         timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    vocal_channel_id bigint,
    trace_config_id  uuid
        constraint fkqu66n6ddxvi9y8gn43jm92x8l
            references trace_config
);


create table trace_ticket_configuration_roles
(
    roles_allowed                 bigint,
    trace_ticket_configuration_id uuid not null
        constraint fkqckcy9akie52s8h7w5civm3gt
            references trace_config
);


create table trace_ticket_configuration_users
(
    users_allowed                 bigint,
    trace_ticket_configuration_id uuid not null
        constraint fkc7mriusih9x3nkkon6b5mxiuf
            references trace_config
);


