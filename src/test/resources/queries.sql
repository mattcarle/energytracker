SELECT * FROM AGREEMENT a
JOIN STANDING_CHARGE s on a.id = s.agreement_id
JOIN UNIT_RATE u on a.id = u.agreement_id
where a.valid_from < now() and (a.valid_to is null or a.valid_to > now())


SELECT mp.mpan, mp.meter_type, mp.is_export, a.tariff_code, CASE WHEN dnt.tariff_code is null THEN 'N' ELSE 'Y' END as IS_DAY_NIGHT, a.valid_from, a.valid_to, r.agreement_id, count(*)
FROM AGREEMENT a
         join METER_POINT mp on a.meter_point_id = mp.id
         left join DAY_AND_NIGHT_TARIFF dnt on dnt.tariff_code = a.tariff_code
         left join unit_rate r on a.id = r.agreement_id
group by mp.mpan, mp.meter_type, mp.is_export, a.tariff_code, a.valid_from, a.valid_to, r.agreement_id
order by mp.mpan, a.valid_from, a.valid_to;

select *, datediff('DAY', a.valid_from, a.valid_to) as days
from unit_rate r join agreement a on r.agreement_id = a.id
where agreement_id in (18, 20, 22, 24, 28)
  and datediff('DAY', a.valid_from, a.valid_to) > 0
order by valid_from

select *, datediff('DAY', a.valid_from, a.valid_to) as hours
from unit_rate r join agreement a on r.agreement_id = a.id
where datediff('HOUR', a.valid_from, a.valid_to) > 0
order by valid_from

select *, datediff('DAY', a.valid_from, a.valid_to) as days, datediff(hour, r.valid_from, r.valid_to) as hours
from unit_rate r join agreement a on r.agreement_id = a.id
where datediff(day, a.valid_from, a.valid_to) > 0
order by valid_from