-- Seed cover periods relative to the day this migration runs (Q13), so the demo data doesn't expire.
-- Each policy covers one year starting `months_ago` months before today. Same mix as V3:
-- in force, expired (0003, 0006) and cancelled (0010, status unchanged).
UPDATE policies p
SET start_date = (current_date - make_interval(months => v.months_ago))::date,
    end_date   = (current_date - make_interval(months => v.months_ago) + interval '1 year' - interval '1 day')::date
FROM (VALUES
    ('POL-MY-MTR-0001',  6),
    ('POL-SG-PRP-0002',  2),
    ('POL-TH-MTR-0003', 30),  -- expired 18 months ago
    ('POL-ID-MTR-0004', 10),
    ('POL-PH-PRP-0005',  1),
    ('POL-VN-PRP-0006', 36),  -- expired 2 years ago
    ('POL-MY-PRP-0007',  8),
    ('POL-SG-MTR-0008',  4),
    ('POL-TH-PRP-0009',  5),
    ('POL-VN-MTR-0010',  3)   -- cancelled
) AS v(policy_number, months_ago)
WHERE p.policy_number = v.policy_number;
