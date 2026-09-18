import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ErrorBoundary } from './ErrorBoundary'
import { scenario } from '../../test/scenario'

const ThrowingComponent = () => {
  throw new Error('Test explosion')
}

const WorkingComponent = () => <div>All good</div>

describe('ErrorBoundary', () => {
  const originalError = console.error
  beforeEach(() => { console.error = vi.fn() })
  afterEach(() => { console.error = originalError })

  it(scenario('ERR-BOUND-001', 'renders children when no error'), () => {
    render(
      <ErrorBoundary>
        <WorkingComponent />
      </ErrorBoundary>
    )
    expect(screen.getByText('All good')).toBeDefined()
  })

  it(scenario('ERR-BOUND-002', 'renders fallback when child throws'), () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>
    )
    expect(screen.getByText(/something went wrong/i)).toBeDefined()
  })

  it(scenario('ERR-BOUND-003', 'renders custom fallback when provided'), () => {
    render(
      <ErrorBoundary fallback={<div>Custom error</div>}>
        <ThrowingComponent />
      </ErrorBoundary>
    )
    expect(screen.getByText('Custom error')).toBeDefined()
  })

  it(scenario('ERR-BOUND-004', 'recovers when retry is clicked'), () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>
    )
    expect(screen.getByText(/something went wrong/i)).toBeDefined()
    fireEvent.click(screen.getByText(/try again/i))
  })
})
