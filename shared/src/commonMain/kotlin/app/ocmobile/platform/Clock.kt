package app.ocmobile.platform

/**
 * Milliseconds since the Unix epoch. `System.currentTimeMillis()` was the only
 * thing the API client needed from Java time, so it is the whole contract.
 */
expect fun nowMillis(): Long
