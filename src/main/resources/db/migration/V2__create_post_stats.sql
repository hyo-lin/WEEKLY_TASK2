CREATE TABLE post_stats (
                            post_id BIGINT PRIMARY KEY,
                            like_count INT NOT NULL DEFAULT 0,
                            comment_count INT NOT NULL DEFAULT 0,
                            CONSTRAINT fk_post_stats_post FOREIGN KEY (post_id) REFERENCES post (post_id)
);

INSERT INTO post_stats (post_id, like_count, comment_count)
SELECT p.post_id,
       (SELECT COUNT(*) FROM post_like l WHERE l.post_id = p.post_id),
       (SELECT COUNT(*) FROM comment c WHERE c.post_id = p.post_id)
FROM post p;

ALTER TABLE post
    MODIFY like_count INT NOT NULL DEFAULT 0,
    MODIFY comment_count INT NOT NULL DEFAULT 0;