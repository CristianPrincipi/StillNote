# Third-party assets

Every asset bundled into the app, where it came from, and what its licence requires. Add a row here
before committing a new font, icon or image.

## Fonts

All four families are bundled as `res/font` resources rather than downloaded at runtime, since
`api.notion.com` is the only host the app contacts. All four are on Google Fonts.

| Asset | Family | Used for | Licence | Source |
| --- | --- | --- | --- | --- |
| `res/font/kalam_regular.ttf`, `kalam_bold.ttf` | Kalam 2.001 | *Handwritten*, the default for your words | SIL OFL 1.1 | [itfoundry/kalam](https://github.com/itfoundry/kalam) |
| `res/font/courier_prime_regular.ttf`, `courier_prime_bold.ttf` | Courier Prime 3.018 | *Typewriter* | SIL OFL 1.1 | [quoteunquoteapps/CourierPrime](https://github.com/quoteunquoteapps/CourierPrime) |
| `res/font/newsreader_variable.ttf` | Newsreader 1.003, variable | *Bookish* | SIL OFL 1.1 | [productiontype/Newsreader](https://github.com/productiontype/Newsreader) |
| `res/font/dm_mono_regular.ttf`, `dm_mono_medium.ttf` | DM Mono 1.000 | Dates, counts and labels | SIL OFL 1.1 | [googlefonts/dm-mono](https://github.com/googlefonts/dm-mono) |

Copyright holders, as recorded in each font's own `name` table:

- Kalam: Copyright (c) 2014, Indian Type Foundry
- Courier Prime: Copyright 2015 The Courier Prime Project Authors
- Newsreader: Copyright 2020 The Newsreader Project Authors
- DM Mono: Copyright 2020 The DM Mono Project Authors

### What the OFL requires

1. **The licence and copyright notice travel with the font.** Each licence ships inside the APK, at
   `assets/licenses/OFL-Kalam.txt`, `OFL-CourierPrime.txt`, `OFL-Newsreader.txt` and
   `OFL-DMMono.txt`. They live in `assets/` rather than `res/raw/` because release builds shrink
   resources, and a `res/raw` file no code references would be removed. Git keeps their bytes as
   they are (see [`.gitattributes`](../.gitattributes)).
2. **The fonts are not sold on their own.** They are not; they are part of the app.
3. **Reserved Font Names may not be reused by a modified version.** The fonts ship unmodified.

## Icons

The line icons are original to StillNote's design: their path data comes from the mockups in
[`design/screens/source/`](../design/screens/source/) and is drawn as `ImageVector`s in
`ui/components/Icons.kt`. The launcher icon (a placeholder, the "+" pad of post-its) and the
notification icon are drawn for the app as vector drawables.

## Images

`res/drawable-nodpi/widget_preview.png`, the widget picker's preview, is drawn by the app itself
from its sample tasks.

The screenshots in `docs/images/` are of the app. The "Made for Notion" badges in `docs/images/` are
Notion assets, used under Notion's brand guidelines, and are not covered by the app's licence.
