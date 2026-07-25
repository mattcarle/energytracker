SELECT * FROM AGREEMENT a
JOIN STANDING_CHARGE s on a.id = s.agreement_id
JOIN UNIT_RATE u on a.id = u.agreement_id
where a.valid_from < now() and (a.valid_to is null or a.valid_to > now())