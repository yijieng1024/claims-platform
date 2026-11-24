-- Fake demo policies, listed in README.md. Holder emails of the first six match seeded auth accounts.
INSERT INTO policies (id, policy_number, holder_name, holder_email, product_type, market, currency, insured_item, sum_insured, start_date, end_date, status) VALUES
    -- claimant1@example.com
    ('00000000-0000-0000-0003-000000000001', 'POL-MY-MTR-0001', 'Demo Claimant 1', 'claimant1@example.com', 'MOTOR',    'MY', 'MYR', 'Vehicle WXY 1234 (fake plate)',                     85000.00,      '2026-03-01', '2027-02-28', 'ACTIVE'),
    ('00000000-0000-0000-0003-000000000002', 'POL-SG-PRP-0002', 'Demo Claimant 1', 'claimant1@example.com', 'PROPERTY', 'SG', 'SGD', '10 Example Lane #05-01, Singapore 000010',          650000.00,     '2026-07-01', '2027-06-30', 'ACTIVE'),
    ('00000000-0000-0000-0003-000000000003', 'POL-TH-MTR-0003', 'Demo Claimant 1', 'claimant1@example.com', 'MOTOR',    'TH', 'THB', 'Vehicle 1KT 2345 (fake plate)',                     900000.00,     '2024-05-01', '2025-04-30', 'ACTIVE'),  -- expired
    -- claimant2@example.com
    ('00000000-0000-0000-0003-000000000004', 'POL-ID-MTR-0004', 'Demo Claimant 2', 'claimant2@example.com', 'MOTOR',    'ID', 'IDR', 'Vehicle B 1234 XYZ (fake plate)',                   350000000.00,  '2026-01-15', '2027-01-14', 'ACTIVE'),
    ('00000000-0000-0000-0003-000000000005', 'POL-PH-PRP-0005', 'Demo Claimant 2', 'claimant2@example.com', 'PROPERTY', 'PH', 'PHP', '12 Sample Street, Makati City (fake address)',     8000000.00,    '2026-09-01', '2027-08-31', 'ACTIVE'),
    ('00000000-0000-0000-0003-000000000006', 'POL-VN-PRP-0006', 'Demo Claimant 2', 'claimant2@example.com', 'PROPERTY', 'VN', 'VND', '34 Duong Mau, Quan 1, Ho Chi Minh City (fake)',    3000000000.00, '2023-10-01', '2024-09-30', 'ACTIVE'),  -- expired
    -- no account yet: use these to try registration
    ('00000000-0000-0000-0003-000000000007', 'POL-MY-PRP-0007', 'Demo Holder Three', 'holder3@example.com', 'PROPERTY', 'MY', 'MYR', '7 Jalan Contoh, Petaling Jaya (fake address)',     450000.00,     '2026-04-01', '2027-03-31', 'ACTIVE'),
    ('00000000-0000-0000-0003-000000000008', 'POL-SG-MTR-0008', 'Demo Holder Four',  'holder4@example.com', 'MOTOR',    'SG', 'SGD', 'Vehicle SAB 1234 Z (fake plate)',                   120000.00,     '2026-02-01', '2027-01-31', 'ACTIVE'),
    ('00000000-0000-0000-0003-000000000009', 'POL-TH-PRP-0009', 'Demo Holder Five',  'holder5@example.com', 'PROPERTY', 'TH', 'THB', '99 Soi Tuaya, Bangkok (fake address)',             5000000.00,    '2026-06-01', '2027-05-31', 'ACTIVE'),
    ('00000000-0000-0000-0003-000000000010', 'POL-VN-MTR-0010', 'Demo Holder Six',   'holder6@example.com', 'MOTOR',    'VN', 'VND', 'Vehicle 51A-123.45 (fake plate)',                   800000000.00,  '2026-01-01', '2026-12-31', 'CANCELLED');
