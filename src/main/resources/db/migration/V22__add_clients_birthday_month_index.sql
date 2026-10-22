-- Clients: their birthdays are looked up by month, whatever the year, so the month is indexed. The
-- expression matches the one the birthday query filters on.
CREATE INDEX ix_clients_birthday_month ON clients ((EXTRACT(MONTH FROM birthday)));
