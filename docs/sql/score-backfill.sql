INSERT INTO score (user_id, nickname, highest_score)
SELECT users.id, users.nickname, COALESCE(users.highest_score, 0)
FROM users
ON DUPLICATE KEY UPDATE highest_score = GREATEST(score.highest_score, VALUES(highest_score));

-- 이관이 끝나면 이 쿼리의 결과가 0행이어야 한다.
SELECT users.id, users.highest_score AS legacy_score, score.highest_score AS migrated_score
FROM users
LEFT JOIN score ON score.user_id = users.id
WHERE score.user_id IS NULL
   OR score.highest_score < COALESCE(users.highest_score, 0);
