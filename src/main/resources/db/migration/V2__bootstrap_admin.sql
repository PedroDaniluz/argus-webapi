-- The first account: POST /api/users requires ADMIN, so one has to exist already.
-- Values come from ADMIN_EMAIL and ADMIN_PASSWORD_HASH, with no default, so an
-- unset placeholder fails startup instead of shipping a known password.
-- Runs once; changing the password later is an UPDATE, not an edit to this file.
INSERT INTO app_user (id, email, password_hash, name, role)
VALUES ('00000000-0000-0000-0000-000000000001',
        '${admin_email}',
        '${admin_password_hash}',
        'Admin',
        'ADMIN')
ON CONFLICT (id) DO NOTHING;
