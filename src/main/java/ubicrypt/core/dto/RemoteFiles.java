package ubicrypt.core.dto;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static ubicrypt.core.Utils.copySynchronized;

public class RemoteFiles {
    private Set<RemoteFile> remoteFiles = ConcurrentHashMap.newKeySet();
    private RemoteFile next;

    public Set<RemoteFile> getRemoteFiles() {
        return remoteFiles;
    }

    public void setRemoteFiles(final Set<RemoteFile> remoteFiles) {
        this.remoteFiles = copySynchronized(remoteFiles);
    }

    public RemoteFile getNext() {
        return next;
    }

    public void setNext(RemoteFile next) {
        this.next = next;
    }
}
