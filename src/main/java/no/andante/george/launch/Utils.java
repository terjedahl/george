// Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
// The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by the terms of this license.
// You must not remove this notice, or any other, from this software.

package no.andante.george.launch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Adler32;


public class Utils {

    public static long checksum(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            Adler32 checksum = new Adler32();
            byte[] buf = new byte[16384];

            int read;
            while ((read = input.read(buf)) > -1)
                checksum.update(buf, 0, read);

            return checksum.getValue();
        }
    }
}
