package bnlp.re.util;

import bnlp.common.CharSetEncoding;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author I. Burak Ozyurt
 * @version $Id$
 */
public class FileUtils {
    protected static SimpleDateFormat sdf = new SimpleDateFormat(
            "MM_dd_yy_HH_mm_ss");
    protected static Pattern driveLetterPattern = Pattern.compile("^[a-zA-Z]:");
    protected static Pattern templateVarPattern = Pattern
            .compile("\\$\\{(\\w+)\\}");

    protected FileUtils() {
    }

    public static String fromURL2Filename(String url) {
        String outFilename = url.replaceFirst("http(s)?://", "");
        outFilename = outFilename.replaceAll("[/:\\.\\-]+", "_");
        return outFilename;
    }

    public static BufferedReader newLatin1CharSetReader(String filename)
            throws IOException {
        filename = adjustPath(filename);
        return new BufferedReader(new InputStreamReader(new FileInputStream(
                filename), Charset.forName("ISO-8859-1")));
    }

    public static BufferedWriter newLatin1CharSetWriter(String filename)
            throws IOException {
        filename = adjustPath(filename);
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                filename), Charset.forName("ISO-8859-1")));
    }

    public static BufferedWriter newUTF8CharSetWriter(String filename)
            throws IOException {
        filename = adjustPath(filename);
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                filename), Charset.forName("UTF-8")));
    }

    public static BufferedReader newUTF8CharSetReader(String filename)
            throws IOException {
        filename = adjustPath(filename);
        return new BufferedReader(new InputStreamReader(new FileInputStream(
                filename), Charset.forName("UTF-8")));
    }

    public static BufferedWriter getBufferedWriter(String filename,
                                                   CharSetEncoding csEncoding) throws IOException {
        if (csEncoding == CharSetEncoding.UTF8) {
            return newUTF8CharSetWriter(filename);
        } else {
            return newLatin1CharSetWriter(filename);
        }
    }

    public static BufferedReader getBufferedReader(String filename,
                                                   CharSetEncoding csEncoding) throws IOException {
        if (csEncoding == CharSetEncoding.UTF8) {
            return newUTF8CharSetReader(filename);
        } else {
            return newLatin1CharSetReader(filename);
        }
    }

    public static String loadAsString(String textFile,
                                      CharSetEncoding csEncoding) throws IOException {
        textFile = adjustPath(textFile);
        StringBuilder buf = new StringBuilder((int) new File(textFile).length());
        BufferedReader in = null;
        try {
            if (csEncoding == CharSetEncoding.UTF8) {
                in = newUTF8CharSetReader(textFile);
            } else {
                in = newLatin1CharSetReader(textFile);
            }

            String line;
            while ((line = in.readLine()) != null) {
                buf.append(line).append('\n');
            }
        } finally {
            close(in);
        }
        return buf.toString().trim();
    }

    public static void saveText(String text, String textFile,
                                CharSetEncoding csEncoding) throws IOException {
        textFile = adjustPath(textFile);
        BufferedWriter out = null;
        try {
            if (csEncoding == CharSetEncoding.UTF8) {
                out = newUTF8CharSetWriter(textFile);
            } else {
                out = newLatin1CharSetWriter(textFile);
            }
            out.write(text);
            out.newLine();
        } finally {
            close(out);
        }
    }

    public static <T> void saveList(List<T> list, String outFile,
                                    CharSetEncoding csEncoding) throws IOException {
        saveList(list, outFile, csEncoding, false);
    }


    public static <T> void saveList(List<T> list, String outFile,
                                    CharSetEncoding csEncoding, boolean showDone) throws IOException {
        outFile = adjustPath(outFile);
        BufferedWriter out = null;
        try {
            if (csEncoding == CharSetEncoding.UTF8) {
                out = newUTF8CharSetWriter(outFile);
            } else {
                out = newLatin1CharSetWriter(outFile);
            }
            for (T elem : list) {
                out.write(elem.toString());
                out.newLine();
            }
            if (showDone) {
                System.out.println("wrote file:" + outFile);
            }
        } finally {
            close(out);
        }
    }

    public static String createTempFileName(String tempDir, String prefix,
                                            String extension) {
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        StringBuffer buf = new StringBuffer(tempDir.length() + 40);
        buf.append(tempDir).append(File.separator);
        if (prefix == null) {
            prefix = "temp_";
        }
        buf.append(prefix).append(System.currentTimeMillis()).append(extension);
        return buf.toString();
    }

    public static Properties loadProperties(String propsFilename)
            throws IOException {
        InputStream is = FileUtils.class.getClassLoader().getResourceAsStream(
                propsFilename);
        if (is == null) {
            throw new IOException(
                    "Cannot find properties file in the classpath:"
                            + propsFilename);
        }
        Properties props = new Properties();
        props.load(new InputStreamReader(is, "UTF-8"));
        return props;
    }

    public static void copyFile(String sourceFile, String destFile)
            throws IOException {

        sourceFile = adjustPath(sourceFile);
        destFile = adjustPath(destFile);
        BufferedInputStream bin = null;
        BufferedOutputStream bout = null;
        try {
            bin = new BufferedInputStream(new FileInputStream(sourceFile));
            bout = new BufferedOutputStream(new FileOutputStream(destFile));
            byte[] buffer = new byte[4096];
            int readBytes = 0;
            while ((readBytes = bin.read(buffer)) != -1) {
                bout.write(buffer, 0, readBytes);
            }

        } finally {
            close(bin);
            close(bout);
        }
    }

    @SuppressWarnings("Duplicates")
    public static void append(String sourceFile1, String sourceFile2, String destFile)
            throws IOException {

        sourceFile1 = adjustPath(sourceFile1);
        sourceFile2 = adjustPath(sourceFile2);
        destFile = adjustPath(destFile);
        BufferedInputStream bin = null;
        BufferedOutputStream bout = null;
        try {
            bout = new BufferedOutputStream(new FileOutputStream(destFile));
            byte[] buffer = new byte[4096];
            try {
                bin = new BufferedInputStream(new FileInputStream(sourceFile1));
                int readBytes;
                while ((readBytes = bin.read(buffer)) != -1) {
                    bout.write(buffer, 0, readBytes);
                }
            } finally {
                close(bin);
            }
            try {
                bin = new BufferedInputStream(new FileInputStream(sourceFile2));
                int readBytes;
                while ((readBytes = bin.read(buffer)) != -1) {
                    bout.write(buffer, 0, readBytes);
                }
            } finally {
                close(bin);
            }
        } finally {
            close(bout);
        }
    }


    public static void gzipAndCopyFile(String sourceFile, String destFile)
            throws FileNotFoundException, IOException {

        sourceFile = adjustPath(sourceFile);
        destFile = adjustPath(destFile);
        BufferedInputStream bin = null;
        GZIPOutputStream gout = null;
        try {
            bin = new BufferedInputStream(new FileInputStream(sourceFile));
            gout = new GZIPOutputStream(new BufferedOutputStream(
                    new FileOutputStream(destFile)));
            byte[] buffer = new byte[4096];
            int readBytes = 0;
            while ((readBytes = bin.read(buffer)) != -1) {
                gout.write(buffer, 0, readBytes);
            }
        } finally {
            close(bin);
            close(gout);
        }
    }

    /**
     * @param configFile
     * @return the conjugate file ( if the config file is training file, for
     * example, the conjugate of it is a testing file.
     */
    public static String prepConjugateFilename(String configFile) {
        File f = new File(configFile);
        String filename = f.getName();
        if (filename.endsWith("_tr.xml")) {
            String s = filename.replaceAll("_tr\\.xml", "_tst.xml");
            return new File(f.getParent(), s).getAbsolutePath();
        } else if (filename.endsWith("_tst.xml")) {
            String s = filename.replaceAll("_tst\\.xml", "_tr.xml");
            return new File(f.getParent(), s).getAbsolutePath();
        } else {
            throw new RuntimeException("Not a valid config file:" + configFile);
        }
    }

    /**
     * Adjusts the given canonical unix path with templates to the underlying
     * operating system/environment setup. Only <code>${home}</code> template is
     * recognized and expanded to the current user's home. An alternative home
     * dir can be specified at system level by setting <code>NLP_HOME</code>
     * system property.
     *
     * @param unixPath
     * @return
     */
    public static String adjustPath(String unixPath) {
        String homeDir = System.getProperty("user.home");
        if (System.getProperty("NLP_HOME") != null) {
            String hd = System.getProperty("NLP_HOME");
            if (!new File(hd).exists()) {
                throw new RuntimeException("Not a valid path for NLP_HOME:"
                        + hd);
            }
            homeDir = hd;
        }
        boolean isWindows = System.getProperty("os.name").toLowerCase()
                .startsWith("windows");

        if (unixPath.indexOf("${") != -1) {
            Matcher matcher = templateVarPattern.matcher(unixPath);
            if (matcher.find()) {
                String group = matcher.group(1);
                if (group.equals("home")) {
                    if (isWindows) {
                        unixPath = unixPath.replaceFirst("\\$\\{home\\}",
                                "c:/cygwin/home/bozyurt");
                        ;
                    } else {
                        unixPath = unixPath.replaceFirst("\\$\\{home\\}",
                                homeDir);
                    }
                } else {
                    throw new RuntimeException("Unsupported template variable:"
                            + group);
                }
            } else {
                throw new RuntimeException("Bad template variable syntax:"
                        + unixPath);
            }
        }
        if (isWindows && unixPath.indexOf("cygwin") == -1) {
            if (unixPath.startsWith("/")) {
                unixPath = "c:/cygwin" + unixPath;

            } else {
                if (!unixPath.startsWith("c:/cygwin/")
                        && !driveLetterPattern.matcher(unixPath).find()) {
                    unixPath = "c:/cygwin/" + unixPath;
                }
            }
        }
        return unixPath;
    }

    public static void checkPath(String path) {
        if (!new File(path).exists()) {
            throw new RuntimeException("Path does not exists!: " + path);
        }
    }

    /*
     * public static String getHomeDir() { boolean isWindows =
     * System.getProperty("os.name").toLowerCase() .startsWith("windows"); if
     * (isWindows) { return "c:/cygwin/home/bozyurt"; } else { return
     * System.getProperty("user.home"); } }
     */
    public static String buildPath(String rootDir, String filename) {
        StringBuffer buf = new StringBuffer();
        buf.append(rootDir);
        if (!rootDir.endsWith("/")) {
            buf.append('/');
        }
        buf.append(filename);
        return adjustPath(buf.toString());
    }

    public static String stripCommonPath(String path, String refPath) {
        int len = Math.min(path.length(), refPath.length());
        int idx = -1;
        for (int i = 0; i < len; i++) {
            if (refPath.charAt(i) != path.charAt(i)) {
                break;
            }
            idx = i;
        }
        return path.substring(idx + 1);
    }

    public static String quoteArgumentIfRequired(String arg) {
        boolean isWindows = System.getProperty("os.name").toLowerCase()
                .startsWith("windows");
        if (isWindows) {
            StringBuffer buf = new StringBuffer();
            buf.append('"').append(arg).append('"');
            return buf.toString();
        }
        return arg;
    }

    public static void close(Reader in) {
        if (in != null) {
            try {
                in.close();
            } catch (Exception x) {
            }
        }
    }

    public static void close(Writer out) {
        if (out != null) {
            try {
                out.close();
            } catch (Exception x) {
            }
        }
    }

    public static void close(OutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (Exception x) {
            }
        }
    }

    public static void close(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (Exception x) {
            }
        }
    }


    public static String[] readLines(String path, boolean skipComments,
                                     CharSetEncoding csEncoding) throws IOException {
        path = adjustPath(path);
        BufferedReader in = null;
        try {
            if (csEncoding == CharSetEncoding.UTF8) {
                in = newUTF8CharSetReader(path);
            } else {
                in = newLatin1CharSetReader(path);
            }
            String line = null;
            List<String> lines = new LinkedList<String>();
            while ((line = in.readLine()) != null) {
                if (line.trim().length() == 0) {
                    continue;
                }
                if (skipComments && line.startsWith("#")) {
                    continue;
                }
                lines.add(line);
            }
            String[] linesArr = new String[lines.size()];
            linesArr = lines.toArray(linesArr);
            return linesArr;
        } finally {
            close(in);
        }
    }

    public static void extractGzippedFile(String gzippedFile, File destFile) throws Exception {
        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = new BufferedInputStream(new GZIPInputStream(new FileInputStream(gzippedFile)));
            out = new BufferedOutputStream(new FileOutputStream(destFile));
            byte[] buffer = new byte[4096];
            int readBytes = 0;
            while ((readBytes = in.read(buffer)) != -1) {
                out.write(buffer, 0, readBytes);
            }
        } finally {
            close(in);
            close(out);
        }
    }

    public static void writeLines(String path, List<?> lines,
                                  CharSetEncoding csEncoding) throws IOException {
        path = adjustPath(path);
        BufferedWriter out = null;
        try {
            if (csEncoding == CharSetEncoding.UTF8) {
                out = newUTF8CharSetWriter(path);
            } else {
                out = newLatin1CharSetWriter(path);
            }
            for (Iterator<?> iter = lines.iterator(); iter.hasNext(); ) {
                Object line = iter.next();
                out.write(line.toString());
                out.newLine();
            }
        } finally {
            close(out);
        }
    }

    public static void appendLine(String path, String line) throws IOException {
        path = adjustPath(path);
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(path, true),
                    Charset.forName("UTF-8")));
            out.write(line);
            out.newLine();
        } finally {
            close(out);
        }
    }

    public static void append(String path, String line) throws IOException {
        path = adjustPath(path);
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(path, true),
                    Charset.forName("UTF-8")));
            out.write(line);
        } finally {
            close(out);
        }
    }

    public static String createTimestampFilename(String prefix) {
        StringBuffer buf = new StringBuffer();
        if (prefix != null)
            buf.append(prefix);
        buf.append(sdf.format(new Date()));
        return buf.toString();

    }

    public static String toUnixPath(String adjustedPath) {
        int idx = adjustedPath.indexOf("cygwin");
        if (idx != -1) {
            return adjustedPath.substring(idx + 6);
        }
        return adjustedPath;
    }


    public static BufferedWriter open2Append(String outFile) throws IOException {
        outFile = FileUtils.adjustPath(outFile);
        return new BufferedWriter(new FileWriter(outFile, true));
    }

    public static void deleteRecursively(File dir) {
        if (dir.isFile()) {
            dir.delete();
        } else if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null && files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        deleteRecursively(files[i]);
                    } else {
                        files[i].delete();
                    }
                }
            }
            dir.delete();
        }
    }

    public static List<File> findFiles(String rootDir, Set<String> includeSet) {
        List<File> collected = new ArrayList<File>();

        findFiles(new File(rootDir), collected, includeSet);
        return collected;
    }

    public static boolean isInIncludeSet(String path, Set<String> includeSet) {
        if (includeSet == null || includeSet.isEmpty()) {
            return true;
        }
        for (String pattern : includeSet) {
            if (path.indexOf(pattern) != -1) {
                return true;
            }
        }
        return false;
    }

    private static void findFiles(File parentDir, List<File> collected, Set<String> includeSet) {
        File[] files = parentDir.listFiles();
        for (File f : files) {
            if (f.isFile() && f.getName().equals("main.xml")) {
                if (isInIncludeSet(f.getAbsolutePath(), includeSet)) {
                    collected.add(f);
                }
            } else if (f.isDirectory()) {
                findFiles(f, collected, includeSet);
            }
        }
    }

    public static Element loadXML(String xmlFile) throws JDOMException,
            IOException {
        return loadXML(xmlFile, false);
    }

    /**
     * returns the root element of the loaded XML DOM tree.
     *
     * @param xmlFile
     * @param useLatinCharset
     * @return the root element of the loaded XML DOM tree
     * @throws JDOMException
     * @throws IOException
     */
    public static Element loadXML(String xmlFile, boolean useLatinCharset)
            throws JDOMException, IOException {
        xmlFile = FileUtils.adjustPath(xmlFile);
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        BufferedReader in = null;
        Element root = null;
        try {
            if (useLatinCharset) {
                in = FileUtils.newLatin1CharSetReader(xmlFile);
            } else {
                in = FileUtils.newUTF8CharSetReader(xmlFile);
            }

            Document doc = builder.build(in);
            root = doc.getRootElement();
        } finally {
            FileUtils.close(in);
        }
        return root;
    }

    public static List<String> findFilesOfNamePatternUnderDir(String directory, String pattern) {
        File[] files = new File(directory).listFiles();
        if (files.length == 0) {
            return Collections.emptyList();
        }
        Pattern p = Pattern.compile(pattern);
        List<String> filtered = new ArrayList<>();
        for (File f : files) {
            if (f.isFile()) {
                Matcher matcher = p.matcher(f.getName());
                if (matcher.find()) {
                    filtered.add(f.getAbsolutePath());
                }
            }
        }
        return filtered;
    }

    public static String convertToFileURL(String filename) {
        String path = new File(filename).getAbsolutePath();
        if (File.separatorChar != '/') {
            path = path.replace(File.separatorChar, '/');
        }
        path = path.replaceAll("%20", "\\%20");

        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "file:" + path;
    }

    public static List<String> loadSentences(String filePath) throws IOException {
        BufferedReader in = null;
        List<String> sentences = new ArrayList<String>(10);
        try {
            in = FileUtils.newUTF8CharSetReader(adjustPath(filePath));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0) {
                    sentences.add(line);
                }
            }
        } finally {
            FileUtils.close(in);
        }
        return sentences;
    }

    public static void saveXML(Element rootElem, String filename) throws IOException, JDOMException {
        filename = FileUtils.adjustPath(filename);
        BufferedWriter out = null;
        try {
            out = FileUtils.newUTF8CharSetWriter(filename);
            XMLOutputter xout = new XMLOutputter(Format.getPrettyFormat());
            xout.output(rootElem, out);
        } finally {
            FileUtils.close(out);
        }
    }

    public static void serialize(Object o, String filename) throws IOException {
        Assertion.assertNotNull(o);
        filename = FileUtils.adjustPath(filename);
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(new BufferedOutputStream(
                    new FileOutputStream(filename), 4096));
            out.writeObject(o);
        } finally {
            FileUtils.close(out);
        }
    }

    public static Object deserialize(String filename) throws IOException,
            ClassNotFoundException {
        assert (filename != null);
        filename = FileUtils.adjustPath(filename);
        assert (new File(filename).isFile());
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new BufferedInputStream(
                    new FileInputStream(filename), 4096));
            Object o = in.readObject();
            return o;
        } finally {
            FileUtils.close(in);
        }
    }
}
