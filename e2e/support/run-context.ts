export function getRunId(): string {
  return process.env.GITHUB_RUN_ID || `local-${Date.now()}`;
}

export function getRunNamespace(): string {
  return `pw-${getRunId()}`;
}

export function namespacedEmail(prefix: string): string {
  return `${getRunNamespace()}-${prefix}@portfolio.test`;
}

export function namespacedBranch(): string {
  return `PW ${getRunId()}`;
}
