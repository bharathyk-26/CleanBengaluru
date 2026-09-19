# Database notes

The application creates its own tables (`spring.jpa.hibernate.ddl-auto=update`).
`schema.sql` is the same design written by hand, for review and for the day you
move to Flyway.

## Useful checks after testing in Postman

```sql
USE cleanbengaluru;

-- passwords must be BCrypt hashes starting with $2a$, never plain text
SELECT id, name, email, role, LEFT(password, 7) AS hash_prefix FROM users;

-- a report right after it is filed
SELECT id, user_id, garbage_type, status, priority, latitude, longitude, before_image
FROM garbage_reports ORDER BY id DESC LIMIT 5;

-- the duplicate link
SELECT id, parent_report_id, duplicate_count FROM garbage_reports WHERE parent_report_id IS NOT NULL;

-- before and after photos live in different tables and both survive
SELECT r.id, r.before_image, a.after_image, a.status
FROM garbage_reports r LEFT JOIN assignments a ON a.report_id = r.id;

-- notification trail for one user
SELECT title, message, is_read, created_at FROM notifications WHERE user_id = 4 ORDER BY id DESC;
```
