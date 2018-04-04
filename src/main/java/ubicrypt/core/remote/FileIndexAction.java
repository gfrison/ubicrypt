package ubicrypt.core.remote;

import ubicrypt.core.Action;
import ubicrypt.core.dto.FileIndex;

public class FileIndexAction {
  private final Action action;
  private final String remoteName;
  private final FileIndex index;

  public FileIndexAction(Action action, String remoteName, FileIndex index) {
    this.action = action;
    this.remoteName = remoteName;
    this.index = index;
  }

  public Action getAction() {
    return action;
  }

  public String getRemoteName() {
    return remoteName;
  }

  public FileIndex getIndex() {
    return index;
  }
}
