package ubicrypt.core.provider.gdrive;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

import rx.functions.Func1;
import ubicrypt.core.Utils;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.ProviderStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

public class GDriveProviderTest {
    private static final Logger log = getLogger(GDriveProviderTest.class);
    private static GDriveConf conf;
    private GDriveProvider gdrive;
    final Func1<InputStream, String> stringFunc1 = is -> {
        try {
            return IOUtils.toString(is, Charset.defaultCharset());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    };

    @BeforeClass
    public static void init() throws GeneralSecurityException, IOException {
        final Path path = Paths.get(System.getProperty("user.home"), "gdrive");
        try {
            conf = Utils.unmarshall(Files.newInputStream(path), GDriveConf.class);
            conf.setFolderName("test");
        } catch (Exception e) {
            log.info("conf not found:{}", e.getMessage(), e);
            GDriveAuthorizer autho = new GDriveAuthorizer();
            log.info("open url:{}", autho.authorizeUrl());
            conf = autho.credential();
            Utils.write(path, Utils.marshall(conf)).toBlocking().last();
        }
    }


    @Before
    public void initialize() throws Exception {
        gdrive = new GDriveProvider(conf);
        assertThat(gdrive.init(Utils.deviceId()).toBlocking().first()).isEqualTo(ProviderStatus.initialized);
    }

    @Test
    public void post() throws Exception {
        String id = null;
        try {
            id = gdrive.post(new ByteArrayInputStream("ciao".getBytes())).toBlocking().last();
            assertThat(id).isNotNull();
            assertThat(gdrive.get(id).map(stringFunc1).toBlocking().last()).isEqualTo("ciao");
        } finally {
            if (id != null) {
                assertThat(gdrive.delete(id).toBlocking().last()).isTrue();
            }
        }
    }

    @Test
    public void update() throws Exception {
        String id = null;
        try {
            id = gdrive.post(new ByteArrayInputStream("ciao".getBytes())).toBlocking().last();
            assertThat(id).isNotNull();
            assertThat(gdrive.put(id, new ByteArrayInputStream("ciao2".getBytes())).toBlocking().last()).isTrue();
            assertThat(gdrive.get(id).map(stringFunc1).toBlocking().last()).isEqualTo("ciao2");
        } finally {
            if (id != null) {
                assertThat(gdrive.delete(id).toBlocking().last()).isTrue();
            }
        }
    }

    @Test(expected = NotFoundException.class)
    public void notFound() throws Exception {
        gdrive.get("notexists").map(stringFunc1).toBlocking().last();
    }
}
