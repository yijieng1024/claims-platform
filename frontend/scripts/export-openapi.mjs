// Fetches each running service's OpenAPI spec into openapi/*.json (committed, so a contract change shows as a diff).
// Pretty-printed with sorted output order kept as the service returns it.
import { writeFileSync } from 'node:fs';

const services = { auth: 8081, claims: 8082, reporting: 8083 };

for (const [name, port] of Object.entries(services)) {
  const res = await fetch(`http://localhost:${port}/v3/api-docs`);
  if (!res.ok) throw new Error(`${name}: HTTP ${res.status}`);
  const spec = await res.json();
  // the server URL is whatever host Swagger saw; drop it so the file doesn't change per machine
  delete spec.servers;
  writeFileSync(`openapi/${name}.json`, JSON.stringify(spec, null, 2) + '\n');
  console.log(`openapi/${name}.json`);
}
