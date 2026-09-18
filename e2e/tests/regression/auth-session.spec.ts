import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';

test.describe('Authentication - Session', () => {
  test.skip(scenario('AUTH-SESSION-001', 'session persists across page navigation'), 'TODO: implement session persistence test — requires authenticated session fixture');
  test.skip(scenario('AUTH-SESSION-002', 'logout clears session'), 'TODO: implement logout test — requires authenticated session fixture');
});
