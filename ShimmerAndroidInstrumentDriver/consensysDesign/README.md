# consensysDesign

A resources-only Android library that implements the **Consensys** design system
(Shimmer's desktop instrument UI, ported to Android): white surfaces, thin grey
hairline borders, grey UPPERCASE labels, square 2dp corners, Shimmer-orange
accents, and the Carlito font.

The module ships **no Kotlin/Java code** — only `res/` (colors, dimens, styles,
drawables, fonts). It builds on **AppCompat 1.7.0** only (no Material Components),
`compileSdk 34`, `minSdk 21`.

## Opt an example app in

1. Add the dependency in the app module's `build.gradle`:

   ```groovy
   dependencies {
       implementation project(':consensysDesign')
   }
   ```

   (`appcompat:1.7.0` is already injected into every subproject by the root
   `build.gradle`, so nothing else is required.)

2. Apply the theme in `AndroidManifest.xml` — on the `<application>` or per
   `<activity>`:

   ```xml
   <application android:theme="@style/Theme.Consensys">
   ```

   Use `@style/Theme.Consensys.NoActionBar` for activities that supply their own
   toolbar.

Applying the theme automatically restyles **all** standard widgets (buttons,
EditTexts, checkboxes, radios, seek bars, spinners, the action bar, and
AlertDialogs) plus sets Carlito as the global font — usually no per-widget
changes are needed.

## Theme

| Style | Parent | Use |
|---|---|---|
| `Theme.Consensys` | `Theme.AppCompat.Light.DarkActionBar` | app/activity theme, keeps a restyled white action bar |
| `Theme.Consensys.NoActionBar` | `Theme.Consensys` | same, no action bar |
| `Theme.Consensys.Dialog` | `Theme.AppCompat.Light.Dialog.Alert` | wired as `alertDialogTheme`; also usable directly for custom dialogs |

The theme wires: `colorAccent`/`colorControlActivated` = orange `#F15D22`,
`colorControlNormal` = grey `#808080`, `colorControlHighlight` = orange @ 10%,
`windowBackground` = white, `textColorPrimary` = `#484848`,
`textColorSecondary` = `#808080`, `textColorHint` = `#8A8A8A`,
`statusBarColor` = `#BB5913`, global `fontFamily` = `@font/carlito`, and the
default `buttonStyle` / `editTextStyle` / `checkboxStyle` / `radioButtonStyle` /
`seekBarStyle` / `spinnerStyle` / `actionBarStyle` / `alertDialogTheme`.

## Widget styles (apply per-view with `style="@style/…"`)

Buttons default to the ghost-less bordered look; opt into variants:

| Style | Purpose |
|---|---|
| `Widget.Consensys.Button` | default (this is the theme default too) — white, 1dp grey border, grey uppercase text |
| `Widget.Consensys.Button.Accent` | solid orange, white text |
| `Widget.Consensys.Button.Ghost` | transparent, borderless, grey text |
| `Widget.Consensys.EditText` | boxed input, orange focus stroke |
| `Widget.Consensys.Checkbox` | 15dp box, orange + white check when checked |
| `Widget.Consensys.Radio` | 15dp circle, orange ring + dot when checked |
| `Widget.Consensys.Spinner` | boxed "select" with grey chevron (chevron on API 23+) |
| `Widget.Consensys.SeekBar` | 4dp orange/grey track, white thumb w/ orange ring |
| `Widget.Consensys.ProgressBar` | horizontal, green fill in a boxed container |
| `Widget.Consensys.ProgressBar.Accent` | horizontal, orange fill |
| `Widget.Consensys.ProgressBar.Circular` | indeterminate spinner tinted orange |
| `Widget.Consensys.FieldLabel` | 12sp bold UPPERCASE grey label |
| `Widget.Consensys.SectionHeading` | 13sp bold UPPERCASE grey heading |
| `Widget.Consensys.PanelTitle` | 13sp bold UPPERCASE `#484848` panel title |
| `Widget.Consensys.Panel` / `.Panel.Padded` | white panel with 1dp `#D3D3D3` border (padded adds 16dp) |
| `Widget.Consensys.StatusBadge` | chip base; tone variants: `.Success` `.Error` `.Info` `.Neutral` `.Accent` `.Pending` |
| `Widget.Consensys.TableHeaderCell` | grey header cell, 12sp bold UPPERCASE |
| `Widget.Consensys.TableCell` | 13sp `#484848` body cell |
| `Widget.Consensys.Toolbar` | `androidx.appcompat.widget.Toolbar` — white bg, hairline, styled title |

Example:

```xml
<Button
    style="@style/Widget.Consensys.Button.Accent"
    android:text="Connect" />

<TextView
    style="@style/Widget.Consensys.StatusBadge.Success"
    android:text="Connected" />

<LinearLayout
    style="@style/Widget.Consensys.Panel.Padded"
    android:orientation="vertical" />
```

For a `ListView`/`RecyclerView` data table, use `@drawable/cs_list_selector`
(pressed `#F5F5F5`, activated/selected `#FDEFE7`), `@drawable/cs_table_row_bg`
(row + 1dp `#EAEAEA` bottom divider), `@drawable/cs_table_header_bg`, and set the
`ListView` divider to `@color/cs_grey_100` at `1dp`.

## Notes / constraints

- AppCompat-only parents; no Material Components dependency.
- `minSdk 21`-safe. The spinner chevron overlay uses layer-list gravity/size
  (API 23+) so it lives in `res/drawable-v23/cs_spinner_bg.xml`; on API 21–22 the
  spinner falls back to a plain box (right padding still reserved).
- Disabled state = 45% alpha (baked into the button/input drawables; for
  checkboxes/radios set `android:alpha="0.45"` on the disabled view if desired).
- All tokens are exposed as `@color/cs_*` and `@dimen/cs_*` for direct reuse.
