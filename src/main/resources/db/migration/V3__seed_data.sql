-- =====================================================================
-- V3: Seed data so the application is usable out of the box.
--
-- Default credentials (CHANGE IN PRODUCTION):
--   tenant: default
--   user:   admin
--   pass:   admin123
--
-- The bcrypt hash below is the result of BCryptPasswordEncoder
-- (strength 10) over the literal string "admin123".
-- =====================================================================

INSERT INTO tenants (code, name) VALUES ('default', 'Default Tenant');

-- bcrypt of "admin123" - generated via BCryptPasswordEncoder (strength 10)
-- Verified: BCryptPasswordEncoder.matches("admin123", "$2a$10$qOqTSVNjdjik2TASeoj14.N7Z3wVH0jX2L4MbBVGEuXcTKF4llzYK") == true
INSERT INTO users (tenant_id, username, password_hash, email, full_name)
VALUES (
    (SELECT id FROM tenants WHERE code = 'default'),
    'admin',
    '$2a$10$qOqTSVNjdjik2TASeoj14.N7Z3wVH0jX2L4MbBVGEuXcTKF4llzYK',
    'admin@example.com',
    'System Administrator'
);

INSERT INTO roles (code, name, description) VALUES
    ('ADMIN',    'Administrator', 'Full access to all resources'),
    ('OPERATOR', 'Operator',      'Manage devices and tasks'),
    ('VIEWER',   'Viewer',        'Read-only access');

INSERT INTO permissions (code, description) VALUES
    ('device:read',   'View device list and details'),
    ('device:write',  'Create, update, decommission devices'),
    ('user:read',     'View users'),
    ('user:write',    'Create and manage users');

-- ADMIN gets every permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';

-- OPERATOR can read everything and write devices
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'OPERATOR'
  AND p.code IN ('device:read', 'device:write', 'user:read');

-- VIEWER reads only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'VIEWER'
  AND p.code IN ('device:read', 'user:read');

-- Make admin user an ADMIN
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.code = 'ADMIN';
