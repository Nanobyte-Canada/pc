const ALLOWED_HOSTS = ['uatportfolio.nanobyte.ca'];

export function assertEnvironment(baseURL: string): void {
  const url = new URL(baseURL);
  if (!ALLOWED_HOSTS.includes(url.hostname)) {
    throw new Error(
      `Environment safety violation: ${url.hostname} is not in the allowed hostlist. ` +
      `Allowed: ${ALLOWED_HOSTS.join(', ')}`
    );
  }
}

export async function assertPageMarker(page: { meta: (name: string) => Promise<string | null> }): Promise<void> {
  const marker = await page.meta('app-environment');
  if (marker !== 'uat') {
    throw new Error(
      `Environment safety violation: app-environment meta tag is "${marker}", expected "uat". ` +
      `This check prevents accidental testing against production.`
    );
  }
}
