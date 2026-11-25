package app.ocmobile.platform

/**
 * "Now: Tuesday, 24 November 2026, 18:40 (Europe/Warsaw)", the line the agent is
 * told as a hidden part of every message.
 *
 * It is a separate platform value because formatting a date and naming the time
 * zone needs each platform's own formatter: `java.time` on Android and the JVM,
 * `NSDateFormatter` on iOS. All three produce the same readable shape.
 */
expect fun nowStamp(): String