#!/usr/bin/env node
/**
 * Print a plantscanner:// setup magic link.
 * Usage: node scripts/magic-link.js <prefix> <execUrl> <accessCode>
 */
const [prefix, url, code] = process.argv.slice(2);
if (!prefix || !url || !code || !/^\d{2}$/.test(prefix)) {
  console.error('Usage: node scripts/magic-link.js <two-digit-prefix> <execUrl> <accessCode>');
  process.exit(1);
}
const link =
  `plantscanner://setup?prefix=${prefix}` +
  `&url=${encodeURIComponent(url)}` +
  `&code=${encodeURIComponent(code)}`;
console.log(link);
