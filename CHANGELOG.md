# Changelog

Each version's notes become its GitHub release notes and the website's changelog page.

## 1.3.2

- Fixed: on some Android 9 TVs, **Agree and continue** showed "No application can handle this action" instead of opening the accessibility settings, so Aangan couldn't be set to open with Home from the TV. It now opens the TV's own accessibility page, or tells you where to find it.
- Fixed: on the same TVs, **Screensaver** in Settings opened the TV's main Settings instead of the screensaver page.

## 1.3.1 · 25 October 2026

- If Aangan ever crashes, it now keeps a short note about what went wrong, on the TV. Settings says so, and the phone page shows the note with a link to report it on GitHub, which you read and send yourself, or delete. Aangan still sends no crash reports: nothing leaves the TV unless you send it.

## 1.3 · 25 October 2026

- **The dock.** Four apps you choose, larger than the rest, across the top of the home screen. It starts with the apps from your old top row, and only you change it: it never rearranges itself, and an uninstalled app leaves an empty place rather than shuffling the others. Hold OK on any app to put it in the dock, or set it in Settings or from your phone.
- **Easier city search.** A screen of its own, with large cities to choose straight away and places appearing as you type. Results show the state and size to tell same-named places apart, leave out airports and neighbourhoods, and older names such as Bombay, Bangalore and Gurgaon find the right city.
- **Suggestions as you type** for the city on the phone page.
- **Security fix for the phone page.** While Settings was open, any device on your Wi-Fi could learn the page's secret address from the "link expired" page, and then change settings or read the family's days. That page now gives nothing away. The page also now answers only on your home network's address, only to requests addressed to it, and only within strict size and time limits, and it tells the browser to load nothing but the TV's own files.
- Settings text that was too faint to read comfortably from across the room is brighter.
- Built with the newest Android tools, and ready for Android 16's Back button.

## 1.2 · 25 October 2026

- Before you switch on **Open with Home**, Aangan now explains exactly what the Home-button service does and asks you to agree.
- Marked as a TV app, so Google Play offers it to TVs only.
- Fixed: a birthday on a day that doesn't exist, such as 31 February, could stop the home screen opening. The TV now corrects the date, and the phone page refuses it.
- Fixed: a malformed request on the home network could close Aangan while Settings was open.
- Fixed: a festival moved into the past disappeared from Settings, so it couldn't be put back.
- Fixed: editing a family day from a phone page opened before someone changed the list on the TV could change the wrong day. The page now says when that happens.
- Fixed: changing the city while the weather was loading could leave the weather blank for a few minutes.
- The phone page's colours now match the website.

## 1.1 · 25 October 2026

- **A new icon.** Eight houses around a sunlit courtyard, which is also a grid of apps with one in focus.
- **A redesigned phone page.** Everything it needs, even its font, comes from the TV; nothing loads from the internet.
- **Edit the family's days** on the TV or the phone, not just add and remove them.
- **Change festival dates.** Move a festival your family keeps on another day, hide one you don't celebrate, or put it back. Changes are stored on the TV.
- A note on the TV explaining the browser's "not secure" warning for the phone page.

## 1.0 · October 2026

- The first release: clock, weather and air quality for your city, the Today card, festival countdowns and scenes, birthdays and anniversaries, phone setup by QR code, the Home-button service, the screensaver, and the welcome.
