import type { DataIntegrityReport } from '../api/types'
import './DataIntegrityReportView.css'

function formatDateTime(value: string | null): string {
  if (!value) return 'ongoing'
  return new Date(value).toLocaleString()
}

function meterPointLabel(meterType: string, isExport: boolean): string {
  if (meterType === 'GAS') return 'Gas'
  return isExport ? 'Electricity (Export)' : 'Electricity (Import)'
}

interface DataIntegrityReportViewProps {
  report: DataIntegrityReport
}

export default function DataIntegrityReportView({ report }: DataIntegrityReportViewProps) {
  if (report.mpans.length === 0) return <p>No meter points found.</p>

  return (
    <>
      {report.mpans.map((mpanReport) => (
        <div className="integrity-report__mpan" key={mpanReport.mpan}>
          <h3>
            MPAN {mpanReport.mpan} ({meterPointLabel(mpanReport.meterType, mpanReport.isExport)})
          </h3>
          {(
            [
              ['Agreements', mpanReport.agreements],
              ['Standing charges', mpanReport.standingCharges],
              ['Unit rates', mpanReport.unitRates],
              ['Usage', mpanReport.usage],
            ] as const
          ).map(([label, result]) => (
            <div className="integrity-report__category" key={label}>
              <p className="integrity-report__category-title">{label}</p>
              <p>
                {result.earliest
                  ? `${formatDateTime(result.earliest)} – ${formatDateTime(result.latest)}`
                  : 'No data'}
              </p>
              {result.earliest && result.gaps.length === 0 && (
                <p className="integrity-report__ok">No gaps found.</p>
              )}
              {result.gaps.length > 0 && (
                <ul className="integrity-report__gaps">
                  {result.gaps.map((gap, index) => (
                    <li key={index}>
                      Gap: {formatDateTime(gap.from)} &rarr; {formatDateTime(gap.to)}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
        </div>
      ))}
    </>
  )
}
