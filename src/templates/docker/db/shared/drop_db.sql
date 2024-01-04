-- TODO remove db name
UPDATE pg_database SET datallowconn = 'false' WHERE datname = 'vk_forwarder_bot';

SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'vk_forwarder_bot';

DROP DATABASE vk_forwarder_bot;

UPDATE pg_database SET datallowconn = 'true' WHERE datname = 'vk_forwarder_bot';



