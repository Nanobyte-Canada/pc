import { Page, expect } from '@playwright/test';

export class AppShell {
  constructor(private page: Page) {}

  async expectSidebarVisible() {
    await expect(this.page.locator('nav, [role="navigation"]')).toBeVisible();
  }

  async navigateTo(route: string) {
    await this.page.click(`a[href="${route}"]`);
    await this.page.waitForURL(route);
  }
}
