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

select *
from meter_point mp
         join meter m on mp.id = m.meter_point_id
         join agreement a on mp.id = a.meter_point_id
         join unit_rate_by_half_hour r on a.id = r.agreement_id
         join usage u on mp.mpan = u.mpan and r.valid_from = u.interval_from
where u.interval_from > '2026-07-20'
--and mp.mpan = 2000060589738
order by u.interval_from


select *
from meter_point mp
         join meter m on mp.id = m.meter_point_id
         join agreement a on mp.id = a.meter_point_id
--join unit_rate_by_half_hour r on a.id = r.agreement_id
         join usage u on mp.mpan = u.mpan
where mp.mpan = 2000016292581
--order by r.valid_from desc;
order by u.interval_from;

select mp.mpan, mp.meter_type, mp.is_export, m.serial_number, a.tariff_code, u.interval_from, u.interval_to, u.consumption
from meter_point mp
         join meter m on mp.id = m.meter_point_id
         join agreement a on mp.id = a.meter_point_id
         join unit_rate_by_half_hour r on r.valid_from = u.interval_from
         join usage u on mp.mpan = u.mpan
where mp.mpan = 2000016292581 and m.serial_number = '24J0660499' and a.tariff_code = 'E-1R-IOG-SMB-FIX-12M-26-02-11-H'
--order by r.valid_from desc;
order by u.interval_from;

select mp.mpan, mp.meter_type, mp.is_export, m.serial_number, a.id as agreement_id, r.agreement_id as rate_agreement_id, a.tariff_code,
       u.interval_from, u.interval_to, u.consumption, r.agreement_id, r.rate_type, r.payment_method, r.value_exc_vat
from meter_point mp
         join meter m on mp.id = m.meter_point_id
         join agreement a on mp.id = a.meter_point_id
         join unit_rate_by_half_hour r on r.valid_from = u.interval_from and r.agreement_id = a.id

         join usage u on mp.mpan = u.mpan
--where mp.mpan = 2000016292581
--and m.serial_number = '24J0660499'
where a.tariff_code = 'E-1R-IOG-SMB-FIX-12M-26-02-11-H'
--order by r.valid_from desc;
order by u.interval_from;

select mp.mpan, mp.meter_type, mp.is_export, a.id, a.tariff_code, u.interval_from, u.consumption
from meter_point mp
         join agreement a on mp.id = a.meter_point_id
         join usage u on mp.mpan = u.mpan
where a.tariff_code =  'E-1R-IOG-SMB-FIX-12M-26-02-11-H'
--group by mp.mpan, a.id, a.tariff_code
order by u.interval_from desc;

select mp.mpan, mp.meter_type, mp.is_export, a.id, a.tariff_code, r.valid_from, r.value_inc_vat
from meter_point mp
         join agreement a on mp.id = a.meter_point_id
         join unit_rate_by_half_hour r on r.agreement_id = a.id
where a.tariff_code =  'E-1R-IOG-SMB-FIX-12M-26-02-11-H'
order by r.valid_from desc;

select * from unit_rate_by_half_hour where agreement_id=62;
select * from unit_rate where agreement_id = 62;
select * from day_and_night_tariff;

select a.tariff_code, count(*) from agreement a join unit_rate_by_half_hour r on a.id=r.agreement_id
group by a.tariff_code;


select mp.mpan, mp.meter_type, mp.is_export, a.tariff_code, a.valid_from, a.valid_to, u.interval_from, u.interval_to, u.consumption, r.payment_method, r.rate_type, r.value_inc_vat, (r.value_inc_vat * u.consumption) / 100 as cost
from meter_point mp
         join agreement a on mp.id = a.meter_point_id
         join unit_rate_by_half_hour r on r.valid_from = u.interval_from and r.agreement_id = a.id
         join usage u on mp.mpan = u.mpan
where mp.mpan = 2000016292581 and a.tariff_code = 'E-1R-IOG-SMB-FIX-12M-26-02-11-H'
--order by r.valid_from desc;
order by u.interval_from;

select *
from unit_rate_by_half_hour r
         join agreement a on r.agreement_id = a.id
where a.tariff_code = 'E-1R-IOG-SMB-FIX-12M-26-02-11-H';

-- USAGE BY DAY
select mp.mpan, mp.meter_type, mp.is_export, CAST(z.local_time AS DATE) as usage_date,
       count(*) as interval_count,
       sum(u.consumption) as kwh,
       sum(u.consumption * r.value_inc_vat / 100) as cost,
       sum(u.consumption * r.value_inc_vat / 100) / sum(u.consumption) as avg_rate
from meter_point mp
         join agreement a on mp.id = a.meter_point_id
         join unit_rate_by_half_hour r on r.valid_from = u.interval_from and r.agreement_id = a.id
         join utc_to_local z on u.interval_from = z.utc_time
         join usage u on mp.mpan = u.mpan
where mp.mpan = 2000016292581
  and z.local_time >= '2026-07-01'
  and r.payment_method in ('DIRECT_DEBIT', 'NA')
group by mp.mpan, mp.meter_type, mp.is_export, CAST(z.local_time AS DATE)
order by  CAST(z.local_time AS DATE)
;

select mp.mpan, CAST(DATE_TRUNC('MONTH', u.interval_from) AS DATE) AS usageMonth, r.rate_type, r.value_inc_vat as rate,
       SUM(u.consumption) AS kwh
FROM meter_point mp
         JOIN agreement a ON mp.id = a.meter_point_id
         JOIN usage u ON mp.mpan = u.mpan
         JOIN unit_rate_by_half_hour r ON r.valid_from = u.interval_from AND r.agreement_id = a.id
WHERE mp.mpan = :mpan
  AND u.interval_from >= :interval_from
  AND u.interval_to < :interval_to
GROUP BY mp.mpan, CAST(DATE_TRUNC('MONTH', u.interval_from) AS DATE), r.rate_type, r.value_inc_vat
ORDER BY CAST(DATE_TRUNC('MONTH', u.interval_from) AS DATE);


SELECT mp.mpan AS mpan,
       mp.meter_type AS meterType,
       mp.is_export AS isExport,
       z.local_time,
       COUNT(*) AS intervalCount,
       SUM(u.consumption) AS kwh,
       SUM(u.consumption * r.value_inc_vat / 100) AS cost,
       SUM(u.consumption * r.value_inc_vat / 100) / NULLIF(SUM(u.consumption), 0) AS avgRate
FROM meter_point mp
         JOIN agreement a ON mp.id = a.meter_point_id
         JOIN usage u ON mp.mpan = u.mpan
         JOIN utc_to_local z ON u.interval_from = z.local_time
         JOIN unit_rate_by_half_hour r ON r.valid_from = z.utc_time AND r.agreement_id = a.id
WHERE mp.mpan = '2000016292581'
  AND z.local_time >= '2026-08-05'
  AND z.local_time < '2026-08-06'
GROUP BY mp.mpan, mp.meter_type, mp.is_export, z.local_time
ORDER BY z.local_time

