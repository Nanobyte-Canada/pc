import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { EducationContent, StrategyInfo } from '@/types/options'
import './StrategyEducationCard.css'

interface StrategyEducationCardProps {
  strategy: StrategyInfo
  education: EducationContent
}

function formatStrategyName(name: string): string {
  if (!name.includes('_') && name !== name.toUpperCase()) return name
  return name
    .split('_')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ')
}

function outlookBadgeClass(outlook: string): string {
  const lower = outlook.toLowerCase()
  if (lower.includes('bullish')) return 'outlook-badge--bullish'
  if (lower.includes('bearish')) return 'outlook-badge--bearish'
  return 'outlook-badge--neutral'
}

export function StrategyEducationCard({ strategy, education }: StrategyEducationCardProps) {
  return (
    <Card className="strategy-edu-card">
      <CardHeader className="strategy-edu-card__header">
        <div className="strategy-edu-card__header-row">
          <CardTitle className="strategy-edu-card__title">
            {formatStrategyName(strategy.name)}
          </CardTitle>
          <span className={`outlook-badge ${outlookBadgeClass(strategy.marketOutlook)}`}>
            {strategy.marketOutlook.split(' ')[0]}
          </span>
        </div>
        <p className="strategy-edu-card__description">{strategy.description}</p>
      </CardHeader>

      <CardContent className="strategy-edu-card__body">
        <section className="strategy-edu-card__section">
          <h4 className="strategy-edu-card__section-title">When to Use</h4>
          <p className="strategy-edu-card__text">{education.whenToUse}</p>
        </section>

        <section className="strategy-edu-card__section">
          <h4 className="strategy-edu-card__section-title">Risk Explanation</h4>
          <p className="strategy-edu-card__text">{education.riskExplanation}</p>
        </section>

        <section className="strategy-edu-card__section">
          <h4 className="strategy-edu-card__section-title">Key Characteristics</h4>
          <ul className="strategy-edu-card__list">
            {education.keyCharacteristics.map((item, i) => (
              <li key={i} className="strategy-edu-card__list-item">{item}</li>
            ))}
          </ul>
        </section>

        {education.warnings.length > 0 && (
          <section className="strategy-edu-card__section strategy-edu-card__section--warnings">
            <h4 className="strategy-edu-card__section-title">Warnings</h4>
            <div className="strategy-edu-card__warnings">
              {education.warnings.map((warning, i) => (
                <div key={i} className="strategy-edu-card__warning">{warning}</div>
              ))}
            </div>
          </section>
        )}
      </CardContent>
    </Card>
  )
}
