# Appearance references — 2026-10-28

The app now has **20 board palettes and 18 Chinese-character piece finishes**.
The first six preference IDs and their surface colors are preserved. Additional
presets are original Canvas drawings informed by the following public primary
references; the app does not bundle their screenshots, logos, fonts or piece
image files. Colors below are our chosen interpretations, with stronger grid or
glyph contrast where needed for compact Android displays.

## Xiangqi.com

- [Official Chinese board setup guide](https://www.xiangqi.com/help/board-and-set-up)
  and its [board image](https://www.xiangqi.com/media/en/pages/help__board-and-set-up/xiangqi-boards-starting-positions.w872.webp):
  the Chinese-character board on the left has a pale ivory/paper surface, fine
  gray grid, solid brick-red/black discs, and white traditional Chinese glyphs.
  The international-symbol board on the right is excluded.
- [Official Android listing](https://play.google.com/store/apps/details?id=com.xiangqimobile):
  inspected the actual in-game screenshot, which uses warm pale maple, brown
  lines, a slim orange frame, and red/black discs with light glyphs and rims.
- [Graphics and assets page](https://www.xiangqi.com/graphics) provides its piece
  ZIP with credit conditions. We inspected the reference appearance and drew our
  own Chinese-text circles, rims and restrained bevels; no ZIP assets are shipped.

The resulting board choices are **Xiangqi.com · Paper** (`#EBE8DF`, grid
`#837F75`) and **Xiangqi.com · Maple** (`#F7DBA9`, grid `#996D3E`). The Classic
piece choice uses `#AC2825` Red and `#111111` Black with white glyphs; Soft lacquer
offers a gentler red/charcoal pair. These are visual adaptations, not an official
Xiangqi.com product or exact copies of its image files.

## Android Xiangqi apps

- [Xiangqi One, official Google Play listing](https://play.google.com/store/apps/details?id=one.xiangqi):
  inspected the in-game marketing screenshot showing an ivory board with thin
  gray grid and a slim coral frame; coral/charcoal discs have light outlined rims.
  Our **Xiangqi One · Ivory** board and **Xiangqi One · Coral** discs use this
  color relationship. Coral glyphs are darkened to maintain at least 4.5:1 text
  contrast instead of reproducing low-contrast white lettering.
- [Xiangqi Classic by Coba, official Google Play listing](https://play.google.com/store/apps/details?id=com.coba.xiangqiclassic.android):
  inspected its golden wood board, warm fine grid and dark brown surrounding
  frame. This informed **Classic golden wood** and **Golden ivory**, rather than
  copying the ornamental image frame.
- [ChineseChess by mobirix, official Google Play listing](https://play.google.com/store/apps/details?id=com.mobirix.Xiangqi):
  inspected honey-gold vertical woodgrain, chestnut grid and cream/gold discs
  with dark green/Red lettering. Our woodgrain and carved-disc variations retain
  these familiar color relationships with a restrained procedural finish.
- [Chinese Chess – Co Tuong by vndynapp, official Google Play listing](https://play.google.com/store/apps/details?id=com.vndynapp.cotuong):
  inspected a muted beige/gray wooden board and pale golden discs with green
  Black lettering. This informed the muted wood and golden-disc alternatives.

## Additional palettes and constraints

Walnut, dark mahogany, sand parchment, blue mist, jade stone, forest, midnight,
navy, lavender and terracotta expand the warm/light/dark choices. Celadon,
blue-and-white porcelain, copper, silver, crimson-and-ink and night-jade discs
are original complementary variants. All are Chinese-character pieces in both
traditional and simplified modes. No international symbols are offered.

Grain, paper and stone effects are low-opacity deterministic Canvas strokes;
frames and rims are drawn in code. Custom board RGB suppresses material tint so
the picked color remains exact. Disc backgrounds are independent of the board,
and glyph ink adapts to a minimum 4.5:1 contrast. Preset grid colors maintain at
least 3:1 contrast against their board fill. Coordinates, hit targets, board size,
last-move arrows, best-move arrows and the side-to-move dot retain their geometry.

Source screenshots used for inspection remain ignored under `.cache` and
`test-results/appearance-research`, separate from shipped app assets.
