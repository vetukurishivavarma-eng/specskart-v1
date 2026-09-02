import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Store from './Store'

describe('Store page', () => {
  it('is a Coming Soon teaser with no working purchase controls', () => {
    render(<MemoryRouter><Store /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: /coming soon/i })).toBeInTheDocument()
    expect(screen.getByText('Frames')).toBeInTheDocument()
    const buttons = screen.getAllByRole('button', { name: /coming soon/i })
    expect(buttons.length).toBeGreaterThan(0)
    buttons.forEach((b) => expect(b).toBeDisabled())
  })
})
