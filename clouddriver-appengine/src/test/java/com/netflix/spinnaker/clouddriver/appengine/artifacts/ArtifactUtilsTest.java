package com.netflix.spinnaker.clouddriver.appengine.artifacts;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.Test;

class ArtifactUtilsTest {

  @Test
  void testUntarStreamToPathWithEntryOutsideDestDirThrowsException() throws IOException {

    Exception ex = null;
    String s = "target/zip-unarchiver-slip-tests";
    File testZip = new File(new File("").getAbsolutePath(), "src/test/zips/zip-slip.zip");
    File outputDirectory = new File(new File("test-tar").getAbsolutePath(), s);

    FileUtils.deleteDirectory(outputDirectory);

    try {
      ArtifactUtils.untarStreamToPath(new FileInputStream(testZip), outputDirectory.getPath());
    } catch (Exception e) {
      ex = e;
    }

    assertNotNull(ex);
    assertTrue(ex.getMessage().startsWith("Entry is outside of the target directory"));
  }
}
