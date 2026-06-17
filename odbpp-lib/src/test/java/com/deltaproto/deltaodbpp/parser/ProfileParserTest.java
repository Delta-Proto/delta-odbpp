package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Profile;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProfileParserTest {

    @Test
    void testParse() throws Exception {
        // Committed synthetic minimal board carries a board-outline profile
        // (steps/pcb/profile) so the test runs against non-customer data.
        Path path = Fixtures.MINIMAL_ODB.resolve("steps").resolve("pcb").resolve("profile");
        ProfileParser parser = new ProfileParser();
        Profile profile = parser.parse(path);
        assertNotNull(profile);
        assertFalse(profile.getSurfaces().isEmpty(),
                "Profile should contain at least one board-outline surface");
    }
}
