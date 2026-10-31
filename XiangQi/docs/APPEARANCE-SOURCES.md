# Appearance references — 2026-10-28

The app now has **24 board palettes and 22 Chinese-character piece finishes**.
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

## Four user-provided visual references (0.5)

The user supplied four board screenshots for direct visual comparison. The four
new paired presets use matching board and piece names so they can be combined
without guessing which finish belongs with which surface. All previous 20 board
and 18 piece IDs remain in their original order.

| Reference | Paired preset | Observed details used for the original drawing |
| --- | --- | --- |
| Light wood with white-lettered black/Red pieces | Warm maple (`warm-maple`) | Pale cream vertical grain, a rounded beige outer frame with amber inner rails, white Chinese glyphs and edge rings, restrained bevel and drop shadow |
| Purple board with luminous pieces | Neon violet (`neon-violet`) | Deep violet surface with a magenta center glow, pale cyan and pink disc faces, dark blue/Red Chinese glyphs, distinct colored halos outside the discs |
| Gold paper with decorative branches | Antique gold (`antique-gold`) | Gold parchment patina, original corner/edge branch motifs, black and deep-maroon discs, gold Chinese glyphs and fine concentric rims |
| Honey-colored carved wooden set | Carved honey (`carved-honey`) | Warm honey board, dark brown surrounding frame, raised honey/ivory discs with visible bevel and lower-right shadow, dark-brown/Red Chinese glyphs |

These additions are procedural Canvas drawings. The supplied screenshots are
inspection references and are not redistributed in the app or repository. No
watermarked reference bitmap is edited or shipped. The app's own board rendering
contains the river labels **楚河 / 漢界**, with no URL or website text in the river.
Source links and credits remain in documentation rather than being drawn across
the playing surface. Colors and contrast are adjusted for a compact phone board,
so these are functional visual adaptations rather than pixel-identical images.


## Distinct sides and retired finishes (0.7.9)

The user's ten retired piece options are Xiangqi One Coral, Golden ivory, Carved
walnut, Copper, Brushed silver, Night jade, Cream, Wood, Jade and Porcelain. These
are removed from the piece selector; all board presets remain. Saved retired
piece IDs fall back to the existing red/black Classic finish. The current catalog
has 16 piece finishes.

New original Canvas palettes prioritize side identification by face color and
opaque contrasting Chinese lettering. They are adaptations of color/material
ideas, not copies of commercial art or bitmap assets:

| Palette | Red face | Black-side face | Character ink |
|---|---|---|---|
| Ruby and snow | ruby red | near white | white / charcoal |
| Scarlet and cobalt | scarlet | cobalt blue | white / white |
| Sunset and navy | orange-red | deep navy | dark brown / white |
| Garnet and mint | dark garnet | pale mint | white / dark green |

Web references inspected:

- [Yellow Mountain Imports acrylic Xiangqi set](https://www.ymimports.com/products/us-cc005-a)
  uses strongly separated red/white and black/white pieces. It informed solid
  opaque faces, clear rims and the red/light-face alternatives.
- [Unicode-hosted Xiangqi design proposal](https://www.unicode.org/wg2/docs/n4748-xiangqi.pdf)
  documents red/blue lettering and light/dark print representations. It informed
  the Scarlet/cobalt and dark/light alternatives. Only Chinese text is used in
  the app; the proposal's optional pictorial symbols are not offered.
- [Xiangqi.com](https://www.xiangqi.com/) retains the familiar red/black convention
  used by Classic and the revised Obsidian finish.

Obsidian's formerly muted pink lettering on gray is replaced with a saturated
crimson face and white lettering. Its Red glyph contrast exceeds7:1; each new
palette's glyphs exceed4.5:1 before the existing adaptive contrast correction.
The new side-face RGB distance also exceeds180 on a0–255 RGB scale. This check
supports the design review; it is not a claim of universal color-blind accessibility.
