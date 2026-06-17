# Finding the PCB Origin and Aligning Components in ODB++

This document explains how to locate the board outline within an ODB++ dataset and align it with the component placement data.

## Summary

In ODB++, all graphical data, including the board outline and component placements, shares a common coordinate system. The board outline is defined in the step profile file located at `steps/{step_name}/profile`. By finding the coordinates that define this outline, you can determine the board's shape and position. Component coordinates, found in `comp` files, can then be directly mapped onto this shape, as they use the same coordinate system.

---

### 1. Finding the Board Outline (Profile)

The physical shape of the PCB is defined by a contour, usually a closed polygon, stored in the step profile within the ODB++ hierarchy.

-   **File Location:** The board outline is defined in the step profile located at `steps/{step_name}/profile`.
-   **File Type:** This is a structured text file that defines the outline shape of the step. It is required by many operations in ODB++.
-   **Content Structure:** The profile is created using a single surface feature representing the outline of the step with optional internal holes (cutouts). There can be only one island, but any number of holes inside the island. Holes cannot touch the island boundary or one another.
-   **File Content:** The profile file contains records that define geometric shapes. For the outline, you are looking for a series of `L` (line) or `A` (arc) commands that form a closed loop. These records will have X and Y coordinates.

    *Example of a line segment in a feature file:*
    ```
    L X1250000Y3450000 X1250000Y4550000 D01
    ```
    This represents a line from (1.25, 3.45) to (1.25, 4.55). By assembling all these segments, you can reconstruct the entire board outline.

### 2. Finding Component Placement Data

Component information, including their placement coordinates, is stored in dedicated files.

-   **File Location:** Component data is located in `steps/{step_name}/layers/comp_+_top` for the top side and `steps/{step_name}/layers/comp_+_bot` for the bottom side of the board.
-   **File Content:** These are plain text files with a specific format. Each line represents a component and contains several fields, including the component's reference designator (e.g., `R1`, `C12`), package name, and most importantly, its **X and Y coordinates** and rotation.

    *Example of a component record:*
    ```
    #CMP  "R1"  "RES-0603"  1  1800000  2200000  90  "T" ...
    ```
    In this example, `R1` is located at X=`1800000` (1.8 units) and Y=`2200000` (2.2 units) with a rotation of 90 degrees on the Top ("T") side.

### 3. The Relationship: The Common Coordinate System

The key to aligning the outline with the components is that **all coordinates in ODB++ share the same origin (0,0)**. This means:

-   The coordinates in the step `profile` file are directly comparable to the coordinates in the `comp_+_top` and `comp_+_bot` files.
-   There is no need for a separate transformation or mapping. The (0,0) point is the same for the board outline, component placements, copper traces, and all other features.

### How to Align the Data:

1.  **Parse the Step Profile:** Read the coordinates from the step profile file (`steps/{step_name}/profile`) to reconstruct the board's shape as a polygon.
2.  **Determine the Board's "Origin":** While the ODB++ canvas has a (0,0) origin, the board itself will be located somewhere on that canvas. You can find the board's effective origin by finding the minimum X and minimum Y coordinates of its outline polygon (i.e., the bottom-left corner of its bounding box).
3.  **Parse the Component Files:** Read the X and Y coordinates for each component from the `comp` files.
4.  **Relate Component Position to Board Shape:**
    -   A component's (X, Y) position from the `comp` file places it directly onto the canvas where the board outline also lies.
    -   If you want to find a component's position *relative to the board's corner*, simply subtract the board's minimum X and Y coordinates (calculated in step 2) from the component's X and Y coordinates.

By following this process, you can accurately determine the location of the board's outline and place every component precisely on it.
