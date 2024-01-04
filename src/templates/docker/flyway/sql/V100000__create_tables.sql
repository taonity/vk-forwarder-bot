CREATE SEQUENCE IF NOT EXISTS vk_group_details_seq;

CREATE TABLE IF NOT EXISTS vk_group_details (
    id                              BIGINT  NOT NULL,
	group_id                        INTEGER  NOT NULL,
	last_forwarded_post_date_time   TIMESTAMP NULL,

	CONSTRAINT vk_group_details_pk PRIMARY KEY (group_id)
);

ALTER TABLE vk_group_details ALTER COLUMN id SET DEFAULT nextval('vk_group_details_seq');
ALTER SEQUENCE vk_group_details_seq OWNED BY vk_group_details.id;