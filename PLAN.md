# ODB++ Parsing Library Implementation Plan

This document outlines the phased approach to developing a comprehensive ODB++ parsing library in Java, leveraging the existing codebase and adhering to the ODB++ Design Format Specification (Release 8.1 Update 4).

**Last Updated:** December 2024

---

## Current Status Summary

| Phase | Description | Status | Progress |
|-------|-------------|--------|----------|
| Phase 0 | Foundational Setup | **Complete** | 100% |
| Phase 1 | Data Model Implementation | **Complete** | 100% |
| Phase 2a | File System & Basic Parsing | **Complete** | 100% |
| Phase 2b | Product Model Entities | **Complete** | 100% |
| Phase 3 | Step Entities | **Mostly Complete** | 90% |
| Phase 4 | Layer Entities | **Complete** | 100% |
| Phase 5 | Attributes & Advanced Features | **Mostly Complete** | 80% |
| Phase 6 | Testing & Refinement | **Mostly Complete** | 90% |
| Phase 7 | SVG Export & Visualization | **In Progress** | 50% |
| Phase 8 | Gerber/Excellon Export (ODB++ → Gerber) | **Working v1** | 80% |

**Implementation Statistics:**
- **74 Model Classes** implemented (~2,100 LOC)
- **23 Parser Classes** implemented (~1,900 LOC)
- **10 Utility/Export Classes** implemented
- **20 Test Classes** with 119 test methods (all passing)

---

## Specification Reference

The ODB++ specification has been converted to markdown and organized in:
`docs/odb_spec_user/`

Key sections for implementation reference:
- **Chapter 1:** ODB++ Design Format Specification (File System, Units, Attributes, Symbols, Geometry)
- **Chapter 2:** Product Model Tree (Directory Structure)
- **Chapter 3:** Product Model Entities (misc, matrix, fonts, symbols, wheels)
- **Chapter 4:** Step Entities (stephdr, eda, boms, netlists, zones)
- **Chapter 5:** Layer Entities (features, components, dimensions, tools)

See `docs/odb_spec_user_processed.md` for the full preprocessed specification.

---

## Phase 0: Foundational Setup & Architecture

**Objective:** Establish a robust foundational architecture for the Java ODB++ parsing library.
**Status:** **COMPLETE**

*   [x] **Task 0.1: Modular Project Structure**
    *   Maven multi-module structure implemented:
        - `odbpp-lib` - Core parsing library (active development)
        - `odbpp-app` - CLI application (stub)
        - `odbpp-server` - REST API server (stub)
        - `odbpp-utils` - Utilities (stub)

*   [x] **Task 0.2: Data Model Definition**
    *   **Note:** Protobuf is configured in pom.xml but Java classes are hand-written (not generated from .proto files)
    *   66 model classes implemented using Lombok annotations
    *   Consider future migration to Protobuf for serialization interoperability

*   [x] **Task 0.3: Initial CI/CD Setup**
    *   Maven build configured with Java 21
    *   JUnit Jupiter testing framework
    *   Dependencies: Apache Commons Compress, Jackson XML, SLF4J/Logback

---

## Phase 1: Data Model Implementation

**Objective:** Create comprehensive Java data models based on ODB++ specification.
**Status:** **COMPLETE**
**Spec Reference:** Chapters 1-5 (Entity definitions)

*   [x] **Task 1.1: Core Product Model Classes**
    - `Job` - Main container for entire ODB++ product model
    - `Net` - Electrical net definition
    - `Component` - Component placement data
    - `Part` - Part definition
    - `Package` - Package geometry definition
    - `Pin`, `PinConnection` - Pin and connection data
    - `BoardSide` (enum) - TOP, BOTTOM

*   [x] **Task 1.2: Step and Layer Model Classes**
    - `Step` - PCB step/panel definition
    - `Layer` - Layer definition with type and polarity
    - `Symbol`, `Wheel` - Symbol and wheel definitions

*   [x] **Task 1.3: Feature Model Classes**
    - `Feature` - Abstract base class
    - `Pad` - Pad features with symbol reference
    - `Line` - Line segments
    - `Arc` - Circular arcs
    - `Surface` - Complex polygonal surfaces
    - `Text` - Text elements
    - `Barcode` - Barcode definitions

*   [x] **Task 1.4: Support Model Classes**
    - `Attribute`, `AttributeDefinition`, `AttrList`
    - `Contour`, `ContourPolygon`, `Polygon`, `Point`
    - `MiscInfo`, `Matrix`, `MatrixLayer`
    - `StepHdr`, `Profile`, `Zone`
    - `Bom`, `BomItem`, `DCode`
    - `EdaData`, `Netlist`, `Metadata`
    - `StandardFont`, `Features`, `Components`

*   [x] **Task 1.5: Stackup/Impedance Models**
    - `stackup` package: `StackupFile`, `Stackup`, `Layer`, `Group`, `Conductor`, `Dielectric`, `Material`, etc.
    - `impedance` package: `ImpedanceFile`, `Descriptor`, `RequiredImpedance`, etc.

---

## Phase 2a: File System and Basic Parsing

**Objective:** Establish foundation for navigating ODB++ file system and parsing file types.
**Status:** **COMPLETE**
**Spec Reference:** Chapter 1 (File System, Format Definition, Large File Compression)

*   [x] **Task 2.1: File System Navigation**
    - `FileSystemNavigator` - Navigates ODB++ directory structure
    - `Decompressor` - Handles .Z compressed files (Apache Commons Compress)
    - Path resolution for nested entities working correctly

*   [x] **Task 2.2: Generic File Type Parsing**
    - `LineRecordParser` - Line-by-line file parsing
    - `StructuredTextParser` - Structured text format parsing
    - `XmlParser` - Jackson-based XML parsing
    - Basic error handling implemented

---

## Phase 2b: Product Model Entities (Chapter 3)

**Objective:** Parse top-level product model entities.
**Status:** **COMPLETE**
**Spec Reference:** Chapter 3 (Product Model Entities)

*   [x] **Task 2.3: `misc` Directory Parsing**
    - [x] `MiscInfoParser` - Parse `misc/info`
    - [x] `AttrListParser` - Parse `misc/attrlist`
    - [x] Parse `misc/last_save`
    - [x] `XmlParser` - Parse `misc/metadata.xml`
    - [x] `AttributeDefinitionParser` - Parse `misc/sysattr.*` and `misc/userattr`

*   [x] **Task 2.4: `matrix` Directory Parsing**
    - [x] `MatrixParser` - Parse `matrix/matrix`
    - [x] `StackupParser` - Parse `matrix/stackup.xml`

*   [x] **Task 2.5: `fonts` Directory Parsing**
    - [x] `StandardFontParser` - Parse `fonts/standard`

*   [x] **Task 2.6: `symbols` Directory Parsing**
    - [x] `AttrListParser` - Parse `<symbol_name>/attrlist`
    - [x] `FeaturesFileParser` - Parse `<symbol_name>/features`

*   [x] **Task 2.7: `wheels` Directory Parsing**
    - [x] `AttrListParser` - Parse `<wheel_name>/attrlist`
    - [x] `DCodeParser` - Parse `<wheel_name>/dcodes`

---

## Phase 3: Step Entities (Chapter 4)

**Objective:** Parse step-level entities and sub-directories.
**Status:** **MOSTLY COMPLETE** (90%)
**Spec Reference:** Chapter 4 (Step Entities)

*   [x] **Task 3.1: Core Step Parsing**
    - [x] `StepHdrParser` - Parse `<step_name>/stephdr`
    - [x] `AttrListParser` - Parse `<step_name>/attrlist`
    - [x] `ProfileParser` - Parse `<step_name>/profile`
    - [x] `ZonesParser` - Parse `<step_name>/zones`
    - [x] `ImpedanceParser` - Parse `<step_name>/impedance.xml`

*   [x] **Task 3.2: BOM Parsing**
    - [x] `BomParser` - Parse `<step_name>/boms/<bom_name>/bom`
    - [x] Parse `<bom_name>/files` (source files)

*   [x] **Task 3.3: EDA Data Parsing**
    - [x] `EdaDataParser` - Parse `<step_name>/eda/data`
    - Populates `Net`, `Component`, `Package` models

*   [ ] **Task 3.4: Additional EDA Files** *(NOT IMPLEMENTED)*
    - [ ] Parse `<step_name>/eda/shortf` (short circuits)
    - [ ] Parse `<step_name>/eda/hdi_netlist` (HDI netlist)

---

## Phase 4: Layer Entities (Chapter 5)

**Objective:** Parse layer-level entities and contents.
**Status:** **MOSTLY COMPLETE** (90%)
**Spec Reference:** Chapter 5 (Layer Entities)

*   [x] **Task 4.1: Core Layer Parsing**
    - [x] `AttrListParser` - Parse `<layer_name>/attrlist`
    - [x] `ComponentsParser` - Parse `<layer_name>/components`
    - [x] `ProfileParser` - Parse `<layer_name>/profile`
    - [x] `ToolsParser` - Parse `<layer_name>/tools` (drill tool definitions)

*   [x] **Task 4.2: Features Parsing**
    - [x] `FeaturesFileParser` - Parse `<layer_name>/features`
    - [x] `Pad` records - Complete
    - [x] `Line` records - Complete
    - [x] `Arc` records - Complete
    - [x] `Surface` records - Complete with `SurfaceParser`
    - [x] `Text` records - Complete
    - [x] `Barcode` records - Complete

*   [x] **Task 4.3: Additional Layer Files**
    - [x] Parse `<layer_name>/dimensions` - Complete with `DimensionsParser`
    - [x] Parse `<layer_name>/notes` - Complete with `NotesParser`
    - [x] Parse `<layer_name>/tools` - Complete with `ToolsParser`

---

## Phase 5: Attributes and Advanced Features

**Objective:** Implement robust attribute handling and complex geometric entities.
**Status:** **MOSTLY COMPLETE** (80%)
**Spec Reference:** Chapter 1 (Attributes, Coordinates, Geometry), Appendix B (System Attributes)

*   [x] **Task 5.1: Attribute Value Assignment**
    - [x] Generic attribute parsing for all types (`BOOLEAN`, `TEXT`, `OPTION`, `INTEGER`, `FLOAT`)
    - [x] `AttributeResolver` utility for attribute lookup by name and index
    - [x] `parseValue()` - Type-aware value parsing
    - [x] `validateValue()` - Value validation against constraints (min/max, options)
    - [x] `createAttribute()` - Attribute creation from index and value
    - [ ] System attribute validation against Appendix B definitions

*   [ ] **Task 5.2: Geometric Entity Enhancement**
    - [ ] Complete rotation/mirroring parameter handling
    - [ ] Symbol reference resolution in features
    - [ ] Contour winding direction validation
    - [ ] Self-Intersecting Polygon (SIP) support per specification

*   [x] **Task 5.3: Coordinate System Handling**
    - [x] `UnitConverter` utility class with full unit support
    - [x] Unit conversion (INCH, MM, MIL, MICRON)
    - [x] `toInches()` / `fromInches()` conversion methods
    - [x] `parseUnit()` - Parse unit strings with aliases
    - [x] Angle normalization (0-360 degrees) via `normalizeAngle()`

---

## Phase 6: Testing and Refinement

**Objective:** Ensure accuracy, robustness, and performance.
**Status:** **IN PROGRESS** (60%)
**Spec Reference:** All chapters for validation

*   [x] **Task 6.1: Unit Testing**
    - [x] `FeaturesFileParserTest` - Features parsing tests (2 tests)
    - [x] `BomParserTest` - BOM parsing tests (8 tests)
    - [x] `ProfileParserTest` - Profile parsing tests (1 test)
    - [x] `BarcodeParserTest` - Barcode parsing tests (4 tests)
    - [x] `ToolsParserTest` - Drill tools parsing tests (6 tests)
    - [x] `SvgRendererTest` - SVG export tests (16 tests)
    - [x] `DimensionsParserTest` - Dimensions parsing tests (3 tests)
    - [x] `NotesParserTest` - Notes parsing tests (3 tests)
    - [x] `UnitConverterTest` - Unit conversion tests (13 tests)
    - [x] `AttributeResolverTest` - Attribute resolution tests (16 tests)
    - [x] `MiscInfoParserTest` - MiscInfo parsing tests (3 tests)
    - [x] `MatrixParserTest` - Matrix parsing tests (4 tests)
    - [x] `AttrListParserTest` - AttrList parsing tests (4 tests)
    - [x] `StepHdrParserTest` - StepHdr parsing tests (3 tests)
    - [x] `ZonesParserTest` - Zones parsing tests (3 tests)
    - [x] `ComponentsParserTest` - Components parsing tests (6 tests)
    - [x] `EdaDataParserTest` - EDA data parsing tests (6 tests)
    - [x] `StandardFontParserTest` - Standard font parsing tests (4 tests)
    - [ ] Model class unit tests (optional)

*   [~] **Task 6.2: Integration Testing**
    - [x] `OdbParserIntegrationTest` - End-to-end parsing tests (14 tests)
    - [x] Tests for sandbox-odb_wifi example (5 tests)
    - [x] Tests for BoardD example (3 tests)
    - [x] Tests for designodb_rigidflex testdata (6 tests)
    - [ ] Test with compressed (.Z) files
    - [ ] Test with different attribute types and geometries

---

## Phase 7: SVG Export & Visualization

**Objective:** Render PCB layers as SVG graphics for visualization and verification.
**Status:** **IN PROGRESS** (30%)
**Spec Reference:** Chapter 1 (Symbols, Geometry), Chapter 5 (Layer Features)

*   [x] **Task 7.1: SVG Rendering Infrastructure**
    - [x] Create `SvgRenderer` class in `com.deltaproto.deltaodbpp.export` package
    - [x] Create `SvgRenderOptions` for configurable rendering
    - [x] Define SVG coordinate system mapping (ODB++ coords to SVG viewBox)
    - [x] Implement unit conversion for SVG output (configurable scale)
    - [x] Y-axis flipping for correct orientation
    - [ ] Handle layer stackup ordering for multi-layer visualization

*   [x] **Task 7.2: Feature to SVG Conversion**
    - [x] `Pad` rendering - Circle with resize factor support
    - [x] `Line` rendering - Line with stroke width
    - [x] `Arc` rendering - SVG arc path commands
    - [x] `Surface` rendering - Polygon fill with contour support
    - [x] `Text` rendering - SVG text (basic)
    - [x] `Barcode` rendering - Rectangle with rotation support
    - [ ] Full symbol lookup for pad rendering (advanced)

*   [ ] **Task 7.3: Symbol Resolution**
    - [ ] Standard symbol rendering (round, square, rectangle, oval, etc.)
    - [ ] User-defined symbol rendering from symbol features
    - [ ] Symbol scaling and rotation transforms
    - [ ] Aperture macro support for complex symbols

*   [~] **Task 7.4: Layer Styling**
    - [x] Default color mapping for layer types
    - [x] Configurable colors via `SvgRenderOptions`
    - [x] Background color support
    - [x] Polarity handling (positive/negative)
    - [x] Drill layer representation (holes with crosshairs)
    - [ ] Copper pour visualization

*   [~] **Task 7.5: Export Options**
    - [x] Single layer export to individual SVG files
    - [x] Configurable padding and scale
    - [x] Board outline (profile) rendering via `renderProfile()`
    - [x] Drill layer rendering via `renderDrillLayer()`
    - [ ] Multi-layer composite with transparency/opacity
    - [ ] Top/bottom view with proper layer ordering
    - [ ] Component placement overlay option
    - [ ] Net highlighting capability

*   [ ] **Task 7.6: CLI Integration**
    - [ ] Implement in `odbpp-app` module
    - [ ] Command: `odbpp export-svg <input.odb> --layer <name> --output <file.svg>`
    - [ ] Options: `--all-layers`, `--top-view`, `--bottom-view`, `--scale`, `--colors`

---

## Phase 8: Gerber/Excellon Export (ODB++ → Gerber Converter)

**Objective:** Convert a parsed ODB++ job into the Gerber X2 + Excellon file
set most PCB manufacturers require, so ODB++-only designs can be served by
gerber-only fabs.
**Status:** **WORKING v1** — validated against 6 real-world boards,
including a board for which a reference CAD Gerber export is
available (BoardF: visual side-by-side match).

*   [x] **Task 8.1: Gerber X2 writer** (`com.deltaproto.deltaodbpp.export.gerber.GerberWriter`)
    - FSLAX46Y46/MOMM, TF file attributes, D01/D02/D03, G75 arcs, G36/G37 regions, LPD/LPC
*   [x] **Task 8.2: Aperture mapping** (`ApertureRegistry`, `OdbSymbolShape`)
    - r/s/rect/oval → standard C/R/O apertures
    - rounded rect, donut, arbitrary rotations → AM aperture macros
    - User-defined symbols flattened inline with full transform support
      (`FeatureTransform`, rotation CW per ODB++ spec, x-mirror, nesting)
*   [x] **Task 8.3: Excellon writer with slot support** (`ExcellonWriter`)
    - Drill hits, tool list reusing the ODB++ tools-file numbers
    - **Slots:** ODB++ line features on drill layers → canonical G85 records
    - Arc/circular milling tessellated into G85 chains (0.01 mm chord tolerance)
*   [x] **Task 8.4: Job orchestration** (`OdbToGerberConverter`)
    - Matrix-driven layer mapping with X2 FileFunction (Copper,Ln,Side /
      Soldermask / Legend / Paste / Plated,n,m,PTH / NonPlated,n,m,NPTH / Profile,NP)
    - Step profile emitted as stroked outline (fab convention), ROUT layers as stroke gerber
    - Text stroked via the ODB++ standard font
*   [x] **Task 8.5: Round-trip validation**
    - `delta-gerber` (the library the DeltaProto backend uses) added as a
      test dependency; every generated file is re-parsed and checked
    - `OdbToGerberRoundTripTest` — exact hole/slot/tool assertions vs ODB++ source
    - `ConvertAllFixturesTest` — all local real-world archives convert warning-free
    - `VisualComparisonGenerator` — side-by-side PNGs vs reference CAD gerber
    - `RealWorldFixturesTest` — parser invariants on all local archives
    - Real-world fixtures live OUTSIDE the repo (private data):
      `~/dev/odbpp-fixtures` or `$ODBPP_FIXTURES_DIR`; tests skip when absent
*   [ ] **Task 8.6: Remaining gaps**
    - [ ] Step & repeat (panelized steps) not expanded
    - [ ] Surface holes emitted as LPC regions (over-clears in pathological
          overlaps; cut-in contours would be exact)
    - [ ] Dynamic text variables ($$DATE etc.) not substituted
    - [ ] Thermal/butterfly/moire standard symbols not mapped (not seen in
          any real-world board so far; falls back with warning)
    - [ ] CLI command in odbpp-app (`export-gerber <archive> -o <dir>`)

**Bug fixes that fell out of this phase:**
- `ToolsParser` scaled tool sizes as inches/mm; the spec says **mils/microns**
  (sizes were 1000× too large; THICKNESS is mils or mm and scales differently)
- `SvgRenderer.renderDrillLayer` looked up tool diameters by symbol index
  instead of the pad symbol's own r&lt;size&gt; (with tools-file dcode fallback)

## Nice to Have (Lower Priority)

These tasks are valuable but not critical for core functionality:

*   [ ] **Task 6.3: Performance Optimization**
    - [ ] Benchmark parsing on large ODB++ files
    - [ ] Memory usage optimization
    - [ ] Lazy loading for large designs
    - [ ] Parallel parsing for independent entities

*   [ ] **Task 6.4: Documentation**
    - [ ] Complete Javadoc for all public APIs
    - [ ] Usage examples and tutorials
    - [ ] Architecture documentation

---

## Appendix A: Implementation Priority Matrix

| Priority | Task | Effort | Impact |
|----------|------|--------|--------|
| ~~HIGH~~ | ~~Task 4.3 (dimensions, notes, tools)~~ | ~~Medium~~ | ~~Complete layer parsing~~ **DONE** |
| HIGH | Task 6.1 (Unit testing) | High | Code quality |
| ~~MEDIUM~~ | ~~Task 5.1 (Attribute handling)~~ | ~~Medium~~ | ~~Data completeness~~ **DONE** |
| MEDIUM | Task 7.2 (SVG feature rendering) | High | Visualization |
| MEDIUM | Task 3.4 (eda/shortf, hdi_netlist) | Low | Niche use cases |
| LOW | Task 5.3 (Coordinate handling) | Low | Already working |
| LOW | CLI/Server implementation | High | User interface |

---

## Appendix B: Parser Implementation Matrix

| File Type | Parser Class | Status | Test Coverage |
|-----------|--------------|--------|---------------|
| misc/info | MiscInfoParser | Complete | **Tested** |
| misc/attrlist | AttrListParser | Complete | **Tested** |
| misc/metadata.xml | XmlParser | Complete | Manual |
| misc/sysattr.* | AttributeDefinitionParser | Complete | Manual |
| matrix/matrix | MatrixParser | Complete | **Tested** |
| matrix/stackup.xml | StackupParser | Complete | Manual |
| fonts/standard | StandardFontParser | Complete | **Tested** |
| symbols/*/features | FeaturesFileParser | Complete | **Tested** |
| wheels/*/dcodes | DCodeParser | Complete | Manual |
| stephdr | StepHdrParser | Complete | **Tested** |
| eda/data | EdaDataParser | Complete | **Tested** |
| eda/shortf | - | **Not Implemented** | - |
| eda/hdi_netlist | - | **Not Implemented** | - |
| impedance.xml | ImpedanceParser | Complete | Manual |
| profile | ProfileParser | Complete | **Tested** |
| zones | ZonesParser | Complete | **Tested** |
| boms/*/bom | BomParser | Complete | **Tested** |
| layers/*/features | FeaturesFileParser | Complete | **Tested** |
| layers/*/components | ComponentsParser | Complete | **Tested** |
| layers/*/dimensions | DimensionsParser | Complete | **Tested** |
| layers/*/notes | NotesParser | Complete | **Tested** |
| layers/*/tools | ToolsParser | Complete | **Tested** |

---

## Appendix C: C++ Reference Project

The `examples/OdbDesign/` C++ project provides:

1. **Architectural Blueprint** - Modular structure (`OdbDesignLib`, `OdbDesignApp`, `OdbDesignServer`, `Utils`)
2. **Protobuf Definitions** - 25 .proto files (not yet ported to Java)
3. **Parsing Logic** - Reference implementations for all file types
4. **CI/CD Practices** - GitHub Actions workflows for testing and security scanning
5. **Test Strategy** - `OdbDesignTests` directory with unit/integration tests

**Key C++ Files for Reference:**
- `FileArchive.cpp` - Main parsing orchestration
- `MiscInfoFile.cpp`, `MatrixFile.cpp` - Product model parsing
- `EdaDataFile.cpp`, `StepHdrFile.cpp` - Step entity parsing
- `FeaturesFile.cpp`, `ComponentsFile.cpp` - Layer entity parsing
- `ContourPolygon.cpp` - Geometric entity handling

---

## Appendix D: Test Data

Available test designs in `src/test/resources/testdata/`:
- **designodb_rigidflex/** - Multi-layer rigid-flex board (complete ODB++ structure)
- **sample-board.zip** - Additional ODB++ design sample

Test data includes: misc/, steps/, layers/, features, components, profile, etc.
