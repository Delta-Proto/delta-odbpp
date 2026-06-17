# Plan: Match SVG Output to Reference Format

## Current Test Results

| Metric | Reference | Generated | Status |
|--------|-----------|-----------|--------|
| **BoardA** |
| Circles | 18,851 | 22,865 | MISMATCH (+4,014) |
| Paths | 8,077 | 1,216 | MISMATCH (-6,861) |
| Lines | 0 | 7,301 | MISMATCH (ref uses paths) |
| Layers | 13 | 11 | MISMATCH (-2) |
| File Size | 12 MB | 3.3 MB | Different format |

## Key Differences Identified

### 1. Coordinate System
- **Reference**: Uses **inches** (e.g., `cx="0.393701"` for 10mm)
- **Generated**: Uses **mm** (e.g., `cx="10.0000"`)
- **Conversion**: `inches = mm / 25.4`

### 2. SVG Structure

**Reference format:**
```xml
<svg id="svgCanvas" stroke-linecap="round" stroke-linejoin="round" ...>
  <g id="viewport" transform="matrix(...)">
    <g xmlns="..." transform="scale(1,-1)" viewBox="...">
      <defs>
        <!-- Symbol definitions -->
        <g id="D89"><path d="..."/></g>
        <g id="D86"><path d="..."/></g>
      </defs>
      <path class="profile" .../>  <!-- Board outline -->
    </g>
    <g class="layer" id="top" type="Top" fill="..." stroke="...">
      <circle cx="..." cy="..." r="...">
        <title>Pad metadata...</title>
      </circle>
      <use href="#D89" x="..." y="..."/>  <!-- Symbol reference -->
    </g>
  </g>
</svg>
```

**Our current format:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="..." width="..." height="..." viewBox="..." data-layers="true">
  <style>
    .layer { ... }
    .layer-top { fill: ...; stroke: ...; }
  </style>
  <g transform="scale(1,-1) translate(0,...)">
    <g id="layer-profile" class="layer" data-layer-name="profile">
      <path d="..."/>
    </g>
    <g id="layer-top" class="layer layer-top" data-layer-name="top">
      <circle cx="10.0000" cy="10.0000" r="0.0250"/>
      <line x1="..." y1="..." x2="..." y2="..."/>
    </g>
  </g>
</svg>
```

### 3. Element Differences

| Feature | Reference | Generated |
|---------|-----------|-----------|
| Circle coordinates | Inches | mm |
| Circle radii | Tiny (e.g., `9.84e-05`) | mm (e.g., `0.025`) |
| Lines | Rendered as `<path>` | Rendered as `<line>` |
| Symbol shapes | Uses `<defs>` + `<use>` | Inline paths |
| Metadata | `<title>` inside elements | None |
| Layer attributes | `type="Top"`, `fill=`, `stroke=` inline | CSS classes |
| Profile | `class="profile"` | `id="layer-profile"` |

### 4. Missing Layers (BoardA)
Reference has 13 layers, we have 11. Need to investigate which layers are missing:
- Possibly component layers (`comp_+_top`, `comp_+_bot`)
- Surface finish layers
- Additional paste/solder layers

## Implementation Plan

### Phase 1: Unit Conversion (High Priority)
**Goal**: Convert all coordinates from mm to inches

1. Add unit configuration to `SvgRenderOptions`
   - `outputUnit`: MM or INCH
   - Default to INCH for reference compatibility

2. Update `MultiLayerSvgRenderer` to use configurable units
   - Apply conversion factor (1/25.4) when writing coordinates
   - Update viewBox calculation

### Phase 2: Structure Matching (Medium Priority)
**Goal**: Match SVG structure to reference format

1. Remove XML declaration (`<?xml ...?>`)
2. Change root `<svg>` attributes to match reference
3. Add viewport group with matrix transform
4. Change layer group structure
   - Use `class="layer"` instead of `id="layer-xxx"`
   - Add `type` attribute for layer type

### Phase 3: Element Format (Medium Priority)
**Goal**: Match element rendering to reference

1. Render lines as `<path>` instead of `<line>`
   - Convert `<line x1 y1 x2 y2>` to `<path d="M x1 y1 L x2 y2">`

2. Add `<title>` elements with metadata (optional)
   - Pad information
   - Location in both mm and inches

### Phase 4: Symbol Optimization (Low Priority)
**Goal**: Use `<defs>` and `<use>` for repeated symbols

1. Identify unique symbol shapes
2. Create `<defs>` section with symbol definitions
3. Replace inline paths with `<use href="#symbolId" x y>`

This is an optimization - can be deferred.

### Phase 5: Missing Layer Investigation
**Goal**: Ensure all layers are rendered

1. Compare layer names between reference and generated
2. Identify parsing issues for missing layers
3. Add support for any missing layer types

## Recommended Implementation Order

### Step 1: Create Test Infrastructure
- Modify tests to compare with tolerance for unit conversion
- Add helper to convert reference inch coordinates to mm for comparison

### Step 2: Implement Unit Conversion
- This is the core change that will make coordinates match
- Most circle/line positions will then align

### Step 3: Fix Line Rendering
- Change `<line>` to `<path>` elements
- This will match path counts

### Step 4: Investigate Layer Differences
- Debug why 2 layers are missing
- May be parsing or filtering issue

### Step 5: Structure Refinement (Optional)
- Match exact SVG structure if byte-for-byte comparison needed
- Otherwise, element-level comparison is sufficient

## Testing Strategy

For element comparison with unit conversion:
```java
// Reference uses inches, we use mm
// Convert reference to mm for comparison
double refMm = refInch * 25.4;
assertTrue(Math.abs(refMm - genMm) < TOLERANCE);
```

Or add a mode to generate in inches for direct comparison.

## Files to Modify

1. `SvgRenderOptions.java` - Add unit configuration
2. `MultiLayerSvgRenderer.java` - Apply unit conversion
3. `SvgElementExtractor.java` - Add unit-aware comparison
4. `SvgFullIntegrationTest.java` - Update tests for unit conversion
