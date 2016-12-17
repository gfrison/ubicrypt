/*
 * Copyright (C) 2016 Giancarlo Frison <giancarlo@gfrison.com>
 *
 * Licensed under the UbiCrypt License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://github.com/gfrison/ubicrypt/LICENSE.md
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ubicrypt.ui;

import com.google.common.base.Throwables;

import java.io.IOException;

import javafx.application.HostServices;

public class OSUtil {
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

  private enum OSType {
    win,
    x,
    mac,
    unknow
  }
}
