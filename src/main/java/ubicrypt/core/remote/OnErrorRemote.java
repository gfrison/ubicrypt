 package ubicrypt.core.remote;

import java.util.Optional;

import ubicrypt.core.FileProvenience;
import ubicrypt.core.IRepository;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.provider.UbiProvider;

public class OnErrorRemote extends OnUpdateRemote {
    public OnErrorRemote(UbiProvider provider, IRepository repository) {
        super(provider, repository);
    }

    @Override
    public boolean test(FileProvenience fileProvenience, RemoteConfig remoteConfig) {
        UbiFile file = fileProvenience.getFile();
        Optional<RemoteFile> rfile = remoteConfig.getRemoteFiles().stream().filter(file1 -> file1.equals(file)).findFirst();
        if (!rfile.isPresent()) {
            return false;
        }
        return rfile.get().isError();
    }

}
