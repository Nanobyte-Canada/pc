import { Page, Request, Response } from '@playwright/test';

export interface NetworkEntry {
  url: string;
  method: string;
  status: number;
  receivedAt: number;
}

export class NetworkMonitor {
  private entries: NetworkEntry[] = [];

  constructor(private page: Page) {
    this.page.on('response', async (response: Response) => {
      const request = response.request();
      this.entries.push({
        url: request.url(),
        method: request.method(),
        status: response.status(),
        receivedAt: Date.now(),
      });
    });
  }

  getEntries(): NetworkEntry[] {
    return [...this.entries];
  }

  getFailedRequests(): NetworkEntry[] {
    return this.entries.filter((e) => e.status >= 400);
  }

  clear(): void {
    this.entries = [];
  }
}
