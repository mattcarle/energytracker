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