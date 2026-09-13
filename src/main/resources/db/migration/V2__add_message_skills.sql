CREATE TABLE message_skills (
    message_id INTEGER NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    skill VARCHAR(128) NOT NULL,
    PRIMARY KEY (message_id, position)
);
