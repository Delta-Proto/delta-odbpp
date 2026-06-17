#!/usr/bin/env python3
"""
Split a markdown file into separate .md files based on headings.
Creates a nested folder structure matching the heading hierarchy.

This script is designed to work with PDF-converted text files that may not have
proper markdown formatting. It will detect chapter/section patterns and convert
them to markdown headings before splitting.
"""

import os
import re
import argparse
from pathlib import Path
from typing import List, Tuple


def sanitize_filename(name: str) -> str:
    """Sanitize a string to be used as a filename/directory name."""
    # Remove or replace invalid characters
    name = re.sub(r'[<>:"/\\|?*]', '', name)
    # Replace multiple spaces with single space
    name = re.sub(r'\s+', ' ', name)
    # Remove the + from ODB++ for cleaner filenames
    name = name.replace('++', 'pp')
    # Strip leading/trailing spaces and dots
    name = name.strip(' .')
    # Limit length
    if len(name) > 100:
        name = name[:100].rsplit(' ', 1)[0]
    return name or 'untitled'


def preprocess_pdf_text(content: str) -> str:
    """
    Convert PDF-extracted text to proper markdown format.
    Detects chapter and section patterns and adds markdown heading syntax.
    """
    lines = content.split('\n')
    processed_lines = []
    i = 0

    # Skip table of contents (lines with dots pattern like "Section...... 12")
    toc_pattern = re.compile(r'^.+\.{3,}\s*\d+\s*$')

    # Track if we've passed the ToC
    passed_toc = False
    toc_end_markers = ['Chapter 1', 'CHAPTER 1']

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # Detect end of ToC
        if not passed_toc:
            for marker in toc_end_markers:
                if stripped.startswith(marker) and i > 100:  # After some ToC lines
                    passed_toc = True
                    break

        # Skip ToC lines
        if not passed_toc and toc_pattern.match(stripped):
            i += 1
            continue

        # Detect "Chapter N" pattern (main chapter heading)
        chapter_match = re.match(r'^Chapter\s+(\d+)\s*$', stripped, re.IGNORECASE)
        if chapter_match and passed_toc:
            chapter_num = chapter_match.group(1)
            # Look at next non-empty line for chapter title
            j = i + 1
            while j < len(lines) and not lines[j].strip():
                j += 1
            if j < len(lines):
                title = lines[j].strip()
                processed_lines.append(f'\n# Chapter {chapter_num}: {title}\n')
                i = j + 1
                continue

        # Detect section headers (lines in ALL CAPS or Title Case that are short and standalone)
        # These typically appear after a blank line and before content
        if passed_toc and stripped and len(stripped) < 100:
            # Check if this looks like a section header
            # - Previous line is blank
            # - Line is relatively short
            # - Line contains mostly letters
            prev_blank = i == 0 or not lines[i - 1].strip()

            # Common section patterns in technical docs
            is_section = False

            # Check for common entity/property patterns like "entity_name (Description)"
            entity_pattern = re.match(r'^([a-z_]+)\s*\(([^)]+)\)\s*$', stripped)
            if entity_pattern:
                is_section = True
                processed_lines.append(f'\n### {stripped}\n')
                i += 1
                continue

            # Check for path-like patterns "path/to/thing"
            if '/' in stripped and not stripped.startswith('http') and len(stripped.split()) <= 3:
                is_section = True
                processed_lines.append(f'\n### {stripped}\n')
                i += 1
                continue

            # Check for standalone capitalized section titles
            if prev_blank and re.match(r'^[A-Z][A-Za-z0-9\s\-\+]+$', stripped):
                # Look ahead to see if next non-blank line is content
                j = i + 1
                while j < len(lines) and not lines[j].strip():
                    j += 1
                if j < len(lines):
                    next_line = lines[j].strip()
                    # If next line is content (starts lowercase or is a bullet)
                    if next_line and (next_line[0].islower() or next_line.startswith('•') or
                                       next_line.startswith('-') or len(next_line) > 100):
                        processed_lines.append(f'\n## {stripped}\n')
                        i += 1
                        continue

        processed_lines.append(line)
        i += 1

    return '\n'.join(processed_lines)


def parse_headings(content: str) -> List[Tuple[int, str, int, int]]:
    """
    Parse markdown content and find all headings.
    Returns list of (level, title, start_pos, end_pos).
    """
    headings = []
    # Match ATX-style headings (# Heading)
    pattern = r'^(#{1,6})\s+(.+?)(?:\s*#*\s*)?$'

    lines = content.split('\n')
    pos = 0

    for i, line in enumerate(lines):
        match = re.match(pattern, line)
        if match:
            level = len(match.group(1))
            title = match.group(2).strip()
            headings.append((level, title, pos, i))
        pos += len(line) + 1  # +1 for newline

    return headings


def get_section_content(content: str, headings: List[Tuple[int, str, int, int]],
                        index: int) -> str:
    """Extract content for a section (from heading to next same-or-higher level heading)."""
    lines = content.split('\n')
    start_line = headings[index][3]
    current_level = headings[index][0]

    # Find the end of this section
    end_line = len(lines)
    for i in range(index + 1, len(headings)):
        if headings[i][0] <= current_level:
            end_line = headings[i][3]
            break

    section_lines = lines[start_line:end_line]
    return '\n'.join(section_lines).strip()


def build_hierarchy(headings: List[Tuple[int, str, int, int]]) -> List[dict]:
    """
    Build a hierarchical structure from flat headings list.
    Returns a list of section dicts with 'level', 'title', 'index', 'children', 'path'.
    """
    if not headings:
        return []

    root = {'level': 0, 'title': 'root', 'children': [], 'index': -1, 'path': []}
    stack = [root]

    for i, (level, title, _, _) in enumerate(headings):
        node = {
            'level': level,
            'title': title,
            'index': i,
            'children': [],
            'path': []
        }

        # Pop stack until we find a parent with lower level
        while stack and stack[-1]['level'] >= level:
            stack.pop()

        if stack:
            parent = stack[-1]
            parent['children'].append(node)
            node['path'] = parent['path'] + [sanitize_filename(parent['title'])] if parent['level'] > 0 else []

        stack.append(node)

    return root['children']


def write_sections(content: str, headings: List[Tuple[int, str, int, int]],
                   hierarchy: List[dict], output_dir: Path,
                   min_level: int = 1, max_level: int = 3) -> int:
    """
    Recursively write sections to files.
    Returns count of files written.
    """
    count = 0

    def process_node(node: dict, parent_path: Path):
        nonlocal count
        level = node['level']
        title = node['title']
        index = node['index']

        # Skip if outside level range
        if level < min_level or level > max_level:
            for child in node['children']:
                process_node(child, parent_path)
            return

        # Create directory for this section if it has children
        safe_name = sanitize_filename(title)

        if node['children'] and level < max_level:
            section_dir = parent_path / safe_name
            section_dir.mkdir(parents=True, exist_ok=True)

            # Write this section's content to index.md or _intro.md
            section_content = get_section_content(content, headings, index)
            if section_content.strip():
                # Only include content up to first child heading
                first_child_line = headings[node['children'][0]['index']][3]
                current_line = headings[index][3]
                lines = content.split('\n')
                intro_content = '\n'.join(lines[current_line:first_child_line]).strip()

                if intro_content.strip():
                    intro_file = section_dir / '_index.md'
                    intro_file.write_text(intro_content + '\n', encoding='utf-8')
                    count += 1
                    print(f"  Created: {intro_file.relative_to(output_dir)}")

            # Process children
            for child in node['children']:
                process_node(child, section_dir)
        else:
            # Write as a standalone file
            section_content = get_section_content(content, headings, index)
            if section_content.strip():
                file_path = parent_path / f"{safe_name}.md"
                file_path.parent.mkdir(parents=True, exist_ok=True)
                file_path.write_text(section_content + '\n', encoding='utf-8')
                count += 1
                print(f"  Created: {file_path.relative_to(output_dir)}")

    for node in hierarchy:
        process_node(node, output_dir)

    return count


def split_markdown(input_file: Path, output_dir: Path,
                   min_level: int = 1, max_level: int = 3,
                   preprocess: bool = True) -> int:
    """
    Split a markdown file into sections based on headings.

    Args:
        input_file: Path to input markdown file
        output_dir: Directory to write output files
        min_level: Minimum heading level to create files for (1-6)
        max_level: Maximum heading level to create files for (1-6)
        preprocess: Whether to preprocess PDF-extracted text

    Returns:
        Number of files created
    """
    print(f"Reading: {input_file}")
    content = input_file.read_text(encoding='utf-8')

    if preprocess:
        print("Preprocessing PDF text to add markdown headings...")
        content = preprocess_pdf_text(content)

        # Optionally save the preprocessed content
        preprocessed_file = input_file.parent / f"{input_file.stem}_processed.md"
        preprocessed_file.write_text(content, encoding='utf-8')
        print(f"Saved preprocessed content to: {preprocessed_file}")

    print("Parsing headings...")
    headings = parse_headings(content)
    print(f"Found {len(headings)} headings")

    if not headings:
        print("No headings found in document!")
        return 0

    # Show heading summary
    level_counts = {}
    for level, _, _, _ in headings:
        level_counts[level] = level_counts.get(level, 0) + 1
    print("Heading distribution:")
    for level in sorted(level_counts.keys()):
        print(f"  H{level}: {level_counts[level]} headings")

    print("\nBuilding hierarchy...")
    hierarchy = build_hierarchy(headings)

    print(f"\nCreating output directory: {output_dir}")
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"\nWriting sections (levels {min_level}-{max_level})...")
    count = write_sections(content, headings, hierarchy, output_dir, min_level, max_level)

    print(f"\nDone! Created {count} files.")
    return count


def main():
    parser = argparse.ArgumentParser(
        description='Split a markdown file into separate files based on headings.'
    )
    parser.add_argument('input', type=Path, help='Input markdown file')
    parser.add_argument('output', type=Path, help='Output directory')
    parser.add_argument('--min-level', type=int, default=1,
                        help='Minimum heading level to split on (default: 1)')
    parser.add_argument('--max-level', type=int, default=3,
                        help='Maximum heading level to split on (default: 3)')
    parser.add_argument('--no-preprocess', action='store_true',
                        help='Skip preprocessing of PDF-extracted text')

    args = parser.parse_args()

    if not args.input.exists():
        print(f"Error: Input file not found: {args.input}")
        return 1

    split_markdown(args.input, args.output, args.min_level, args.max_level,
                   preprocess=not args.no_preprocess)
    return 0


if __name__ == '__main__':
    exit(main())
