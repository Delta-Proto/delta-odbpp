# SVG Rendering Implementation Plan

## Problem Statement

The current SVG rendering does not produce recognizable PCB visualizations because:
1. **Pads are rendered as fixed-size circles** regardless of their actual symbol definition
2. **Symbol resolution is not implemented** - standard symbols (r, s, rect, oval, etc.) are not parsed
3. **User-defined symbols are not resolved** - complex pad shapes from the symbols directory are ignored
4. **D-Code to symbol mapping is incomplete** - features reference symbol numbers but we don't look up the actual symbol

## Architecture Overview

```
Feature (Pad/Line/Arc/etc.)
    |
    +-- symbolNumber --> SymbolResolver --> Symbol Definition
                                               |
                                               +-- StandardSymbol (parsed from name like "r50", "rect100x50")
                                               |
                                               +-- UserDefinedSymbol (from symbols/ directory, contains Features)

Symbol Definition
    |
    +-- SymbolRenderer --> SVG Path/Shape
```

## Phase 1: Symbol Infrastructure (Foundation)

### 1.1 Create StandardSymbol Parser
**File:** `src/main/java/com/deltaproto/deltaodbpp/model/symbol/StandardSymbol.java`

Parse ODB++ standard symbol names into renderable definitions. Standard symbols follow naming conventions:

| Symbol Type | Name Pattern | Example | Description |
|-------------|--------------|---------|-------------|
| Round | `r<d>` | `r50` | Circle, diameter=50 mils |
| Square | `s<w>` | `s40` | Square, width=40 mils |
| Rectangle | `rect<w>x<h>` | `rect100x50` | Rectangle, 100x50 mils |
| Rounded Rectangle | `rc<w>x<h>x<r>` | `rc100x50x10` | Rectangle with corner radius |
| Chamfered Rectangle | `ch<w>x<h>x<c>` | `ch100x50x10` | Rectangle with chamfered corners |
| Oval | `oval<w>x<h>` | `oval80x40` | Oblong/oval shape |
| Diamond | `di<w>x<h>` | `di60x60` | Diamond (rotated square) |
| Octagon | `oct<w>x<h>x<c>` | `oct100x100x20` | Octagon with corner cut |
| Hexagon Horizontal | `hex_l<w>x<h>x<c>` | `hex_l100x80x20` | Horizontal hexagon |
| Hexagon Vertical | `hex_s<w>x<h>x<c>` | `hex_s80x100x20` | Vertical hexagon |
| Triangle | `tri<b>x<h>` | `tri50x40` | Isoceles triangle |
| Half Oval | `ho<w>x<h>` | `ho60x30` | Half of an oval |
| Round Donut | `donut_r<od>x<id>` | `donut_r100x40` | Ring (outer/inner diameter) |
| Square Donut | `donut_s<ow>x<iw>` | `donut_s100x60` | Square ring |
| Oval Donut | `donut_o<ow>x<oh>x<lw>` | `donut_o100x60x10` | Oval ring |
| Thermal Round | `thr<od>x<id>x<a>x<n>x<g>` | `thr100x40x45x4x10` | Thermal relief pad |
| Thermal Square | `ths<od>x<id>x<a>x<n>x<g>` | `ths100x40x45x4x10` | Square thermal |
| Ellipse | `el<w>x<h>` | `el80x50` | True ellipse |
| Moire | `moire<od>x<id>x<rw>x<rg>x<nc>x<lw>x<ll>x<la>` | Complex | Target/registration mark |
| Hole | `hole<d>x<p>x<tp>x<tm>` | `hole50x1x0x0` | Drill hole representation |
| Butterfly | `bfr<d>` or `bfs<d>` | `bfr50` | Butterfly pad shape |
| Rounded Square Butterfly | `bfrs<d>x<r>` | `bfrs50x10` | Butterfly with rounded corners |

### 1.2 Create StandardSymbolParser
**File:** `src/main/java/com/deltaproto/deltaodbpp/parser/StandardSymbolParser.java`

```java
public class StandardSymbolParser {
    // Parse symbol name like "r50" or "rect100x50" into StandardSymbol
    public StandardSymbol parse(String symbolName);

    // Check if this is a standard symbol (vs user-defined)
    public boolean isStandardSymbol(String symbolName);
}
```

### 1.3 Create SymbolResolver
**File:** `src/main/java/com/deltaproto/deltaodbpp/export/SymbolResolver.java`

```java
public class SymbolResolver {
    private final Map<String, Symbol> userSymbols;  // From Job.getSymbols()
    private final StandardSymbolParser standardParser;

    // Resolve symbol by name - returns either StandardSymbol or user Symbol
    public ResolvedSymbol resolve(String symbolName);

    // Resolve by symbol number using the step's symbol list
    public ResolvedSymbol resolveByNumber(int symbolNumber, Step step);
}
```

## Phase 2: Symbol Renderers (Core Rendering)

### 2.1 Create SymbolRenderer Interface
**File:** `src/main/java/com/deltaproto/deltaodbpp/export/render/SymbolRenderer.java`

```java
public interface SymbolRenderer {
    /**
     * Render a symbol at the given position with transforms.
     * @param x Center X position
     * @param y Center Y position
     * @param rotation Rotation in degrees
     * @param mirror Whether to mirror
     * @param scale Scale factor (from resize)
     * @return SVG path/element string
     */
    String render(double x, double y, double rotation, boolean mirror, double scale);

    /**
     * Get the bounding box of this symbol (for bounds calculation).
     */
    Bounds getBounds(double scale);
}
```

### 2.2 Create Standard Symbol Renderers
**Directory:** `src/main/java/com/deltaproto/deltaodbpp/export/render/`

| File | Shapes Handled |
|------|----------------|
| `RoundRenderer.java` | `r` (circle) |
| `SquareRenderer.java` | `s` (square) |
| `RectangleRenderer.java` | `rect`, `rc` (chamfered), `ch` (rounded) |
| `OvalRenderer.java` | `oval`, `el` (ellipse) |
| `PolygonRenderer.java` | `di` (diamond), `oct`, `hex_l`, `hex_s`, `tri` |
| `DonutRenderer.java` | `donut_r`, `donut_s`, `donut_o`, `donut_rs`, `donut_sr` |
| `ThermalRenderer.java` | `thr`, `ths`, `tho` |
| `SpecialRenderer.java` | `moire`, `hole`, `bfr`, `bfs`, `ho` |
| `UserSymbolRenderer.java` | User-defined symbols (renders nested features) |

### 2.3 Implement Each Renderer with Tests

Each renderer needs:
1. **Parameter parsing** from symbol name
2. **SVG generation** (path, polygon, or combined elements)
3. **Transform handling** (rotation, mirror, scale)
4. **Unit tests** with visual verification

Example test structure:
```java
@Test
void testRoundSymbol_r50() {
    RoundRenderer renderer = new RoundRenderer(50); // 50 mils
    String svg = renderer.render(100, 100, 0, false, 1.0);
    // Verify it's a circle with correct center and radius
    assertTrue(svg.contains("<circle"));
    assertTrue(svg.contains("r=\"25.0\"")); // radius = diameter/2
}
```

## Phase 3: Feature Renderer Refactoring

### 3.1 Refactor SvgRenderer to Use Symbol Resolution

**Current flow:**
```
Feature (Pad) --> Fixed circle rendering
```

**New flow:**
```
Feature (Pad)
    --> Get symbolNumber
    --> SymbolResolver.resolveByNumber()
    --> Get SymbolRenderer for resolved symbol
    --> Render with position, rotation, mirror, resize from Pad
```

### 3.2 Update Pad Rendering
**File:** `src/main/java/com/deltaproto/deltaodbpp/export/SvgRenderer.java`

```java
private void renderPad(Pad pad, Writer writer, String color) {
    // Resolve the symbol
    ResolvedSymbol symbol = symbolResolver.resolveByNumber(pad.getSymbolNumber(), currentStep);
    SymbolRenderer renderer = getRendererFor(symbol);

    // Calculate transforms from pad properties
    double rotation = calculateRotation(pad.getOrientationType(), pad.getCustomRotation());
    boolean mirror = pad.getOrientationType() >= 4; // Types 4-7 are mirrored
    double scale = pad.getResizeFactor() != null ? pad.getResizeFactor() : 1.0;

    // Render
    String svgElement = renderer.render(pad.getX(), pad.getY(), rotation, mirror, scale);
    writer.write(svgElement);
}
```

### 3.3 Update Line Rendering for Line Width
Lines should use the symbol to determine stroke width (not a fixed default):

```java
private void renderLine(Line line, Writer writer, String color) {
    ResolvedSymbol symbol = symbolResolver.resolveByNumber(line.getSymbolNumber(), currentStep);
    double strokeWidth = symbol.getWidth(); // From round symbol diameter
    // Render line with correct stroke width
}
```

## Phase 4: Test Infrastructure

### 4.1 Visual Test Framework
Create a test that generates SVGs for visual inspection:

**File:** `src/test/java/com/deltaproto/deltaodbpp/export/SymbolVisualTest.java`

```java
@Test
void generateSymbolCatalog() {
    // Generate an SVG showing all standard symbols for visual verification
    // Outputs to target/test-output/symbol-catalog.svg
}
```

### 4.2 Unit Tests for Each Symbol Type

| Test Class | Coverage |
|------------|----------|
| `StandardSymbolParserTest.java` | Parsing all symbol name formats |
| `RoundRendererTest.java` | Round symbols with various sizes |
| `RectangleRendererTest.java` | rect, rc, ch variants |
| `OvalRendererTest.java` | oval, ellipse |
| `PolygonRendererTest.java` | diamond, octagon, hexagon, triangle |
| `DonutRendererTest.java` | All donut variants |
| `ThermalRendererTest.java` | Thermal relief patterns |
| `SymbolResolverTest.java` | Resolution of standard vs user symbols |
| `PadRenderingIntegrationTest.java` | End-to-end pad rendering |

### 4.3 Regression Tests
Compare rendered output against known-good reference SVGs:

```java
@Test
void testRenderWifiBoardMatchesReference() {
    // Render the wifi board
    // Compare output to reference SVG (pixel comparison or SVG diff)
}
```

## Phase 5: Integration

### 5.1 Update MultiLayerSvgRenderer
Ensure multi-layer composite also uses symbol resolution.

### 5.2 Update Web Application
The odbpp-app should display symbols correctly after this work.

### 5.3 Performance Optimization
- Cache parsed standard symbols
- Cache symbol renderers
- Lazy-load user-defined symbols

## Implementation Order

### Sprint 1: Foundation (Estimated: 2-3 days work)
1. [ ] Create `StandardSymbol` model class
2. [ ] Create `StandardSymbolParser` with tests
3. [ ] Implement parsing for: `r`, `s`, `rect`, `oval`
4. [ ] Create `SymbolRenderer` interface
5. [ ] Implement `RoundRenderer` with tests
6. [ ] Implement `SquareRenderer` with tests
7. [ ] Implement `RectangleRenderer` with tests
8. [ ] Implement `OvalRenderer` with tests

### Sprint 2: Core Shapes (Estimated: 2-3 days work)
1. [ ] Implement `PolygonRenderer` for: `di`, `oct`, `hex_l`, `hex_s`, `tri`
2. [ ] Implement `DonutRenderer` for all donut variants
3. [ ] Create `SymbolResolver`
4. [ ] Refactor `SvgRenderer.renderPad()` to use symbol resolution
5. [ ] Refactor `SvgRenderer.renderLine()` to use symbol width

### Sprint 3: Advanced Shapes (Estimated: 1-2 days work)
1. [ ] Implement `ThermalRenderer`
2. [ ] Implement `SpecialRenderer` for: `moire`, `hole`, `bfr`, `ho`
3. [ ] Implement `UserSymbolRenderer` for user-defined symbols
4. [ ] Handle rounded/chamfered rectangle variants

### Sprint 4: Testing & Polish (Estimated: 1-2 days work)
1. [ ] Create visual test catalog
2. [ ] Add regression tests with reference SVGs
3. [ ] Update `MultiLayerSvgRenderer`
4. [ ] Performance optimization (caching)
5. [ ] Update documentation

## File Structure After Implementation

```
odbpp-lib/src/main/java/com/deltaproto/deltaodbpp/
├── model/
│   └── symbol/
│       ├── StandardSymbol.java       # Parsed standard symbol data
│       └── ResolvedSymbol.java       # Union type for resolved symbols
├── parser/
│   └── StandardSymbolParser.java     # Parse "r50", "rect100x50", etc.
└── export/
    ├── SymbolResolver.java           # Resolve symbol number to definition
    ├── SvgRenderer.java              # Updated to use symbols
    ├── MultiLayerSvgRenderer.java    # Updated to use symbols
    └── render/
        ├── SymbolRenderer.java       # Interface
        ├── RoundRenderer.java
        ├── SquareRenderer.java
        ├── RectangleRenderer.java
        ├── OvalRenderer.java
        ├── PolygonRenderer.java
        ├── DonutRenderer.java
        ├── ThermalRenderer.java
        ├── SpecialRenderer.java
        └── UserSymbolRenderer.java
```

## Success Criteria

1. **All standard symbols render correctly** - Visual verification with symbol catalog
2. **User-defined symbols render** - Complex pad shapes from symbols directory work
3. **Lines have correct widths** - Based on round symbol diameter
4. **Transforms work** - Rotation, mirroring, and scaling applied correctly
5. **Test coverage > 90%** - All renderers have comprehensive unit tests
6. **Integration tests pass** - Real ODB++ files render recognizably
7. **Web app shows correct PCB** - odbpp-app displays proper board visualization

## References

- [ODB++ Specification v7.0](https://odbplusplus.com/wp-content/uploads/sites/2/2020/03/ODB_Format_Description_v7.pdf) - Official symbol definitions (pages 203-215)
- [ODB++ Symbols Overview](https://www.artwork.com/odb++/rs274x/symbols.htm) - Symbol naming conventions
- [ODB++ Design Format Spec 8.1](https://odbplusplus.com/wp-content/uploads/sites/2/2020/03/odb_spec_user.pdf) - Latest specification
