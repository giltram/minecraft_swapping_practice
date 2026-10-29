import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Loom 0.4.33 asks HTTP servers for gzip but copies their compressed bytes verbatim.
 * Keep its implementation and dependencies; change only DownloadUtil's HTTP coding
 * constant to identity. All inputs and the targeted class are checksum verified.
 */
class LoomCompatibility {
    static final String UPSTREAM_VERSION = '0.4.33'
    static final String PATCHED_VERSION = '0.4.33-hst1'
    static final String CLASS_PATH = 'net/fabricmc/loom/util/DownloadUtil.class'
    static final String CLASS_SHA256 = 'a2907651f400445ee3844c1c3e02addb7ded1e87e5cbc54e155da4567fecc0e2'
    static final String PATCHED_JAR_SHA256 = 'd9a8fd175b5d3fc83452b3596c697779c26a1768a23b1bb1c0305b6a50afa040'
    static final String PATCHED_POM_SHA256 = 'd16fb46d214e7a636b4bf92e9e3073af17518114382d912bd2c744699dc95b10'
    static final Map<String, String> INPUT_SHA256 = [
        jar: '1c2c95b8a894f59ed2925d2e105edb93b4783a2dd457d10f6de4b5c639d34a24',
        pom: 'caab2ed5c04762311e8ed09a42014e92f74273bbbd3c430a0fefc3252e78879d'
    ]

    File prepare(File root, File gradleHome, boolean offline) {
        File repository = new File(root, '.gradle/loom-compat/repository')
        File output = new File(repository, "net/fabricmc/fabric-loom/${PATCHED_VERSION}")
        File jar = new File(output, "fabric-loom-${PATCHED_VERSION}.jar")
        File pom = new File(output, "fabric-loom-${PATCHED_VERSION}.pom")
        if (jar.isFile() && pom.isFile() && sha256(jar.bytes) == PATCHED_JAR_SHA256
                && sha256(pom.bytes) == PATCHED_POM_SHA256) return repository

        File inputs = new File(root, '.gradle/loom-compat/upstream')
        inputs.mkdirs()
        INPUT_SHA256.each { extension, checksum ->
            String name = "fabric-loom-${UPSTREAM_VERSION}.${extension}"
            File input = new File(inputs, name)
            if (!input.isFile()) {
                File cache = new File(gradleHome, "caches/modules-2/files-2.1/net.fabricmc/fabric-loom/${UPSTREAM_VERSION}")
                File cached = cache.isDirectory() ? cache.listFiles().collect { new File(it, name) }.find { it.isFile() } : null
                if (cached != null) Files.copy(cached.toPath(), input.toPath())
                else {
                    if (offline) throw new IOException("Loom compatibility input is unavailable offline: ${name}")
                    download(new URL("https://maven.fabricmc.net/net/fabricmc/fabric-loom/${UPSTREAM_VERSION}/${name}"), input)
                }
            }
            if (sha256(input.bytes) != checksum) throw new IOException("Loom checksum mismatch: ${input}")
        }

        output.mkdirs()
        File temporary = new File(output, 'patched.jar.tmp')
        ZipFile upstream = new ZipFile(new File(inputs, "fabric-loom-${UPSTREAM_VERSION}.jar"))
        try {
            new ZipOutputStream(new FileOutputStream(temporary)).withCloseable { zip ->
                // Stored entries avoid JDK-dependent compression when reproducing this jar.
                Collections.list(upstream.entries()).sort { it.name }.each { entry ->
                    byte[] bytes = upstream.getInputStream(entry).withCloseable { it.bytes }
                    if (entry.name == CLASS_PATH) {
                        if (sha256(bytes) != CLASS_SHA256) throw new IOException('Unexpected Loom DownloadUtil bytecode')
                        bytes = replaceEncodingConstant(bytes)
                    }
                    CRC32 crc = new CRC32(); crc.update(bytes)
                    ZipEntry copy = new ZipEntry(entry.name)
                    copy.time = entry.time; copy.method = ZipEntry.STORED
                    copy.size = bytes.length; copy.crc = crc.value
                    zip.putNextEntry(copy); zip.write(bytes); zip.closeEntry()
                }
            }
        } finally { upstream.close() }
        Files.move(temporary.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
        String originalPom = new File(inputs, "fabric-loom-${UPSTREAM_VERSION}.pom").getText('UTF-8')
        pom.setText(originalPom.replaceFirst('<version>0.4.33</version>', "<version>${PATCHED_VERSION}</version>"), 'UTF-8')
        if (sha256(jar.bytes) != PATCHED_JAR_SHA256 || sha256(pom.bytes) != PATCHED_POM_SHA256) {
            throw new IOException('Unexpected Loom compatibility output checksum')
        }
        return repository
    }

    /** Constant pool indices and all bytecode remain unchanged. Exactly one UTF8 value changes. */
    static byte[] replaceEncodingConstant(byte[] original) {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(original))
        ByteArrayOutputStream result = new ByteArrayOutputStream()
        DataOutputStream output = new DataOutputStream(result)
        output.writeInt(input.readInt()); output.writeInt(input.readInt()) // magic + class version
        int count = input.readUnsignedShort(); output.writeShort(count)
        int changed = 0
        for (int index = 1; index < count; index++) {
            int tag = input.readUnsignedByte(); output.writeByte(tag)
            if (tag == 1) {
                String value = input.readUTF()
                if (value == 'gzip') { value = 'identity'; changed++ }
                output.writeUTF(value)
            } else {
                int length
                switch (tag) {
                    case 3: case 4: case 9: case 10: case 11: case 12: case 18: length = 4; break
                    case 5: case 6: length = 8; index++; break
                    case 7: case 8: case 16: length = 2; break
                    case 15: length = 3; break
                    default: throw new IOException("Unexpected constant pool tag ${tag}")
                }
                byte[] bytes = new byte[length]; input.readFully(bytes); output.write(bytes)
            }
        }
        if (changed != 1) throw new IOException("Expected one gzip constant; found ${changed}")
        output.write(input.bytes)
        return result.toByteArray()
    }

    static String sha256(byte[] bytes) { MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString() }

    private static void download(URL url, File destination) {
        def connection = url.openConnection()
        connection.setRequestProperty('Accept-Encoding', 'identity')
        connection.connectTimeout = 30000; connection.readTimeout = 60000
        File temporary = new File(destination.parentFile, destination.name + '.tmp')
        connection.inputStream.withCloseable { input -> temporary.withOutputStream { it << input } }
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
