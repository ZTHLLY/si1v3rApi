/** Match backend canonical raw paths; never silently normalize user input. */
export const isValidApiPath = (value: unknown): boolean => {
  if (typeof value !== 'string' || !value.startsWith('/api/')) return false;
  return !/\/\/|\/\.\.?($|\/)|[;\\?#\s\x00-\x1f\x7f<>"{}|^`[\]]|%(2e|2f|5c|00)|%(?![\da-f]{2})/i.test(
    value,
  );
};

export const isValidApiMethod = (value: unknown): boolean =>
  typeof value === 'string' && ['GET', 'POST', 'PUT', 'DELETE'].includes(value.toUpperCase());
