interface Environment {
  apiUrl: string
  environment: string
}

export const environment: Environment = {
  apiUrl: import.meta.env.VITE_API_URL || '',
  environment: import.meta.env.MODE || 'development',
}
