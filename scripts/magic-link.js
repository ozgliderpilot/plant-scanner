#!/usr/bin/env node
/**
 * Print setup links for a device prefix.
 * Usage: node scripts/magic-link.js <prefix> <execUrl> <accessCode>
 *
 * Prints:
 *   1) https://…/setup.html?…  — send this in Gmail / Messages (needs GitHub Pages)
 *   2) plantscanner://setup?… — QR codes, adb, or in-app testing
 */
const [prefix, url, code] = process.argv.slice(2);
if (!prefix || !url || !code || !/^\d{2}$/.test(prefix)) {
  console.error('Usage: node scripts/magic-link.js <two-digit-prefix> <execUrl> <accessCode>');
  process.exit(1);
}

const q =
  `prefix=${encodeURIComponent(prefix)}` +
  `&url=${encodeURIComponent(url)}` +
  `&code=${encodeURIComponent(code)}`;

const httpsBase =
  process.env.SETUP_PAGE_BASE ||
  'https://ozgliderpilot.github.io/plant-scanner/setup.html';

const httpsLink = `${httpsBase.replace(/\/?$/, '')}?${q}`;
const appLink = `plantscanner://setup?${q}`;

console.log(httpsLink);
console.log(appLink);
