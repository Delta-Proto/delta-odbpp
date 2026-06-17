package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Notes;
import com.deltaproto.deltaodbpp.model.Notes.Note;
import com.deltaproto.deltaodbpp.model.Polarity;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for ODB++ notes files.
 * Parses electronic notes/annotations from layer notes files.
 */
public class NotesParser {

    public Notes parse(Path notesFile) throws IOException {
        Notes notes = new Notes();

        try (BufferedReader reader = Files.newBufferedReader(notesFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("UNITS=")) {
                    notes.setUnits(line.substring(6));
                } else if (line.startsWith("NOTE {")) {
                    notes.getNotes().add(parseNote(reader));
                }
            }
        }
        return notes;
    }

    private Note parseNote(BufferedReader reader) throws IOException {
        Note note = new Note();
        Map<String, String> data = new HashMap<>();
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("}")) {
                break;
            }
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                data.put(parts[0].trim(), parts[1].trim());
            }
        }

        if (data.containsKey("ID")) note.setId(Integer.parseInt(data.get("ID")));
        if (data.containsKey("X")) note.setX(Double.parseDouble(data.get("X")));
        if (data.containsKey("Y")) note.setY(Double.parseDouble(data.get("Y")));
        if (data.containsKey("WIDTH")) note.setWidth(Double.parseDouble(data.get("WIDTH")));
        if (data.containsKey("HEIGHT")) note.setHeight(Double.parseDouble(data.get("HEIGHT")));
        if (data.containsKey("TEXT")) note.setText(data.get("TEXT"));
        if (data.containsKey("FONT")) note.setFont(data.get("FONT"));
        if (data.containsKey("FONT_SIZE")) note.setFontSize(Double.parseDouble(data.get("FONT_SIZE")));
        if (data.containsKey("ALIGNMENT")) note.setAlignment(data.get("ALIGNMENT"));
        if (data.containsKey("ANGLE")) note.setAngle(Double.parseDouble(data.get("ANGLE")));
        if (data.containsKey("MIRROR")) note.setMirror("YES".equals(data.get("MIRROR")) || "Y".equals(data.get("MIRROR")));
        if (data.containsKey("POLARITY")) note.setPolarity(Polarity.fromString(data.get("POLARITY")));

        return note;
    }
}
