package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Notes;
import com.deltaproto.deltaodbpp.model.Notes.Note;
import com.deltaproto.deltaodbpp.model.Polarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NotesParserTest {

    private NotesParser parser;

    @BeforeEach
    void setUp() {
        parser = new NotesParser();
    }

    @Test
    void testParseBasicNotes(@TempDir Path tempDir) throws IOException {
        String content = """
            UNITS=INCH
            NOTE {
                ID=1
                X=1.5
                Y=2.5
                TEXT=Test Note
                FONT=STANDARD
                FONT_SIZE=0.1
                ANGLE=0
            }
            """;

        Path notesFile = tempDir.resolve("notes");
        Files.writeString(notesFile, content);

        Notes notes = parser.parse(notesFile);

        assertNotNull(notes);
        assertEquals("INCH", notes.getUnits());
        assertEquals(1, notes.getNotes().size());

        Note note = notes.getNotes().get(0);
        assertEquals(1, note.getId());
        assertEquals(1.5, note.getX(), 0.001);
        assertEquals(2.5, note.getY(), 0.001);
        assertEquals("Test Note", note.getText());
        assertEquals("STANDARD", note.getFont());
        assertEquals(0.1, note.getFontSize(), 0.001);
        assertEquals(0.0, note.getAngle(), 0.001);
    }

    @Test
    void testParseMultipleNotes(@TempDir Path tempDir) throws IOException {
        String content = """
            UNITS=MM
            NOTE {
                ID=1
                X=10
                Y=20
                TEXT=First Note
            }
            NOTE {
                ID=2
                X=30
                Y=40
                TEXT=Second Note
                MIRROR=YES
                POLARITY=P
            }
            NOTE {
                ID=3
                X=50
                Y=60
                TEXT=Third Note
                ALIGNMENT=CENTER
            }
            """;

        Path notesFile = tempDir.resolve("notes");
        Files.writeString(notesFile, content);

        Notes notes = parser.parse(notesFile);

        assertNotNull(notes);
        assertEquals("MM", notes.getUnits());
        assertEquals(3, notes.getNotes().size());

        assertEquals("First Note", notes.getNotes().get(0).getText());
        assertEquals("Second Note", notes.getNotes().get(1).getText());
        assertTrue(notes.getNotes().get(1).isMirror());
        assertEquals(Polarity.POSITIVE, notes.getNotes().get(1).getPolarity());
        assertEquals("Third Note", notes.getNotes().get(2).getText());
        assertEquals("CENTER", notes.getNotes().get(2).getAlignment());
    }

    @Test
    void testParseEmptyNotesFile(@TempDir Path tempDir) throws IOException {
        String content = "UNITS=INCH\n";

        Path notesFile = tempDir.resolve("notes");
        Files.writeString(notesFile, content);

        Notes notes = parser.parse(notesFile);

        assertNotNull(notes);
        assertEquals("INCH", notes.getUnits());
        assertTrue(notes.getNotes().isEmpty());
    }
}
