-- Runs once, on first start of an empty Postgres volume.
-- One schema per service, owned by that service's DB user (design section 2).
-- No user gets USAGE on another service's schema, so cross-schema access is impossible.
-- Passwords are local-dev only; override via env in any shared environment.

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE claims_platform FROM PUBLIC;

CREATE USER auth_svc      WITH PASSWORD 'auth_svc';
CREATE USER claims_svc    WITH PASSWORD 'claims_svc';
CREATE USER reporting_svc WITH PASSWORD 'reporting_svc';

GRANT CONNECT ON DATABASE claims_platform TO auth_svc, claims_svc, reporting_svc;

CREATE SCHEMA auth      AUTHORIZATION auth_svc;
CREATE SCHEMA claims    AUTHORIZATION claims_svc;
CREATE SCHEMA reporting AUTHORIZATION reporting_svc;

ALTER USER auth_svc      SET search_path = auth;
ALTER USER claims_svc    SET search_path = claims;
ALTER USER reporting_svc SET search_path = reporting;
