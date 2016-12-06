package ubicrypt.ui;

import com.google.common.base.Throwables;

import java.io.IOException;

import javafx.application.HostServices;

public class OSUtil {
    private enum OSType {win, x, mac, unknow}

    private static OSType os;

    static {
        String oss = System.getProperty("os.name").toLowerCase();
        if (oss.indexOf("win") >= 0) {
            os = OSType.win;
        } else if (oss.indexOf("mac") >= 0) {
            os = OSType.mac;
        } else if ((oss.indexOf("nix") >= 0 || oss.indexOf("nux") >= 0 || oss.indexOf("aix") > 0)) {
            os = OSType.x;
        } else {
            os = OSType.unknow;
        }
    }

    private final HostServices hostServices;

    public OSUtil(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public void openUrl(String url) {
        try {
            switch (os) {
                case mac:
                    Runtime.getRuntime().exec("open " + url);
                    break;
                default:
                    hostServices.showDocument(url);
                    break;
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }
}
