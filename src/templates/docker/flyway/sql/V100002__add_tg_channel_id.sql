ALTER TABLE vk_group_details RENAME COLUMN group_id to vk_group_id;
ALTER TABLE vk_group_details ADD COLUMN tg_channel_id TEXT NOT NULL DEFAULT 'NONE';