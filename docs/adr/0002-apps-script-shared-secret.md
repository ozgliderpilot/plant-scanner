# Apps Script + shared secret, not Sheets API

The app talks to a Google Apps Script web app over plain HTTPS/JSON, authenticated with a shared secret in Script Properties. Official Sheets API + OAuth (Cloud project, consent screens, token refresh) is overkill for two trusted sideloaded devices; Sheet formatting and append logic stay in the script.
