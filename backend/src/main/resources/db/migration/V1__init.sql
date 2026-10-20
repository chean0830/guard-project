-- 운영 MySQL 초기 스키마. 엔티티를 MySQL 8.0 방언으로 Hibernate가 생성한 DDL이다 (docs/결정사항.md 56번).
-- 이 파일은 한 번 적용되면 고치지 않는다. 엔티티를 바꾸면 V2__설명.sql 처럼 새 파일을 추가한다
-- (MySqlSchemaTest가 스크립트와 엔티티가 어긋나면 실패한다).


create table admin_auth_tokens (
    expires_at datetime(6) not null,
    token varchar(255) not null,
    primary key (token)
) engine=InnoDB;

create table admin_messages (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    lawyer_id bigint not null,
    read_at datetime(6),
    content varchar(2000) not null,
    primary key (id)
) engine=InnoDB;

create table app_login_codes (
    expires_at datetime(6) not null,
    code_challenge varchar(64) not null,
    code_hash varchar(64) not null,
    email varchar(255) not null,
    session_token varchar(255) not null,
    primary key (code_hash)
) engine=InnoDB;

create table auth_tokens (
    expires_at datetime(6) not null,
    user_id bigint not null,
    token varchar(255) not null,
    primary key (token)
) engine=InnoDB;

create table chat_blocks (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    lawyer_id bigint not null,
    user_id bigint not null,
    blocker_type varchar(30) not null,
    primary key (id)
) engine=InnoDB;

create table consultation_messages (
    `read` bit not null,
    consultation_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    content varchar(2000) not null,
    sender_type varchar(30) not null,
    primary key (id)
) engine=InnoDB;

create table consultations (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    last_message_at datetime(6) not null,
    lawyer_id bigint not null,
    user_id bigint not null,
    primary key (id)
) engine=InnoDB;

create table device_push_tokens (
    id bigint not null auto_increment,
    owner_id bigint not null,
    updated_at datetime(6) not null,
    platform varchar(20),
    token varchar(512) not null,
    owner_type varchar(30) not null,
    primary key (id)
) engine=InnoDB;

create table email_verifications (
    failed_attempts integer not null,
    code_expires_at datetime(6) not null,
    id bigint not null auto_increment,
    used_at datetime(6),
    verified_at datetime(6),
    code_hash varchar(255) not null,
    email varchar(255) not null,
    token_hash varchar(255),
    primary key (id)
) engine=InnoDB;

create table lawyer_auth_tokens (
    expires_at datetime(6) not null,
    lawyer_id bigint not null,
    token varchar(255) not null,
    primary key (token)
) engine=InnoDB;

create table lawyer_credential_documents (
    id bigint not null auto_increment,
    lawyer_id bigint not null,
    uploaded_at datetime(6) not null,
    content_type varchar(255) not null,
    file_name varchar(255) not null,
    data longblob not null,
    primary key (id)
) engine=InnoDB;

create table lawyers (
    blocked bit not null,
    career_years integer,
    email_notifications_enabled bit not null,
    failed_login_count integer default 0 not null,
    login_locked boolean default false not null,
    blocked_at datetime(6),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    reviewed_at datetime(6),
    headline varchar(100),
    fee_info varchar(300),
    achievements varchar(1000),
    introduction varchar(1000),
    bar_number varchar(255) not null,
    blocked_reason varchar(255),
    email varchar(255) not null,
    law_firm varchar(255),
    name varchar(255) not null,
    password_hash varchar(255) not null,
    rejection_reason varchar(255),
    specialties varchar(255),
    status varchar(30) not null,
    primary key (id)
) engine=InnoDB;

create table password_reset_tokens (
    account_id bigint not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    used_at datetime(6),
    account_type varchar(255) not null,
    token_hash varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table payment_orders (
    amount bigint not null,
    canceled_at datetime(6),
    consultation_id bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    paid_at datetime(6),
    refund_requested_at datetime(6),
    used_at datetime(6),
    user_id bigint not null,
    refund_reason varchar(500),
    refund_rejected_reason varchar(500),
    order_id varchar(255) not null,
    payment_key varchar(255),
    product_type varchar(30) default 'LAWYER_SELECTION' not null,
    status varchar(30) not null,
    primary key (id)
) engine=InnoDB;

create table push_subscriptions (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    user_id bigint not null,
    -- 웹 푸시 주소는 URL(ASCII)이다. utf8mb4로 두면 1000자 = 4000바이트라 unique 인덱스 한도(3072바이트)를 넘어 생성이 실패한다.
    endpoint varchar(1000) character set ascii not null,
    auth varchar(255) not null,
    p256dh varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table rate_limit_counters (
    usage_count integer not null,
    usage_day date not null,
    id bigint not null auto_increment,
    counter_key varchar(120) not null,
    primary key (id)
) engine=InnoDB;

create table reports (
    consultation_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    reporter_id bigint not null,
    resolved_at datetime(6),
    target_id bigint not null,
    detail varchar(1000),
    reason varchar(30) not null,
    reporter_type varchar(30) not null,
    status varchar(30) not null,
    target_type varchar(30) not null,
    primary key (id)
) engine=InnoDB;

create table users (
    blocked bit not null,
    failed_login_count integer default 0 not null,
    login_locked boolean default false not null,
    blocked_at datetime(6),
    id bigint not null auto_increment,
    blocked_reason varchar(255),
    email varchar(255) not null,
    name varchar(255),
    password_hash varchar(255),
    provider varchar(255) not null,
    provider_id varchar(255),
    primary key (id)
) engine=InnoDB;

create index IDXn5jq7ommg9botpuokvacsf1h0 
   on admin_messages (lawyer_id);

alter table chat_blocks 
   add constraint UKpgj6fx1tmah12jjtym2goia0x unique (blocker_type, user_id, lawyer_id);

alter table device_push_tokens 
   add constraint UKhljr39cfhod08fqkteidqq44c unique (token);

alter table email_verifications 
   add constraint UKhjceoovthrmgk1vaeh332fex3 unique (token_hash);

alter table lawyers 
   add constraint UKkogsgwf3td6ftw4fdxu6h9h2f unique (email);

alter table password_reset_tokens 
   add constraint UKajre85ybxavf1tt4omkrs5p6g unique (token_hash);

alter table payment_orders 
   add constraint UKnay7fl271yoj5f6olh75wvut4 unique (order_id);

alter table payment_orders 
   add constraint UK3lxf38j2sjrdms4atpc4hhd5o unique (payment_key);

alter table push_subscriptions 
   add constraint UK6vabkyu7g789ehysoff7cfumf unique (endpoint);

alter table rate_limit_counters 
   add constraint UKh8fugmslux0gy0drl220irfnr unique (counter_key, usage_day);

alter table users 
   add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

alter table lawyer_credential_documents 
   add constraint FKccyg8q94q3ugg0en9mw297aob 
   foreign key (lawyer_id) 
   references lawyers (id);
