# Contributing to Ipitivi

Thanks for helping! Ipitivi's goal is to stay **simple enough for an older, non-technical user**. Every change should respect that.

## Ground rules
- Keep screens simple: few choices, big targets, plain wording (French UI today).
- Everything must work with a TV remote only (D-pad, OK, long OK, number keys).
- No tracking, ads or analytics.
- Ipitivi ships no channels, no playlists and no links to providers. Please do not add any.

## Development
- JDK 17, Android SDK 34. Plain Java; UI is built in code.
- Logic that does not need Android (parsers, storage) lives in pure Java classes with unit tests: `./gradlew testDebugUnitTest`.
- Add or update a test with every change to parsing or storage.

## Pull requests
Describe what changed and why, and say how you tested it (device or emulator, remote-only navigation). Screenshots are very welcome.

## Feedback from real users
Reports from older or non-technical users are the most valuable contribution: what confused them, what they could not find.
