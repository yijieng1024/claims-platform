/**
 * Hands a downloaded file to the browser under its original name. Files are fetched through HttpClient
 * (so the interceptor adds the token; a plain <a href> would not send it), then saved from memory.
 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
