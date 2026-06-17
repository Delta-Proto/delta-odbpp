# SVG Export Testing Plan

## Overview

This plan describes how to test the ODB++ import and SVG export functionality using the three reference SVG files:
- `testdata/minimal-odb.svg` (9.6KB) - Simple, well-documented test case
- `testdata/BoardA-odb++.svg` (12MB) - Complex production design
- `testdata/BoardB.svg` (8.5MB) - Complex production design

The reference SVG files are considered correct. Colors don't need to match exactly.

## Phase 1: Element-Level Unit Tests (minimal-odb)

The `minimal-odb` is designed for testing with known coordinates:

### Features in minimal-odb:
```
# Symbols: $0=r1 (radius 0.5mm), $1=r2 (radius 1mm), $2=r5 (radius 2.5mm)

# 3 main pads with different sizes:
P 10 10 0  -> Pad at (10mm, 10mm), symbol r1
P 20 10 1  -> Pad at (20mm, 10mm), symbol r2
P 30 10 2  -> Pad at (30mm, 10mm), symbol r5

# 3 lines:
L 10 20 30 20 0  -> Line from (10,20) to (30,20), width r1
L 10 30 30 30 1  -> Line from (10,30) to (30,30), width r2
L 10 40 30 50 0  -> Diagonal from (10,40) to (30,50)

# 1 arc:
A 50 40 60 50 50 50 0  -> Arc from (50,40) to (60,50), center (50,50)

# 10 grid pads at (50-90, 10) and (50-90, 20)
```

### Unit Tests to Create:
1. `testMinimalOdbPadCoordinates()` - Verify all 13 pad centers match expected mm coordinates
2. `testMinimalOdbPadSizes()` - Verify pad radii match symbol definitions
3. `testMinimalOdbLineEndpoints()` - Verify line start/end coordinates
4. `testMinimalOdbArcGeometry()` - Verify arc center and endpoints

## Phase 2: SVG Element Comparison Framework

Create a utility class to parse reference SVG and extract elements:

### SvgElementExtractor
- Extract circles with cx, cy, r attributes
- Extract lines with x1, y1, x2, y2 attributes
- Extract paths and decode path data
- Group by layer ID

### SvgComparisonUtils
- `assertCircleAt(svg, x, y, tolerance)` - Find circle near coordinate
- `assertCircleWithRadius(svg, x, y, r, tolerance)` - Match position and radius
- `assertLineFrom(svg, x1, y1, x2, y2, tolerance)` - Match line endpoints
- `assertPathContains(svg, command, params)` - Check path has command
- `countElements(svg, type)` - Count circles, lines, paths
- `extractLayerContent(svg, layerId)` - Get elements from specific layer

## Phase 3: Layer-Level Regression Tests

For each test file, validate layer structure:

### BoardA Tests:
1. Count circles per layer matches reference
2. Count paths per layer matches reference
3. Layer names present in output
4. Board outline (profile) rendered correctly

### BoardB Tests:
Similar structure to BoardA tests.

## Phase 4: Full Output Comparison

Compare complete generated output against reference:

1. Element count comparison (tolerance for floating point)
2. Bounding box comparison
3. Layer structure validation
4. Profile/outline validation

## Implementation Order

1. **Create SvgElementExtractor utility** (30 min)
   - Parse SVG using standard Java XML parser
   - Extract circle, line, path elements with attributes

2. **Create SvgComparisonUtils** (30 min)
   - Tolerance-based coordinate matching
   - Element counting and grouping

3. **Write minimal-odb element tests** (45 min)
   - Test each known element against expected coordinates
   - Use 0.001mm tolerance for coordinate matching

4. **Write layer count regression tests** (30 min)
   - Extract element counts from reference SVGs
   - Assert generated output matches within tolerance

5. **Write full output validation tests** (30 min)
   - Compare bounding boxes
   - Compare total element counts
   - Validate SVG structure

## File Structure

```
src/test/java/com/deltaproto/deltaodbpp/export/
├── SvgExportIntegrationTest.java        (existing)
├── SvgElementComparisonTest.java        (NEW - element-level tests)
├── SvgRegressionTest.java               (NEW - full output comparison)
└── util/
    ├── SvgElementExtractor.java         (NEW - parse reference SVG)
    └── SvgComparisonUtils.java          (NEW - assertion helpers)
```

## Reference SVG Analysis

### minimal-odb.svg structure:
- Profile layer with board outline
- Top layer with circles (pads) and paths (lines, arcs)
- Coordinates in inches (converted from mm)

### Key conversion: 1 inch = 25.4 mm
- 10mm = 0.393701 inch
- 20mm = 0.787402 inch
- 30mm = 1.18110 inch

## Success Criteria

1. All minimal-odb elements render at correct coordinates (within tolerance)
2. Element counts match reference SVGs (circles, lines, paths)
3. Layer structure matches (layer IDs, grouping)
4. No locale issues (dots not commas for decimals)
5. Valid SVG output (proper XML, namespace, closing tags)
