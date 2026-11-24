-- Demo accounts. Plain-text passwords are listed in README.md:
--   manager@example.com        / Manager123!
--   officer.<market><n>@example.com / Officer123!   (2 officers per market)
--   claimant<n>@example.com    / Claimant123!
INSERT INTO users (id, email, password_hash, full_name, role, market) VALUES
    ('00000000-0000-0000-0000-000000000001', 'manager@example.com', '$2a$10$sv764z9pL00suXNJjpglAO.gxjjh2WgqcmSl80l0liR3gwV3YA/2e', 'Demo Manager', 'MANAGER', NULL),
    ('00000000-0000-0000-0001-000000000011', 'officer.my1@example.com', '$2a$10$T86pkGrlL6jQreQ1/aSvquV7h/PJFAz7HFrDVwXHlJRjdTpupJ/S6', 'Demo Officer MY-1', 'OFFICER', 'MY'),
    ('00000000-0000-0000-0001-000000000012', 'officer.my2@example.com', '$2a$10$jfgtDALKC6NbBi61htaeSO/XzbpDyF9XA8y.SoPzi4pPVRMnNIO12', 'Demo Officer MY-2', 'OFFICER', 'MY'),
    ('00000000-0000-0000-0001-000000000021', 'officer.sg1@example.com', '$2a$10$tlaWP87iQBgO/BJW5UuUu.ozmhaI1Peh0.UWeXsv0ifP9UIwd/Cue', 'Demo Officer SG-1', 'OFFICER', 'SG'),
    ('00000000-0000-0000-0001-000000000022', 'officer.sg2@example.com', '$2a$10$/zy3edkvGEjiQZ2/Kgdky.wuNdChPRRXjtXWQdSvLXA1hICiTdfJC', 'Demo Officer SG-2', 'OFFICER', 'SG'),
    ('00000000-0000-0000-0001-000000000031', 'officer.th1@example.com', '$2a$10$X4xEI2ytbMpssFMMRC0d5Okff18TDUIZ6A15sHZ40a4vhitvTYHki', 'Demo Officer TH-1', 'OFFICER', 'TH'),
    ('00000000-0000-0000-0001-000000000032', 'officer.th2@example.com', '$2a$10$B9YTs9UoeztZhuhhlU1zOOmrtlwlfXmlsoz2aKSGyPXs0S3X2mdGi', 'Demo Officer TH-2', 'OFFICER', 'TH'),
    ('00000000-0000-0000-0001-000000000041', 'officer.id1@example.com', '$2a$10$EgX1u0eDPID63RI1TN/PsO/2Y41cZxRxv8/jgXZbULcTMKrln9rTu', 'Demo Officer ID-1', 'OFFICER', 'ID'),
    ('00000000-0000-0000-0001-000000000042', 'officer.id2@example.com', '$2a$10$Dlzd6Uw6HLSp0ylTqOcQBu3Zvj6K9gPhEefBsQSRCCQ1vM0Umzrc.', 'Demo Officer ID-2', 'OFFICER', 'ID'),
    ('00000000-0000-0000-0001-000000000051', 'officer.ph1@example.com', '$2a$10$mWsAtRYOanfRRbeLl9MdVOABPP1EkBMz0WV6E9CfZug6Koh9fZT86', 'Demo Officer PH-1', 'OFFICER', 'PH'),
    ('00000000-0000-0000-0001-000000000052', 'officer.ph2@example.com', '$2a$10$KlehPzeysUzMX7R7kzb/bOe4KTDq.kUbTng0NhXhwjGb5dCeYxlhu', 'Demo Officer PH-2', 'OFFICER', 'PH'),
    ('00000000-0000-0000-0001-000000000061', 'officer.vn1@example.com', '$2a$10$4XnAzSMIzQy2qnwFswlLmee9zzLdtDHpiXx4ZIyFfgq43Kd9tFuyK', 'Demo Officer VN-1', 'OFFICER', 'VN'),
    ('00000000-0000-0000-0001-000000000062', 'officer.vn2@example.com', '$2a$10$CtNFSq8RylMaMsWS3dySOOuw0bJNlmsHUGfHNkZqna05T.sNHIi/.', 'Demo Officer VN-2', 'OFFICER', 'VN'),
    ('00000000-0000-0000-0002-000000000001', 'claimant1@example.com', '$2a$10$oaJej7RZTii9VP3WzUBAL.bXZpDXVDW2wZ64ceZyq6Wmecq0AFvEy', 'Demo Claimant 1', 'CLAIMANT', NULL),
    ('00000000-0000-0000-0002-000000000002', 'claimant2@example.com', '$2a$10$tRYudSm.8ACubKhYaPz0bO3y1YrMS5uQTSROXKyG3dO0PCXrMcU3.', 'Demo Claimant 2', 'CLAIMANT', NULL);
