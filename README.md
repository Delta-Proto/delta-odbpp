# Delta ODB++

A Java library for parsing ODB++ PCB design archives, rendering them to SVG/PNG,
and converting them to Gerber X2 + Excellon. Ships with an interactive web viewer.

![ODB++ Viewer](docs/Screenshot%202026-01-05%20at%2011.08.12.png)

## Maven Dependency

```xml
<dependency>
    <groupId>com.deltaproto</groupId>
    <artifactId>delta-odbpp</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Features

### ODB++ Parsing
- Reads ODB++ archives (`.zip`, `.tgz`, `.tar.gz`) and exploded directories
- Full job model: matrix, steps, layers, and features
  (pad, line, arc, surface, text, barcode)
- Symbols (standard + user-defined), components, BOM, netlists, EDA data
- Stackup, impedance, profile, and attribute parsing
- The whole model is normalised to **millimetres at parse time**
- Malformed or partial archives degrade gracefully with recorded warnings

### SVG / PNG Rendering
- Per-layer SVG rendering with the full ODB++ symbol catalogue
- Multi-layer composite rendering with configurable colours and opacity
- Realistic top/bottom PCB views (copper, soldermask, silkscreen, drills)
- Component silhouette and centroid views
- PCB layer-stackup cross-section view
- PNG rasterisation of any view through the Batik pipeline

### ODB++ → Gerber / Excellon Conversion
- Gerber X2 writer with `.FileFunction` attributes, regions (G36/G37),
  arcs (G75), and polarity (LPD/LPC)
- Aperture mapping to standard apertures and aperture macros, with full
  user-symbol flattening and transforms
- Excellon writer with drill hits, tool reuse, and **slot** support (G85)
- Matrix-driven layer mapping for copper, soldermask, paste, legend,
  plated/non-plated drills, and the board profile

### Web Viewer (`delta-odbpp-app`)
- Spring Boot app: upload an archive, view layers as stacked SVG
- Top / bottom / dual view modes, per-layer toggling, pan/zoom
- Component centroid overlay

## Quick Start

```bash
mvn clean install
cd odbpp-app
mvn spring-boot:run
```

Open http://localhost:8080 and upload an ODB++ archive.

## Usage as a Library

### Parse an archive

```java
// From an exploded ODB++ directory
Job job = new OdbParser().parse(Path.of("path/to/odb"));

// Or from a .zip / .tgz archive
Path root = new OdbArchiveExtractor().extractToTemp(Path.of("board.tgz"));
Job job2 = new OdbParser().parse(root);
```

### Render to SVG

```java
// One image with all layers stacked, top view
MultiLayerSvgRenderer.forTopView().renderJob(job, Path.of("top.svg"));

// Realistic top + bottom PNGs
byte[] topPng = new MultiLayerSvgRenderer().renderRealisticSidePng(job, true, 1024);
Files.write(Path.of("board-top.png"), topPng);
```

### Convert to Gerber + Excellon

```java
OdbToGerberConverter.Result result = new OdbToGerberConverter().convert(job, "pcb");
result.writeTo(Path.of("gerber-out"));        // writes .gbr / .drl files
result.warnings.forEach(System.out::println); // any conversion warnings
```

## Symbol Visual Test

The library includes a visual catalogue of all implemented ODB++ symbol types.

[View Symbol Visual Test](https://htmlpreview.github.io/?https://github.com/Delta-Proto/delta-odbpp/blob/main/generated/symbol-visual-test.html)

## Project Structure

- `odbpp-lib` — the published library (`com.deltaproto:delta-odbpp`):
  parsing, SVG/PNG rendering, and the ODB++→Gerber converter
- `odbpp-app` — Spring Boot web viewer (not published to Maven Central)
- `examples/` — openly-available sample ODB++ archives used by tests

## Releasing

See [DEPLOY.md](DEPLOY.md) and [RELEASING.md](RELEASING.md).

## License

MIT
