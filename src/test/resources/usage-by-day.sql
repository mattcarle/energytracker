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