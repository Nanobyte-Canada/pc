import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { scenario } from '../../test/scenario';
import { Button } from './button';

describe('Button', () => {
  it(scenario('UI-BUTTON-001', 'renders with text'), () => {
    render(<Button>Click me</Button>);
    expect(screen.getByRole('button', { name: /click me/i })).toBeInTheDocument();
  });
});
