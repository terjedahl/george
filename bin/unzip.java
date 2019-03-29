import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class unzip {

    public static boolean verbose = false;

    public static void main(String args[]) {
        if (args.length == 0) {
            System.err.println("Usage: java unzip.java <zip-file> [target-dir]");
            System.exit(1);
        }
        var zipFile = args[0];

        var targetDir = args.length > 1 ? args[1] : ".";

        if (verbose) System.out.println("Unzipping '" + zipFile + "'to dir '" + targetDir + "' ...");

        unzip(zipFile, targetDir);
    }


    private static void unzip(String zipFilePath, String destDirPath) {
        File destDir = new File(destDirPath);

        byte [] buffer = new byte[1024];
        int len;

        try(ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
//            ZipEntry entry = zis.getNextEntry();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                if(entry.isDirectory())
                    file.mkdirs();
                else {
                    if(verbose) System.out.println("Unzipping: " + file);
                    try(FileOutputStream fos = new FileOutputStream(file)) {
                        while ((len = zis.read(buffer)) > 0)
                            fos.write(buffer, 0, len);
                    } catch (IOException e) { e.printStackTrace(); }
                }
//                zis.closeEntry();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}