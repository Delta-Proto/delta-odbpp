## **Appendix A Standard ODB++ Symbols**

A symbol is one of the geometric primitives supported by ODB++ described here, or a combination of them. They are defined by the parameters: width, height, radius, diameter, spokes, gap, angle, size and corner. The corners of rounded or chamfered rectangles or thermals are specified in ascending order moving counter-clockwise—starting from the top-right corner.

For an example of a symbol with chamfered corners, see "Rounded/Chamfered Rectangles"on page 203.

All symbols described in this section are standard (system) symbols. User defined symbols are defined as described in "symbols (User-Defined Symbols)"on page 92.

Basic Standard Symbols Symbols Suitable for Solder Stencil Design Other Symbol Information Obsolete Symbols

## **Basic Standard Symbols**

These are the basic standard symbols.

![](_page_0_Picture_7.jpeg)

![](_page_1_Figure_1.jpeg)

| Symbol Name             | Example | Parameters                                                                                   |
|-------------------------|---------|----------------------------------------------------------------------------------------------|
| Diamond                 |         | di <w>x<h><br/>w — Diamond width<br/>h — Diamond height</h></w>                              |
| Octagon                 |         | oct <w>x<h>x<r><br/>w — Octagon width<br/>h — Octagon height<br/>r — Corner size</r></h></w> |
| Round Donut             |         | donut_r <od>x<id><br/>od — Outer diameter<br/>id — Inner diameter</id></od>                  |
| Square Donut            |         | donut_s <od>x<id><br/>od — Outer diameter<br/>id — Inner diameter</id></od>                  |
| Square / Round<br>Donut |         | donut_sr <od>x<id><br/>od — Outer diameter<br/>id — Inner diameter</id></od>                 |

| Symbol Name                | Example | Parameters                                                                                                                                                                                                                                                                               |
|----------------------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Rounded Square<br>Donut    |         | donut_s <od>x<id>xr<rad>x<corners><br/>od — Outer diameter<br/>id — Inner diameter<br/>rad — Corner radius<br/>corners — Indicates which corners are<br/>rounded. x<corners> is omitted if all<br/>corners are rounded.</corners></corners></rad></id></od>                              |
| Rectangle Donut            |         | donut_rc <ow>x<oh>x<lw><br/>ow — Outer width<br/>oh — Outer height<br/>lw — Line width</lw></oh></ow>                                                                                                                                                                                    |
| Rounded<br>Rectangle Donut |         | donut_rc <ow>x<oh>x<lw>xr<rad>x<c<br>orners&gt;<br/>ow — Outer width<br/>oh — Outer height<br/>lw — Line width<br/>rad — Corner radius<br/>corners —Indicates which corners are<br/>rounded. x<corners> is omitted if all<br/>corners are rounded.</corners></c<br></rad></lw></oh></ow> |
| Oval Donut                 |         | donut_o <ow>x<oh>x<lw><br/>ow — Outer width<br/>oh — Outer height<br/>lw — Line width</lw></oh></ow>                                                                                                                                                                                     |

| Symbol Name           | Example | Parameters                                                                                     |
|-----------------------|---------|------------------------------------------------------------------------------------------------|
| Horizontal<br>Hexagon |         | hex_l <w>x<h>x<r><br/>w — Hexagon width<br/>h — Hexagon height<br/>r — Corner size</r></h></w> |
| Vertical Hexagon      |         | hex_s <w>x<h>x<r><br/>w — Hexagon width<br/>h — Hexagon height<br/>r — Corner size</r></h></w> |
| Butterfly             |         | bfr <d><br/>d — Diameter</d>                                                                   |
| Square Butterfly      |         | bfs <s><br/>s — Size</s>                                                                       |

| Symbol Name                | Example | Parameters                                                                                                                                                                                                                                                                                                      |
|----------------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Triangle                   |         | tri <base/> x <h><br/>base — Triangle base<br/>h — Triangle height</h>                                                                                                                                                                                                                                          |
| Half Oval                  |         | oval_h <w>x<h><br/>w — Width<br/>h — Height</h></w>                                                                                                                                                                                                                                                             |
| Round Thermal<br>(Rounded) |         | thr <od>x<id>x<angle>x<num_spokes<br>&gt;x<gap><br/>od — Outer diameter<br/>id — Inner diameter<br/>angle — Gap angle from 0 degrees<br/>num_spokes — Number of spokes<br/>gap — Size of spoke gap<br/>od and id control the air gap (size of<br/>laminate separation).</gap></num_spokes<br></angle></id></od> |
| Round Thermal<br>(Squared) |         | ths <od>x<id>x<angle>x<num_spokes<br>&gt;x<gap><br/>od — Outer diameter<br/>id — Inner diameter<br/>angle — Gap angle from 0 degrees</gap></num_spokes<br></angle></id></od>                                                                                                                                    |

| Symbol Name                      | Example | Parameters                                                                                                                                                                                                                                                                                                                                                     |
|----------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|                                  |         | num_spokes — Number of spokes<br>gap — Size of spoke gap<br>od and id control the air gap (size of<br>laminate separation).                                                                                                                                                                                                                                    |
| Square Thermal                   |         | s_ths <os>x<is>x<angle>x<num_spok<br>es&gt;x<gap><br/>os — Outer size<br/>is — Inner size<br/>angle — Gap angle from 0 degrees<br/>num_spokes — Number of spokes<br/>gap — Size of spoke gap<br/>os and is control the air gap (size of<br/>laminate separation).</gap></num_spok<br></angle></is></os>                                                        |
| Square Thermal<br>(Open Corners) |         | s_tho <od>x<id>x<angle>x<num_spo<br>kes&gt;x<gap><br/>od — Outer diameter<br/>id — Inner diameter<br/>angle — Gap angle from 0 degrees in<br/>increments of 45 degrees<br/>num_spokes — Number of spokes: 1,<br/>2, or 4<br/>gap — Size of spoke gap<br/>od and id control the air gap (size of<br/>laminate separation).</gap></num_spo<br></angle></id></od> |

| Symbol Name             | Example | Parameters                                                                                               |
|-------------------------|---------|----------------------------------------------------------------------------------------------------------|
| Line Thermal            |         | s_thr <os>x<is>x<angle>x<num_spok<br>es&gt;x<gap></gap></num_spok<br></angle></is></os>                  |
|                         |         | os — Outer size                                                                                          |
|                         |         | is — Inner size                                                                                          |
|                         |         | angle — Gap angle always 45 degrees                                                                      |
|                         |         | num_spoke — number of spokes<br>always 4                                                                 |
|                         |         | gap — Size of spoke gap                                                                                  |
|                         |         | Ends of lines are rounded with diameter<br>(os-is)/2.                                                    |
| Square-Round<br>Thermal |         | sr_ths <os>x<id>x<angle>x<num_spo<br>kes&gt;x<gap></gap></num_spo<br></angle></id></os>                  |
|                         |         | os — Outer size                                                                                          |
|                         |         | id — Inner diameter                                                                                      |
|                         |         | angle — Gap angle from 0 degrees                                                                         |
|                         |         |                                                                                                          |
|                         |         | num_spokes — Number of spokes                                                                            |
|                         |         | gap — Size of spoke gap                                                                                  |
|                         |         | os and id control the air gap (size of<br>laminate separation).                                          |
| Rectangular<br>Thermal  |         | rc_ths <w>x<h>x<angle>x<num_spok<br>es&gt;x<gap>x<air_gap></air_gap></gap></num_spok<br></angle></h></w> |
|                         |         | w — Outer width                                                                                          |
|                         |         | h — Outer height                                                                                         |
|                         |         | angle — Gap angle from 0 degrees; in<br>multiples of 45 degrees                                          |
|                         |         |                                                                                                          |
|                         |         | num_spokes — Number of spokes                                                                            |
|                         |         | gap — Size of spoke gap                                                                                  |
|                         |         | air_gap — Size of laminate separation                                                                    |

![](_page_8_Figure_1.jpeg)

| Symbol Name                     | Example | Parameters                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|---------------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Rounded<br>Rectangle<br>Thermal |         | num_spokes — Number of spokes<br>gap — Size of spoke gap<br>rad — Corner radius<br>corners — Indicates which corners are<br>rounded. x <corners> is omitted if all<br/>corners are rounded.<br/>rc_ths<ow>x<oh>x<angle>x<num_sp<br>okes&gt;x<gap>x<lw>xr<rad>x<corne<br>rs&gt;<br/>ow — Outer width<br/>oh — Outer height<br/>lw — Line width<br/>angle — Gap angle from 0 degrees<br/>num_spokes — Number of spokes<br/>gap — Size of spoke gap<br/>rad — Corner radius<br/>corners — Indicates which corners are<br/>rounded. x<corners> is omitted if all<br/>corners are rounded.</corners></corne<br></rad></lw></gap></num_sp<br></angle></oh></ow></corners> |
| Oval Thermal                    |         | o_ths <ow>x<oh>x<angle>x<num_spo<br>kes&gt;x<gap>x<lw><br/>ow — Outer width<br/>oh — Outer height<br/>angle — Gap angle from 0 degrees<br/>num_spokes — Number of spokes<br/>gap — Size of spoke gap<br/>lw — Line width</lw></gap></num_spo<br></angle></oh></ow>                                                                                                                                                                                                                                                                                                                                                                                                  |

| Symbol Name     | Example | Parameters                                                                                                   |
|-----------------|---------|--------------------------------------------------------------------------------------------------------------|
| Oblong Thermal  |         | oblong_ths <ow>x<oh>x<angle>x<nu<br>m_spokes&gt;x<gap>x<lw>x<r s></r s></lw></gap></nu<br></angle></oh></ow> |
|                 |         | ow — Outer width                                                                                             |
|                 |         | oh — Outer height                                                                                            |
|                 |         | angle — For 2 spokes, angle can be 0<br>or 90 degrees; for 4 spokes, angle can<br>be 0, 45, or 90 degrees.   |
|                 |         | num_spokes — Number of spokes                                                                                |
|                 |         | gap — Size of spoke gap                                                                                      |
|                 |         | lw — Line width                                                                                              |
|                 |         | r s — Support for rounded or straight<br>corners                                                             |
| Home Plate      |         | hplate <w>x<h>x<c></c></h></w>                                                                               |
| (hplate)        |         | hplate <w>x<h>x<c>x<ra>x<ro></ro></ra></c></h></w>                                                           |
|                 |         | w — Horizontal side                                                                                          |
|                 |         | h — Vertical side                                                                                            |
|                 |         | c — Cut size (c<=w)                                                                                          |
|                 |         | ra — Corner radius (acute angle)<br>(optional)                                                               |
|                 |         | ro — Corner radius (obtuse angle)<br>(optional)                                                              |
|                 |         | ra and ro must meet the restrictions in<br>"Symbols Suitable for Solder Stencil<br>Design"on page 201.       |
| Inverted Home   |         | rhplate <w>x<h>x<c></c></h></w>                                                                              |
| Plate (rhplate) |         | rhplate <w>x<h>x<c>x<ra>x<ro></ro></ra></c></h></w>                                                          |
|                 |         | w — Horizontal side                                                                                          |
|                 |         | h — Vertical side                                                                                            |
|                 |         | c — Cut size (c <w)< td=""></w)<>                                                                            |
|                 |         | ra — Corner radius (acute angle)<br>(optional)                                                               |
|                 |         | ro — Corner radius (obtuse angle)<br>(optional)                                                              |
|                 |         | ra and ro must meet the restrictions in<br>"Symbols Suitable for Solder Stencil<br>Design"on page 201.       |

| Symbol Name                                    | Example | Parameters                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|------------------------------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Flat Home Plate<br>(fhplate)                   |         | fhplate <w>x<h>x<vc>x<hc><br/>fhplate<w>x<h>x<vc>x<hc>x<ra>x&lt;<br/>ro&gt;<br/>w — Horizontal side<br/>h — Vertical side<br/>hc — Horizontal cut size (hc<w)<br>vc — Vertical cut size (vc<h 2)<br="">ra — Corner radius (acute angle)<br/>(optional)<br/>ro — Corner radius (obtuse angle)<br/>(optional)<br/>ra and ro must meet the restrictions in<br/>"Symbols Suitable for Solder Stencil<br/>Design"on page 201.</h></w)<br></ra></hc></vc></h></w></hc></vc></h></w> |
| Radiused<br>Inverted Home<br>Plate (radhplate) |         | radhplate <w>x<h>x<ms><br/>radhplate<w>x<h>x<ms>x<ra><br/>w — Horizontal side<br/>h — Vertical side (h&gt;(ms + (w-ms)/2)/2)<br/>ms — Middle curve size (ms <w)<br>ra — Corner radius (optional)<br/>ra and ro must meet the restrictions in<br/>"Symbols Suitable for Solder Stencil<br/>Design"on page 201.</w)<br></ra></ms></h></w></ms></h></w>                                                                                                                          |
| Radiused Home<br>Plate (dshape)                |         | dshape <w>x<h>x<r><br/>dshape<w>x<h>x<r>x<ra><br/>w — Horizontal side<br/>h — Vertical side<br/>r — Relief (r&lt;=w or (r=w and h=2*w))<br/>ra — Corner radius (optional)<br/>ra and ro must meet the restrictions in<br/>"Symbols Suitable for Solder Stencil<br/>Design"on page 201.</ra></r></h></w></r></h></w>                                                                                                                                                           |
| Cross<br>(crossx[r s])                         |         | The cross symbol consists of two<br>intersecting orthogonal line segments.<br>The cross point is at the intersection of<br>their respective skeletons.<br>cross <w>x<h>x<hs>x<vs>x<hc>x<vc<br>&gt;x[r s]<br/>cross<w>x<h>x<hs>x<vs>x<hc>x<vc<br>&gt;x[r s]<ra><br/>w — Horizontal side<br/>h — Vertical side</ra></vc<br></hc></vs></hs></h></w></vc<br></hc></vs></hs></h></w>                                                                                               |

![](_page_12_Figure_1.jpeg)

![](_page_13_Figure_1.jpeg)