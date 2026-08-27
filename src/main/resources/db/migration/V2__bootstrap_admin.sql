-- Primeira conta. Sem ela nao ha como chamar POST /api/users, que exige ADMIN:
-- alguem tem que existir antes do primeiro login.
--
-- Email e hash saem de placeholder do Flyway, com default para localhost. Para
-- usar outra credencial, defina ADMIN_EMAIL e ADMIN_PASSWORD_HASH no .env antes
-- da primeira subida.
--
-- ON CONFLICT DO NOTHING: rodou uma vez, nunca mais mexe. Trocar a senha depois
-- e UPDATE no banco, nao alteracao desta migration.
INSERT INTO app_user (id, email, password_hash, name, role)
VALUES ('00000000-0000-0000-0000-000000000001',
        '${admin_email}',
        '${admin_password_hash}',
        'Admin',
        'ADMIN')
ON CONFLICT (id) DO NOTHING;
